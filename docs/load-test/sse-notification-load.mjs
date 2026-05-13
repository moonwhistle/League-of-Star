const totalConnections = Number(process.env.CONNECTIONS || 5000);
const holdMillis = parseDuration(process.env.HOLD_DURATION || '5m');
const rampUpMillis = parseDuration(process.env.RAMP_UP || '60s');
const setupConcurrency = Number(process.env.SETUP_CONCURRENCY || 100);
const joinTps = Number(process.env.JOIN_TPS || 0);
const mode = process.env.MODE || 'connection';
const namespace = process.env.TEST_USER_NAMESPACE || 'sse';
const password = process.env.TEST_PASSWORD || 'loadtest123';
const baseUrls = (process.env.API_BASE_URLS || 'http://localhost:8080,http://localhost:8081')
  .split(',')
  .map((url) => url.trim())
  .filter(Boolean);

const metrics = {
  sseAttempts: 0,
  sseConnected: 0,
  sseFailed: 0,
  sseClosed: 0,
  connectedEvents: 0,
  heartbeatEvents: 0,
  matchFoundEvents: 0,
  bytesReceived: 0,
  joinAttempts: 0,
  joinSuccess: 0,
  joinFailed: 0,
  openLatencies: [],
  matchFoundLatencies: [],
  errors: new Map(),
};

const joinStartedAtByIndex = new Map();

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

async function main() {
  validateMode();

  console.log(`[sse-load] mode=${mode}`);
  console.log(`[sse-load] connections=${totalConnections}, hold=${holdMillis}ms, rampUp=${rampUpMillis}ms`);
  console.log(`[sse-load] api=${baseUrls.join(',')}`);

  const tokens = await prepareUsers(totalConnections);
  console.log(`[sse-load] users ready=${tokens.length}`);

  const reporter = setInterval(printProgress, 10_000);
  const sseTasks = tokens.map((token, index) => openSse(index, token));

  if (mode === 'match') {
    const connectWaitMillis = parseDuration(process.env.CONNECT_WAIT || '10s');
    await sleep(connectWaitMillis);
    await joinQueues(tokens);
  }

  await Promise.allSettled(sseTasks);
  clearInterval(reporter);
  printSummary();
}

async function prepareUsers(count) {
  const tokens = new Array(count);
  let cursor = 0;

  async function worker() {
    while (cursor < count) {
      const index = cursor;
      cursor += 1;
      tokens[index] = await prepareUser(index);
    }
  }

  await Promise.all(Array.from({ length: Math.min(setupConcurrency, count) }, worker));
  return tokens;
}

async function prepareUser(index) {
  const user = userFor(index);
  const baseUrl = baseUrlFor(index);
  const loginResult = await login(baseUrl, user.email, user.password);

  if (loginResult.ok) {
    return loginResult.token;
  }

  if (!isMissingUser(loginResult)) {
    throw new Error(`login failed. index=${index}, status=${loginResult.status}, body=${loginResult.body}`);
  }

  const signUpResult = await signUp(baseUrl, user);
  if (!signUpResult.ok && !isExistingUser(signUpResult)) {
    throw new Error(`signUp failed. index=${index}, status=${signUpResult.status}, body=${signUpResult.body}`);
  }

  const reloginResult = await login(baseUrl, user.email, user.password);
  if (!reloginResult.ok) {
    throw new Error(`login after signUp failed. index=${index}, status=${reloginResult.status}, body=${reloginResult.body}`);
  }

  return reloginResult.token;
}

async function login(baseUrl, email, userPassword) {
  const response = await fetch(`${baseUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: userPassword }),
  });
  const body = await response.text();

  let parsed = {};
  try {
    parsed = JSON.parse(body);
  } catch (_) {
    // Ignore non-JSON error body.
  }

  return {
    ok: response.status === 200 && Boolean(parsed.accessToken),
    status: response.status,
    body,
    code: parsed.code,
    token: parsed.accessToken,
  };
}

async function signUp(baseUrl, user) {
  const response = await fetch(`${baseUrl}/api/v1/auth/signUp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(user),
  });
  const body = await response.text();

  let parsed = {};
  try {
    parsed = JSON.parse(body);
  } catch (_) {
    // Ignore non-JSON error body.
  }

  return {
    ok: response.status === 200,
    status: response.status,
    body,
    code: parsed.code,
  };
}

