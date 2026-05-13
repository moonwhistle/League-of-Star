const totalUsers = Number(process.env.USERS || process.env.TOTAL_USERS || 5000);
const joinTps = Number(process.env.JOIN_TPS || 50);
const setupConcurrency = Number(process.env.SETUP_CONCURRENCY || 100);
const connectWaitMillis = parseDuration(process.env.CONNECT_WAIT || '5s');
const scenario = process.env.SCENARIO || 'mixed';
const namespace = process.env.TEST_USER_NAMESPACE || `mrt${Date.now()}`;
const password = process.env.TEST_PASSWORD || 'loadtest123';
const responseDelayMillis = Number(process.env.RESPONSE_DELAY_MS || 100);
const timeoutWaitMillis = parseDuration(process.env.TIMEOUT_WAIT || '15s');
const holdMillis = process.env.HOLD_DURATION
  ? parseDuration(process.env.HOLD_DURATION)
  : defaultHoldMillis(totalUsers, joinTps, timeoutWaitMillis, connectWaitMillis);
const cleanupRequeued = process.env.CLEANUP_REQUEUED === 'true';
const mixedWeights = parseMixedWeights(process.env.MIXED_WEIGHTS);
const baseUrls = (process.env.API_BASE_URLS || 'http://localhost:8080,http://localhost:8081')
  .split(',')
  .map((url) => url.trim())
  .filter(Boolean);

const tokens = [];
const controllers = [];
const joinStartedAtByIndex = new Map();
const matchById = new Map();
const handledMatches = new Set();
const handledUsers = new Set();
const pendingPairResolvers = [];

const metrics = {
  usersReady: 0,
  sseAttempts: 0,
  sseConnected: 0,
  sseFailed: 0,
  connectedEvents: 0,
  heartbeatEvents: 0,
  matchFoundEvents: 0,
  pairsReady: 0,
  pairsHandled: 0,
  pairFailures: 0,
  rematchEvents: 0,
  joinsAttempted: 0,
  joinsSucceeded: 0,
  joinsFailed: 0,
  acceptAttempts: 0,
  acceptSucceeded: 0,
  acceptFailed: 0,
  rejectAttempts: 0,
  rejectSucceeded: 0,
  rejectFailed: 0,
  leaveAttempts: 0,
  leaveSucceeded: 0,
  leaveFailed: 0,
  scenarioCounts: new Map(),
  matchFoundLatencies: [],
  responseLatencies: [],
  errors: new Map(),
  expectedInitialPairs: Math.floor(totalUsers / 2),
};

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

async function main() {
  validateScenario();
  validateUserCount();

  console.log(`[match-response-load] scenario=${scenario}`);
  console.log(`[match-response-load] users=${totalUsers}, joinTps=${joinTps}, namespace=${namespace}`);
  console.log(`[match-response-load] api=${baseUrls.join(',')}`);

  const reporter = setInterval(printProgress, 10_000);
  const preparedTokens = await prepareUsers(totalUsers);
  preparedTokens.forEach((token, index) => {
    tokens[index] = token;
  });
  metrics.usersReady = tokens.length;

  const sseTasks = tokens.map((token, index) => openSse(index, token));
  await sleep(connectWaitMillis);
  await joinQueues();
  await waitForExpectedPairs(metrics.expectedInitialPairs, timeoutWaitMillis);

  if (usesTimeoutScenario()) {
    console.log(`[match-response-load] waiting for timeout settlement: ${timeoutWaitMillis}ms`);
    await sleep(timeoutWaitMillis);
  }

  controllers.forEach((controller) => controller.abort());
  await Promise.allSettled(sseTasks);
  clearInterval(reporter);
  printSummary();
}

async function prepareUsers(count) {
  const result = new Array(count);
  let cursor = 0;

  async function worker() {
    while (cursor < count) {
      const index = cursor;
      cursor += 1;
      result[index] = await prepareUser(index);
    }
  }

  await Promise.all(Array.from({ length: Math.min(setupConcurrency, count) }, worker));
  return result;
}

async function prepareUser(index) {
  const user = userFor(index);
  const loginResult = await login(baseUrlFor(index), user.email, user.password);

  if (loginResult.ok) {
    return loginResult.token;
  }
  if (!isMissingUser(loginResult)) {
    throw new Error(`login failed. index=${index}, status=${loginResult.status}, body=${loginResult.body}`);
  }

  const signUpResult = await signUp(baseUrlFor(index), user);
  if (!signUpResult.ok && !isExistingUser(signUpResult)) {
    throw new Error(`signUp failed. index=${index}, status=${signUpResult.status}, body=${signUpResult.body}`);
  }

  const reloginResult = await login(baseUrlFor(index), user.email, user.password);
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
  return parseAuthResponse(response);
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
  return { ok: response.status === 200, status: response.status, body, code: parsed.code };
}

