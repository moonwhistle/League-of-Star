import http from 'k6/http';
import { check, sleep } from 'k6';

export const totalUsers = Number(__ENV.TOTAL_USERS || 5000);
export const vus = Number(__ENV.VUS || 100);
export const batchSize = Number(__ENV.SETUP_BATCH_SIZE || 250);
export const duration = __ENV.DURATION || '5m';
export const baseUrls = (__ENV.API_BASE_URLS || 'http://localhost:8080,http://localhost:8081')
  .split(',')
  .map((url) => url.trim())
  .filter((url) => url.length > 0);

const userNamespace = __ENV.TEST_USER_NAMESPACE || 'lt';
const password = __ENV.TEST_PASSWORD || 'loadtest123';
const nicknamePrefix = buildNicknamePrefix(userNamespace);

export function prepareUsers(count) {
  const tokens = [];

  for (let start = 0; start < count; start += batchSize) {
    const end = Math.min(start + batchSize, count);
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
    const signupTargets = [];

    loginResponses.forEach((response, offset) => {
      const index = start + offset;
      const user = userFor(index);
      const accessToken = parseAccessToken(response);

      if (response.status === 200 && accessToken) {
        tokens[index] = accessToken;
        return;
      }

      if (!isMissingUserResponse(response)) {
        throw new Error(`login failed: index=${index}, status=${response.status}, body=${response.body}`);
      }

      signupTargets.push({ index, user });
    });

    if (signupTargets.length > 0) {
      const signupResponses = http.batch(
        signupTargets.map(({ index, user }) => ({
          method: 'POST',
          url: `${baseUrlFor(index)}/api/v1/auth/signUp`,
          body: JSON.stringify(user),
          params: jsonParams('signup'),
        })),
      );

      signupResponses.forEach((response, offset) => {
        const { index } = signupTargets[offset];
        if (!isExistingUserResponse(response) && response.status !== 200) {
          throw new Error(`signUp failed: index=${index}, status=${response.status}, body=${response.body}`);
        }
      });

      const reloginRequests = signupTargets.map(({ index, user }) => ({
        method: 'POST',
        url: `${baseUrlFor(index)}/api/v1/auth/login`,
        body: JSON.stringify({ email: user.email, password: user.password }),
        params: jsonParams('login'),
      }));

      const reloginResponses = http.batch(reloginRequests);
      reloginResponses.forEach((response, offset) => {
        const { index } = signupTargets[offset];
        const accessToken = parseAccessToken(response);
        const ok = check(response, {
          'login succeeded after signUp': (res) => res.status === 200 && Boolean(accessToken),
        });

        if (!ok) {
          throw new Error(`login after signUp failed: index=${index}, status=${response.status}, body=${response.body}`);
        }

        tokens[index] = accessToken;
      });
    }
  }

  if (tokens.length < count || tokens.some((token) => !token)) {
    throw new Error(`setup failed: expected ${count} tokens, got ${tokens.filter((token) => Boolean(token)).length}`);
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
  const match = String(value).trim().match(/^(\d+)([smh])$/);
  if (!match) {
    throw new Error(`unsupported duration: ${value}. Use values like 300s or 5m.`);
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
    'http_req_failed{endpoint:joinQueue}': ['rate<0.01'],
  };
}

export function baseUrlFor(index) {
  return baseUrls[index % baseUrls.length];
}

function userFor(index) {
  return {
    email: `${userNamespace}-${index}@load.leagueofstar`,
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

function isMissingUserResponse(response) {
  if (response.status === 401) {
    try {
      return response.json('code') === 'AUTH_008';
    } catch (_) {
      return true;
    }
  }

  return false;
}

function isExistingUserResponse(response) {
  if (response.status === 409) {
    return true;
  }

  if (response.status !== 400) {
    return false;
  }

  try {
    return response.json('code') === 'AUTH_005';
  } catch (_) {
    return false;
  }
}
