import http from 'k6/http';
import { check, sleep } from 'k6';

export const totalUsers = Number(__ENV.TOTAL_USERS || 1000);
export const vus = Number(__ENV.VUS || 100);
export const batchSize = Number(__ENV.SETUP_BATCH_SIZE || 100);
export const duration = __ENV.DURATION || '3m';
export const baseUrls = (__ENV.API_BASE_URLS || 'http://localhost:8080,http://localhost:8081')
  .split(',')
  .map((url) => url.trim())
  .filter((url) => url.length > 0);

const runId = __ENV.RUN_ID || `${Date.now()}`;
const password = __ENV.TEST_PASSWORD || 'loadtest123';
const nicknamePrefix = buildNicknamePrefix(runId);

export function prepareUsers(count) {
  const tokens = [];

  for (let start = 0; start < count; start += batchSize) {
    const end = Math.min(start + batchSize, count);
    const signupRequests = [];

    for (let index = start; index < end; index += 1) {
      const user = userFor(index);
      signupRequests.push({
        method: 'POST',
        url: `${baseUrlFor(index)}/api/v1/auth/signUp`,
        body: JSON.stringify(user),
        params: jsonParams('signup'),
      });
    }

    const signupResponses = http.batch(signupRequests);
    signupResponses.forEach((response, offset) => {
      const index = start + offset;
      const ok = check(response, {
        'signUp succeeded or already exists': (res) => res.status === 200 || res.status === 409,
      });

      if (!ok) {
        console.error(`signUp failed: index=${index}, status=${response.status}, body=${response.body}`);
      }
    });

    const loginRequests = [];
    for (let index = start; index < end; index += 1) {
      const user = userFor(index);
      loginRequests.push({
        method: 'POST',
        url: `${baseUrlFor(index)}/api/v1/auth/login`,
        body: JSON.stringify({ email: user.email, password: user.password }),
        params: jsonParams('login'),
      });
    }

    const loginResponses = http.batch(loginRequests);
    loginResponses.forEach((response, offset) => {
      const index = start + offset;
      const accessToken = parseAccessToken(response);
      const ok = check(response, {
        'login succeeded': (res) => res.status === 200 && Boolean(accessToken),
      });

      if (!ok) {
        throw new Error(`login failed: index=${index}, status=${response.status}, body=${response.body}`);
      }

      tokens[index] = accessToken;
    });
  }

  return { tokens };
}

export function joinQueue(index, data) {
  if (index >= data.tokens.length) {
    return;
  }

  const response = http.post(`${baseUrlFor(index)}/api/v1/match/join`, null, {
    headers: {
      Authorization: `Bearer ${data.tokens[index]}`,
    },
    tags: {
      endpoint: 'joinQueue',
    },
  });

  check(response, {
    'joinQueue succeeded': (res) => res.status === 200,
  });

  sleep(Number(__ENV.SLEEP_SECONDS || 0));
}

export function durationToSeconds(value) {
  const match = String(value).trim().match(/^(\d+)(s|m|h)$/);
  if (!match) {
    throw new Error(`unsupported duration: ${value}. Use values like 180s or 3m.`);
  }

  const amount = Number(match[1]);
  const unit = match[2];

  if (unit === 's') {
    return amount;
  }
  if (unit === 'm') {
    return amount * 60;
  }
  return amount * 60 * 60;
}

export function joinQueueThresholds() {
  return {
    http_req_failed: ['rate<0.01'],
  };
}

export function baseUrlFor(index) {
  return baseUrls[index % baseUrls.length];
}

function userFor(index) {
  return {
    email: `lt-${runId}-${index}@load.smite`,
    password,
    nickname: `${nicknamePrefix}${index.toString(36)}`.slice(0, 16),
  };
}

function buildNicknamePrefix(value) {
  const normalized = String(value).replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
  const suffix = normalized.length > 0 ? normalized.slice(-8) : `${Date.now()}`.slice(-8);
  return `lt${suffix}`;
}

function jsonParams(endpoint) {
  return {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      endpoint,
    },
  };
}

function parseAccessToken(response) {
  try {
    return response.json('accessToken');
  } catch (_) {
    return null;
  }
}