async function parseAuthResponse(response) {
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

async function openSse(index, token) {
  metrics.sseAttempts += 1;
  const controller = new AbortController();
  controllers[index] = controller;
  const closeTimer = setTimeout(() => controller.abort(), holdMillis);

  try {
    const response = await fetch(`${baseUrlFor(index)}/api/v1/notifications/match/stream`, {
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
    await readSseStream(index, response.body.getReader());
  } catch (error) {
    if (!isExpectedSseClose(error)) {
      metrics.sseFailed += 1;
      addError(errorKey(error, 'sse_error'));
    }
  } finally {
    clearTimeout(closeTimer);
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
  const event = parseSseEvent(rawEvent);
  if (event.name === 'connected') {
    metrics.connectedEvents += 1;
    return;
  }
  if (event.name === 'heartbeat') {
    metrics.heartbeatEvents += 1;
    return;
  }
  if (event.name !== 'match_found') {
    return;
  }

  const payload = parseJson(event.data);
  if (!payload?.matchId) {
    addError('match_found_without_matchId');
    return;
  }

  metrics.matchFoundEvents += 1;
  if (handledUsers.has(index)) {
    metrics.rematchEvents += 1;
    return;
  }

  const startedAt = joinStartedAtByIndex.get(index);
  if (startedAt) {
    metrics.matchFoundLatencies.push(Date.now() - startedAt);
  }

  const match = matchById.get(payload.matchId) || {
    matchId: payload.matchId,
    acceptTimeoutSeconds: payload.acceptTimeoutSeconds || 10,
    members: [],
    createdAt: Date.now(),
    polluted: false,
  };

  if (!match.members.some((member) => member.index === index)) {
    match.members.push({
      index,
      token: tokens[index],
      userId: payload.userId,
      opponentUserId: payload.opponentUserId,
    });
  }
  matchById.set(payload.matchId, match);

  if (match.members.length >= 2 && !handledMatches.has(match.matchId)) {
    handledMatches.add(match.matchId);
    if (match.members.some((member) => handledUsers.has(member.index)) || match.polluted) {
      match.members.forEach((member) => handledUsers.add(member.index));
      metrics.rematchEvents += match.members.length;
      return;
    }
    match.members.forEach((member) => handledUsers.add(member.index));
    metrics.pairsReady += 1;
    resolvePairWaiters();
    handlePair(match)
      .then(() => {
        metrics.pairsHandled += 1;
      })
      .catch((error) => {
        metrics.pairFailures += 1;
        addError(errorKey(error, 'pair_error'));
        console.error(`[match-response-load] pair failed. matchId=${match.matchId}`, error);
      });
  }
}

async function handlePair(match) {
  const selectedScenario = scenario === 'mixed' ? selectMixedScenario(metrics.pairsReady - 1) : scenario;
  incrementMap(metrics.scenarioCounts, selectedScenario);

  if (selectedScenario === 'both_accept') {
    await respondBothAccept(match);
    return;
  }
  if (selectedScenario === 'reject_accept') {
    await respondRejectAccept(match);
    return;
  }
  if (selectedScenario === 'accept_reject') {
    await respondAcceptReject(match);
    return;
  }
  if (selectedScenario === 'one_reject_other_silent') {
    await respondOneRejectOtherSilent(match);
    return;
  }
  if (selectedScenario === 'one_accept_other_timeout') {
    await respondOneAcceptOtherTimeout(match);
    return;
  }
  if (selectedScenario === 'both_timeout') {
    return;
  }

  throw new Error(`unsupported scenario selected: ${selectedScenario}`);
}

async function respondBothAccept(match) {
  const [a, b] = match.members;
  await Promise.all([
    accept(match.matchId, a),
    delayed(() => accept(match.matchId, b), responseDelayMillis),
  ]);
}

async function respondRejectAccept(match) {
  const [rejecter, accepter] = match.members;
  await reject(match.matchId, rejecter);
  await delayed(() => accept(match.matchId, accepter), responseDelayMillis);
  await cleanupReturnedUser(accepter);
}

async function respondAcceptReject(match) {
  const [accepter, rejecter] = match.members;
  await accept(match.matchId, accepter);
  await delayed(() => reject(match.matchId, rejecter), responseDelayMillis);
  await cleanupReturnedUser(accepter);
}

async function respondOneRejectOtherSilent(match) {
  const [rejecter] = match.members;
  await reject(match.matchId, rejecter);
}

async function respondOneAcceptOtherTimeout(match) {
  const [accepter] = match.members;
  await accept(match.matchId, accepter);
  await delayed(() => cleanupReturnedUser(accepter), timeoutWaitMillis);
}

async function accept(matchId, member) {
  metrics.acceptAttempts += 1;
  const startedAt = Date.now();
  let response;
  try {
    response = await sendRequest(
      'accept',
      `${baseUrlFor(member.index)}/api/v1/match/${matchId}/accept`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${member.token}` },
      }
    );
  } catch (error) {
    metrics.acceptFailed += 1;
    metrics.responseLatencies.push(Date.now() - startedAt);
    throw error;
  }
  metrics.responseLatencies.push(Date.now() - startedAt);

  if (response.status === 200) {
    metrics.acceptSucceeded += 1;
    return;
  }

  metrics.acceptFailed += 1;
  addError(`accept_status_${response.status}`);
}

async function reject(matchId, member) {
  metrics.rejectAttempts += 1;
  const startedAt = Date.now();
  let response;
  try {
    response = await sendRequest(
      'reject',
      `${baseUrlFor(member.index)}/api/v1/match/${matchId}/reject`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${member.token}` },
      }
    );
  } catch (error) {
    metrics.rejectFailed += 1;
    metrics.responseLatencies.push(Date.now() - startedAt);
    throw error;
  }
  metrics.responseLatencies.push(Date.now() - startedAt);

  if (response.status === 200) {
    metrics.rejectSucceeded += 1;
    return;
  }

  metrics.rejectFailed += 1;
  addError(`reject_status_${response.status}`);
}

async function cleanupReturnedUser(member) {
  if (!cleanupRequeued) {
    return;
  }

  metrics.leaveAttempts += 1;
  try {
    const response = await sendRequest(
      'leave',
      `${baseUrlFor(member.index)}/api/v1/match/leave`,
      {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${member.token}` },
      }
    );

    if (response.status === 200) {
      metrics.leaveSucceeded += 1;
      return;
    }

    metrics.leaveFailed += 1;
    addError(`leave_status_${response.status}`);
  } catch (error) {
    metrics.leaveFailed += 1;
    addError(errorKey(error, 'leave_error'));
  }
}

async function joinQueues() {
  const intervalMillis = joinTps > 0 ? Math.floor(1000 / joinTps) : 0;
  const tasks = [];

  for (let index = 0; index < tokens.length; index += 1) {
    joinStartedAtByIndex.set(index, Date.now());
    tasks.push(joinQueue(index));

    if (intervalMillis > 0) {
      await sleep(intervalMillis);
    }
  }

  await Promise.allSettled(tasks);
}

async function joinQueue(index) {
  metrics.joinsAttempted += 1;

  try {
    const response = await sendRequest(
      'join',
      `${baseUrlFor(index)}/api/v1/match/join`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokens[index]}` },
      }
    );

    if (response.status === 200) {
      metrics.joinsSucceeded += 1;
      return;
    }

    metrics.joinsFailed += 1;
    addError(`join_status_${response.status}`);
  } catch (error) {
    metrics.joinsFailed += 1;
    addError(errorKey(error, 'join_error'));
  }
}

function parseSseEvent(rawEvent) {
  const lines = rawEvent.split('\n');
  let name = 'message';
  const data = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      name = line.slice('event:'.length).trim();
    }
    if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).trimStart());
    }
  }

  return { name, data: data.join('\n') };
}

