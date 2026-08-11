import { readFile, writeFile } from 'node:fs/promises';
import net from 'node:net';
import { performance } from 'node:perf_hooks';

const host = process.env.REDIS_HOST || '127.0.0.1';
const port = Number(process.env.REDIS_PORT || 6379);
const users = Number(process.env.USERS || 5000);
const workers = Number(process.env.WORKERS || 2);
const repetitions = Number(process.env.REPETITIONS || 5);
const warmups = Number(process.env.WARMUPS || 2);
const outputFile = process.env.OUTPUT_FILE || 'docs/load-test/result/matching/redis/matching-engine-benchmark.json';
const queueKey = 'matching:queue:engine-benchmark';
const baselineScript = await readFile(
  'docs/load-test/redis/claim_oldest_pair_baseline.lua',
  'utf8',
);
const batchScript = await readFile(
  'backend/league-of-star-matching/src/main/resources/scripts/claim_oldest_batch.lua',
  'utf8',
);
const batchSizes = (process.env.BATCH_SIZES || '20,50,100,200')
  .split(',')
  .map(Number);

validateOptions();

const scenarios = [
  { name: 'baseline-2', batchSize: 2, script: baselineScript, args: [] },
  ...batchSizes.map((batchSize) => ({
    name: `batch-${batchSize}`,
    batchSize,
    script: batchScript,
    args: [String(batchSize)],
  })),
];

