import { writeFile } from 'node:fs/promises';

const totalUsers = Number(process.env.TOTAL_USERS || 5000);
const concurrency = Number(process.env.PREP_CONCURRENCY || 50);
const baseUrls = (process.env.API_BASE_URLS || 'http://localhost:8080,http://localhost:8081')
  .split(',')
  .map((url) => url.trim())
  .filter(Boolean);
const namespace = process.env.TEST_USER_NAMESPACE || 'lt';
const password = process.env.TEST_PASSWORD || 'loadtest123';
const output = process.env.TOKENS_FILE || '/tmp/league-of-star-load-test-tokens.json';
const nicknamePrefix = buildNicknamePrefix(namespace);
const tokens = new Array(totalUsers);
let nextIndex = 0;

async function request(url, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch (_) {
    payload = null;
  }
  return { status: response.status, payload, text };
}

async function prepare(index) {
  const baseUrl = baseUrls[index % baseUrls.length];
  const user = userFor(index);
  let login = await request(`${baseUrl}/api/v1/auth/login`, {
    email: user.email,
    password: user.password,
  });

  if (login.status !== 200 || !login.payload?.accessToken) {
    if (login.status !== 401 || login.payload?.code !== 'AUTH_008') {
      throw new Error(`login failed: index=${index}, status=${login.status}, body=${login.text}`);
    }

    const signup = await request(`${baseUrl}/api/v1/auth/signUp`, user);
    const alreadyExists = signup.status === 409
      || (signup.status === 400 && signup.payload?.code === 'AUTH_005');
    if (signup.status !== 200 && !alreadyExists) {
      throw new Error(`signup failed: index=${index}, status=${signup.status}, body=${signup.text}`);
    }

    login = await request(`${baseUrl}/api/v1/auth/login`, {
      email: user.email,
      password: user.password,
    });
  }

  if (login.status !== 200 || !login.payload?.accessToken) {
    throw new Error(`final login failed: index=${index}, status=${login.status}, body=${login.text}`);
  }
  tokens[index] = login.payload.accessToken;
}

async function worker() {
  while (true) {
    const index = nextIndex;
    nextIndex += 1;
    if (index >= totalUsers) {
      return;
    }
    await prepare(index);
    if ((index + 1) % 500 === 0) {
      process.stdout.write(`prepared ${index + 1}/${totalUsers}\n`);
    }
  }
}

await Promise.all(Array.from({ length: Math.min(concurrency, totalUsers) }, () => worker()));
await writeFile(output, JSON.stringify({ tokens }), { mode: 0o600 });
process.stdout.write(`wrote ${tokens.length} tokens to ${output}\n`);

function userFor(index) {
  return {
    email: `${namespace}-${index}@load.leagueofstar`,
    password,
    nickname: `${nicknamePrefix}${index.toString(36)}`.slice(0, 16),
  };
}

function buildNicknamePrefix(value) {
  const normalized = String(value).replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
  const suffix = normalized.length > 0 ? normalized.slice(-8) : `${Date.now()}`.slice(-8);
  return `lt${suffix}`;
}