function parseJson(value) {
  try {
    return JSON.parse(value);
  } catch (_) {
    return null;
  }
}

async function waitForExpectedPairs(expectedPairs, waitMillis) {
  const deadline = Date.now() + waitMillis;
  while (metrics.pairsReady < expectedPairs && Date.now() < deadline) {
    await new Promise((resolve) => {
      pendingPairResolvers.push(resolve);
      setTimeout(resolve, 500);
    });
  }
}

function resolvePairWaiters() {
  while (pendingPairResolvers.length > 0) {
    pendingPairResolvers.pop()();
  }
}

function printProgress() {
  console.log(
    `[match-response-load] pairs=${metrics.pairsHandled}/${metrics.pairsReady}, ` +
    `match_found=${metrics.matchFoundEvents}, rematch=${metrics.rematchEvents}, join=${metrics.joinsSucceeded}/${metrics.joinsAttempted}, ` +
    `accept=${metrics.acceptSucceeded}/${metrics.acceptAttempts}, reject=${metrics.rejectSucceeded}/${metrics.rejectAttempts}`
  );
}

function printSummary() {
  console.log('\n========== match response load test summary ==========');
  console.log(`scenario: ${scenario}`);
  console.log(`users: ${totalUsers}`);
  console.log(`users ready: ${metrics.usersReady}`);
  console.log(`sse connected: ${metrics.sseConnected}/${metrics.sseAttempts}`);
  console.log(`connected events: ${metrics.connectedEvents}`);
  console.log(`heartbeat events: ${metrics.heartbeatEvents}`);
  console.log(`join success: ${metrics.joinsSucceeded}/${metrics.joinsAttempted}`);
  console.log(`match_found events: ${metrics.matchFoundEvents}`);
  console.log(`rematch events ignored: ${metrics.rematchEvents}`);
  console.log(`ready pairs: ${metrics.pairsReady}/${metrics.expectedInitialPairs}`);
  console.log(`handled pairs: ${metrics.pairsHandled}/${metrics.pairsReady}`);
  console.log(`pair failures: ${metrics.pairFailures}`);
  console.log(`scenario counts: ${JSON.stringify(Object.fromEntries(metrics.scenarioCounts))}`);
  console.log(`accept success: ${metrics.acceptSucceeded}/${metrics.acceptAttempts}`);
  console.log(`reject success: ${metrics.rejectSucceeded}/${metrics.rejectAttempts}`);
  console.log(`leave cleanup success: ${metrics.leaveSucceeded}/${metrics.leaveAttempts}`);
  console.log(`match_found latency p50/p95/p99: ${percentile(metrics.matchFoundLatencies, 0.50)}ms / ${percentile(metrics.matchFoundLatencies, 0.95)}ms / ${percentile(metrics.matchFoundLatencies, 0.99)}ms`);
  console.log(`response latency p50/p95/p99: ${percentile(metrics.responseLatencies, 0.50)}ms / ${percentile(metrics.responseLatencies, 0.95)}ms / ${percentile(metrics.responseLatencies, 0.99)}ms`);
  console.log(`errors: ${JSON.stringify(Object.fromEntries(metrics.errors))}`);
}