async function openSse(index, token) {
  const delay = Math.floor((index / totalConnections) * rampUpMillis);
  await sleep(delay);

  metrics.sseAttempts += 1;
  const baseUrl = baseUrlFor(index);
  const controller = new AbortController();
  const openedAt = Date.now();
  const closeTimer = setTimeout(() => controller.abort(), holdMillis);

  try {
    const response = await fetch(`${baseUrl}/api/v1/notifications/match/stream`, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`,
      },
      signal: controller.signal,
    });

    if (!response.ok || !response.body) {
      metrics.sseFailed += 1;
      addError(`sse_status_${response.status}`);
      return;
    }

    metrics.sseConnected += 1;
    metrics.openLatencies.push(Date.now() - openedAt);

    await readSseStream(index, response.body.getReader(), openedAt);
  } catch (error) {
    if (error.name !== 'AbortError') {
      metrics.sseFailed += 1;
      addError(error.name || 'sse_error');
    }
  } finally {
    clearTimeout(closeTimer);
    metrics.sseClosed += 1;
  }
}

async function readSseStream(index, reader) {
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      return;
    }

    metrics.bytesReceived += value.byteLength;
    buffer += decoder.decode(value, { stream: true });

    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const rawEvent = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      handleSseEvent(index, rawEvent);
      boundary = buffer.indexOf('\n\n');
    }
  }
}

function handleSseEvent(index, rawEvent) {
  const lines = rawEvent.split('\n');
  let eventName = 'message';

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
      break;
    }
  }

  if (eventName === 'connected') {
    metrics.connectedEvents += 1;
    return;
  }

  if (eventName === 'heartbeat') {
    metrics.heartbeatEvents += 1;
    return;
  }

  if (eventName === 'match_found') {
    metrics.matchFoundEvents += 1;
    const startedAt = joinStartedAtByIndex.get(index);
    if (startedAt) {
      metrics.matchFoundLatencies.push(Date.now() - startedAt);
    }
  }
}

async function joinQueues(tokens) {
  const intervalMillis = joinTps > 0 ? Math.floor(1000 / joinTps) : 0;
  const tasks = [];

  for (let index = 0; index < tokens.length; index += 1) {
    joinStartedAtByIndex.set(index, Date.now());
    tasks.push(joinQueue(index, tokens[index]));

    if (intervalMillis > 0) {
      await sleep(intervalMillis);
    }
  }

  await Promise.allSettled(tasks);
}

async function joinQueue(index, token) {
  metrics.joinAttempts += 1;

  try {
    const response = await fetch(`${baseUrlFor(index)}/api/v1/match/join`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.status === 200) {
      metrics.joinSuccess += 1;
      return;
    }

    metrics.joinFailed += 1;
    addError(`join_status_${response.status}`);
  } catch (error) {
    metrics.joinFailed += 1;
    addError(error.name || 'join_error');
  }
}

function printProgress() {
  console.log(
    `[sse-load] connected=${metrics.sseConnected}/${metrics.sseAttempts}, ` +
    `closed=${metrics.sseClosed}, heartbeat=${metrics.heartbeatEvents}, ` +
    `match_found=${metrics.matchFoundEvents}, join=${metrics.joinSuccess}/${metrics.joinAttempts}`
  );
}

function printSummary() {
  const openedRate = ratio(metrics.sseConnected, metrics.sseAttempts);
  const heartbeatPerConnection = metrics.sseConnected > 0 ? metrics.heartbeatEvents / metrics.sseConnected : 0;
  const matchFoundRate = mode === 'match' ? ratio(metrics.matchFoundEvents, metrics.joinSuccess) : null;

  console.log('\n========== SSE load test summary ==========');
  console.log(`mode: ${mode}`);
  console.log(`connections: ${totalConnections}`);
  console.log(`sse connected: ${metrics.sseConnected}/${metrics.sseAttempts} (${openedRate.toFixed(2)}%)`);
  console.log(`sse failed: ${metrics.sseFailed}`);
  console.log(`connected events: ${metrics.connectedEvents}`);
  console.log(`heartbeat events: ${metrics.heartbeatEvents} (${heartbeatPerConnection.toFixed(2)} per connection)`);
  console.log(`open latency p50/p95/p99: ${percentile(metrics.openLatencies, 0.50)}ms / ${percentile(metrics.openLatencies, 0.95)}ms / ${percentile(metrics.openLatencies, 0.99)}ms`);

  if (mode === 'match') {
    console.log(`join success: ${metrics.joinSuccess}/${metrics.joinAttempts}`);
    console.log(`match_found received: ${metrics.matchFoundEvents}/${metrics.joinSuccess} (${matchFoundRate.toFixed(2)}%)`);
    console.log(`match_found latency p50/p95/p99: ${percentile(metrics.matchFoundLatencies, 0.50)}ms / ${percentile(metrics.matchFoundLatencies, 0.95)}ms / ${percentile(metrics.matchFoundLatencies, 0.99)}ms`);
  }

  console.log(`bytes received: ${metrics.bytesReceived}`);
  console.log(`errors: ${JSON.stringify(Object.fromEntries(metrics.errors))}`);
}

function userFor(index) {
  const normalized = namespace.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
  const prefix = `sse${normalized.slice(-8)}`;

  return {
    email: `${namespace}-${index}@load.smite`,
    password,
    nickname: `${prefix}${index.toString(36)}`.slice(0, 16),
  };
}

function baseUrlFor(index) {
  return baseUrls[index % baseUrls.length];
}

function isMissingUser(result) {
  return result.status === 401 && (!result.code || result.code === 'AUTH_008');
}

function isExistingUser(result) {
  return result.status === 409 || (result.status === 400 && result.code === 'AUTH_005');
}

function validateMode() {
  if (!['connection', 'match'].includes(mode)) {
    throw new Error(`unsupported MODE=${mode}. Use connection or match.`);
  }
}

function parseDuration(value) {
  const match = String(value).trim().match(/^(\d+)(ms|s|m|h)$/);
  if (!match) {
    throw new Error(`unsupported duration: ${value}. Use values like 500ms, 30s, 5m.`);
  }

  const amount = Number(match[1]);
  const unit = match[2];

  if (unit === 'ms') return amount;
  if (unit === 's') return amount * 1000;
  if (unit === 'm') return amount * 60 * 1000;
  return amount * 60 * 60 * 1000;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function addError(name) {
  metrics.errors.set(name, (metrics.errors.get(name) || 0) + 1);
}

function ratio(numerator, denominator) {
  if (denominator === 0) {
    return 0;
  }
  return (numerator / denominator) * 100;
}

function percentile(values, p) {
  if (values.length === 0) {
    return 0;
  }

  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1);
  return Math.round(sorted[index]);
}
