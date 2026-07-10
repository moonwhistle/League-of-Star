import http from 'k6/http';
import { check } from 'k6';

// Paste a local access token here when measuring manually.
// Keep this value empty before committing.
const ACCESS_TOKEN = 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzYW5naHVpZ2dAbmF2ZXIuY29tIiwidXNlcklkIjoxMzEwNzEsImF1dGgiOiJST0xFX1VTRVIiLCJpYXQiOjE3ODM2NTg1NzIsImV4cCI6MTc4MzY2MjE3Mn0.aeifPp-UAcTjXbZn1_k1uY3hyau78maznmElG2jMtobokhKTiQ6ev9d13TIhdLCk68hAWk5Tieessr3oJE8YuA';

const apiBaseUrl = (__ENV.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const mode = __ENV.MODE || 'baseline';
const limit = Number(__ENV.LIMIT || 50);
const iterations = Number(__ENV.ITERATIONS || 30);
const vus = Number(__ENV.VUS || 1);
const token = ACCESS_TOKEN || __ENV.TOKEN;

const paths = {
  baseline: '/api/v1/rankings/baseline',
  optimized: '/api/v1/rankings',
};

if (!paths[mode]) {
  throw new Error(`Unsupported MODE=${mode}. Use baseline or optimized.`);
}

if (!token) {
  throw new Error('Access token is required. Paste it into ACCESS_TOKEN at the top of this script.');
}

export const options = {
  scenarios: {
    ranking_api_repeat: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
  },
};

export default function () {
  const url = `${apiBaseUrl}${paths[mode]}?limit=${limit}`;
  const response = http.get(url, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
    tags: {
      endpoint: mode,
    },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
    'has entries': (r) => {
      try {
        const body = r.json();
        return Array.isArray(body.entries);
      } catch {
        return false;
      }
    },
  });
}