function selectMixedScenario(pairIndex) {
  const total = mixedWeights.reduce((sum, item) => sum + item.weight, 0);
  let point = pairIndex % total;
  for (const item of mixedWeights) {
    if (point < item.weight) {
      return item.name;
    }
    point -= item.weight;
  }
  return mixedWeights[0].name;
}

function parseMixedWeights(value) {
  if (!value) {
    return [
      { name: 'both_accept', weight: 60 },
      { name: 'accept_reject', weight: 15 },
      { name: 'reject_accept', weight: 15 },
      { name: 'one_accept_other_timeout', weight: 5 },
      { name: 'both_timeout', weight: 5 },
    ];
  }

  return value.split(',')
    .map((part) => {
      const [name, weight] = part.split(':');
      return { name: name.trim(), weight: Number(weight) };
    })
    .filter((item) => item.name && item.weight > 0);
}

function usesTimeoutScenario() {
  if (['one_reject_other_silent', 'one_accept_other_timeout', 'both_timeout', 'mixed'].includes(scenario)) {
    return true;
  }
  return false;
}

async function sendRequest(endpoint, url, options) {
  try {
    return await fetch(url, options);
  } catch (error) {
    addError(`${endpoint}_${errorKey(error, 'fetch_error')}`);
    throw error;
  }
}

function isExpectedSseClose(error) {
  if (error?.name === 'AbortError') {
    return true;
  }
  if (error?.name === 'TypeError' && error?.message === 'terminated') {
    return true;
  }
  return false;
}

function validateScenario() {
  const supported = new Set([
    'both_accept',
    'reject_accept',
    'accept_reject',
    'one_reject_other_silent',
    'one_accept_other_timeout',
    'both_timeout',
    'mixed',
  ]);

  if (!supported.has(scenario)) {
    throw new Error(`unsupported SCENARIO=${scenario}. supported=${Array.from(supported).join(',')}`);
  }

  for (const item of mixedWeights) {
    if (!supported.has(item.name) || item.name === 'mixed') {
      throw new Error(`unsupported MIXED_WEIGHTS scenario: ${item.name}`);
    }
  }
}

function validateUserCount() {
  if (totalUsers < 2 || totalUsers % 2 !== 0) {
    throw new Error('USERS must be an even number greater than or equal to 2.');
  }
}

function userFor(index) {
  const normalized = namespace.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
  const prefix = `mrt${normalized.slice(-8)}`;
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

function defaultHoldMillis(users, tps, timeoutWait, connectWait) {
  const joinDurationMillis = Math.ceil(users / Math.max(tps, 1)) * 1000;
  return joinDurationMillis + timeoutWait + connectWait + 30_000;
}

async function delayed(task, delayMillis) {
  await sleep(delayMillis);
  return task();
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function addError(name) {
  incrementMap(metrics.errors, name);
}

function errorKey(error, fallback) {
  const name = error?.name || fallback;
  const message = error?.message ? `:${error.message}` : '';
  return `${name}${message}`.slice(0, 120);
}

function incrementMap(map, name) {
  map.set(name, (map.get(name) || 0) + 1);
}

function percentile(values, p) {
  if (values.length === 0) {
    return 0;
  }

  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1);
  return Math.round(sorted[index]);
}