async function main() {
  const results = [];
  for (const scenario of scenarios) {
    for (let warmup = 0; warmup < warmups; warmup += 1) {
      await runScenario(scenario);
    }
    const runs = [];
    for (let repetition = 0; repetition < repetitions; repetition += 1) {
      runs.push(await runScenario(scenario));
    }
    results.push(summarize(scenario, runs));
  }

  const report = {
    measuredAt: new Date().toISOString(),
    environment: { host, port, users, workers, warmups, repetitions },
    results,
  };

  await writeFile(outputFile, `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

async function runScenario(scenario) {
  const control = await RedisConnection.connect(host, port);
  await control.command(['FLUSHALL']);
  await preloadQueue(control);

  const connections = await Promise.all(
    Array.from({ length: workers }, () => RedisConnection.connect(host, port)),
  );
  const claimedUserIds = new Set();
  let duplicateUsers = 0;
  let successfulClaims = 0;
  const latencies = [];

  const startedAt = performance.now();
  await Promise.all(connections.map(async (connection) => {
    while (true) {
      const commandStartedAt = performance.now();
      const response = await connection.command([
        'EVAL',
        scenario.script,
        '1',
        queueKey,
        ...scenario.args,
      ]);
      latencies.push(performance.now() - commandStartedAt);

      if (!Array.isArray(response) || response.length === 0) {
        return;
      }

      successfulClaims += 1;
      for (let index = 0; index < response.length; index += 2) {
        const userId = Number(response[index]);
        if (claimedUserIds.has(userId)) {
          duplicateUsers += 1;
        }
        claimedUserIds.add(userId);
      }
    }
  }));
  const durationMs = performance.now() - startedAt;
  const remainingUsers = Number(await control.command(['ZCARD', queueKey]));

  await Promise.all(connections.map((connection) => connection.close()));
  await control.close();

  return {
    durationMs,
    successfulClaims,
    totalClaims: latencies.length,
    claimedUsers: claimedUserIds.size,
    duplicateUsers,
    remainingUsers,
    p50Ms: percentile(latencies, 0.50),
    p95Ms: percentile(latencies, 0.95),
    p99Ms: percentile(latencies, 0.99),
    maxMs: Math.max(...latencies),
  };
}

async function preloadQueue(connection) {
  const args = ['ZADD', queueKey];
  for (let userId = 1; userId <= users; userId += 1) {
    args.push(String(userId), String(userId));
  }
  await connection.command(args);
}

function summarize(scenario, runs) {
  for (const run of runs) {
    if (run.claimedUsers !== users || run.duplicateUsers !== 0 || run.remainingUsers !== 0) {
      throw new Error(`correctness failure in ${scenario.name}: ${JSON.stringify(run)}`);
    }
  }

  return {
    scenario: scenario.name,
    batchUsers: scenario.batchSize,
    averageDurationMs: average(runs.map((run) => run.durationMs)),
    medianDurationMs: percentile(runs.map((run) => run.durationMs), 0.50),
    averageSuccessfulClaims: average(runs.map((run) => run.successfulClaims)),
    averageTotalClaims: average(runs.map((run) => run.totalClaims)),
    averageCommandP95Ms: average(runs.map((run) => run.p95Ms)),
    averageCommandP99Ms: average(runs.map((run) => run.p99Ms)),
    maxCommandMs: Math.max(...runs.map((run) => run.maxMs)),
    claimedUsers: users,
    duplicateUsers: 0,
    remainingUsers: 0,
    runs,
  };
}

function average(values) {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function percentile(values, ratio) {
  const sorted = [...values].sort((left, right) => left - right);
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1);
  return sorted[index];
}

function validateOptions() {
  if (users < 2 || users % 2 !== 0) {
    throw new Error('USERS must be an even number greater than or equal to 2');
  }
  if (workers < 1 || repetitions < 1 || warmups < 0) {
    throw new Error('WORKERS and REPETITIONS must be positive, and WARMUPS must not be negative');
  }
  for (const batchSize of batchSizes) {
    if (batchSize < 2 || batchSize % 2 !== 0) {
      throw new Error(`invalid batch size: ${batchSize}`);
    }
  }
}

class RedisConnection {
  constructor(socket) {
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.pending = [];
    socket.on('data', (chunk) => this.onData(chunk));
    socket.on('error', (error) => this.rejectAll(error));
    socket.on('close', () => this.rejectAll(new Error('Redis connection closed')));
  }

  static connect(hostname, redisPort) {
    return new Promise((resolve, reject) => {
      const socket = net.createConnection({ host: hostname, port: redisPort });
      socket.once('connect', () => resolve(new RedisConnection(socket)));
      socket.once('error', reject);
    });
  }

  command(args) {
    return new Promise((resolve, reject) => {
      this.pending.push({ resolve, reject });
      this.socket.write(encodeCommand(args));
    });
  }

  close() {
    return new Promise((resolve) => {
      this.socket.end(resolve);
    });
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.pending.length > 0) {
      const parsed = parseResponse(this.buffer, 0);
      if (!parsed) {
        return;
      }
      this.buffer = this.buffer.subarray(parsed.offset);
      const request = this.pending.shift();
      if (parsed.value instanceof Error) {
        request.reject(parsed.value);
      } else {
        request.resolve(parsed.value);
      }
    }
  }

  rejectAll(error) {
    while (this.pending.length > 0) {
      this.pending.shift().reject(error);
    }
  }
}

function encodeCommand(args) {
  const parts = [Buffer.from(`*${args.length}\r\n`)];
  for (const arg of args) {
    const value = Buffer.from(String(arg));
    parts.push(Buffer.from(`$${value.length}\r\n`), value, Buffer.from('\r\n'));
  }
  return Buffer.concat(parts);
}

function parseResponse(buffer, offset) {
  if (offset >= buffer.length) {
    return null;
  }
  const type = String.fromCharCode(buffer[offset]);
  const lineEnd = buffer.indexOf('\r\n', offset + 1);
  if (lineEnd === -1) {
    return null;
  }
  const header = buffer.toString('utf8', offset + 1, lineEnd);
  const bodyOffset = lineEnd + 2;

  if (type === '+' || type === '-' || type === ':') {
    const value = type === '+' ? header : type === '-' ? new Error(header) : Number(header);
    return { value, offset: bodyOffset };
  }
  if (type === '$') {
    const length = Number(header);
    if (length === -1) {
      return { value: null, offset: bodyOffset };
    }
    const end = bodyOffset + length;
    if (buffer.length < end + 2) {
      return null;
    }
    return { value: buffer.toString('utf8', bodyOffset, end), offset: end + 2 };
  }
  if (type === '*') {
    const count = Number(header);
    const values = [];
    let nextOffset = bodyOffset;
    for (let index = 0; index < count; index += 1) {
      const child = parseResponse(buffer, nextOffset);
      if (!child) {
        return null;
      }
      values.push(child.value);
      nextOffset = child.offset;
    }
    return { value: values, offset: nextOffset };
  }
  throw new Error(`Unsupported RESP type: ${type}`);
}

await main();
