import http from 'k6/http';
import { check, sleep } from 'k6';

const profiles = {
  baseline: {
    stages: [
      { duration: '10s', target: 10 },
      { duration: '1m', target: 10 },
      { duration: '10s', target: 0 },
    ],
  },
  medium: {
    stages: [
      { duration: '30s', target: 50 },
      { duration: '5m', target: 50 },
      { duration: '30s', target: 0 },
    ],
  },
  high: {
    stages: [
      { duration: '60s', target: 100 },
      { duration: '10m', target: 100 },
      { duration: '60s', target: 0 },
    ],
  },
  higher: {
    stages: [
      { duration: '120s', target: 250 },
      { duration: '15m', target: 250 },
      { duration: '120s', target: 0 },
    ],
  },
  soak: {
    stages: [
      { duration: '120s', target: 100 },
      { duration: '30m', target: 100 },
      { duration: '120s', target: 0 },
    ],
  },
};

const profileName = __ENV.PROFILE || 'baseline';
const selectedProfile = profiles[profileName] || profiles.baseline;

export const options = {
  scenarios: {
    checkout_flow: {
      executor: 'ramping-vus',
      gracefulRampDown: '30s',
      stages: selectedProfile.stages,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const thinkTimeSeconds = Number(__ENV.THINK_TIME_SECONDS || '1');

export default function () {
  const loginRes = http.post(
    `${baseUrl}/api/login`,
    JSON.stringify({ username: 'demo', password: 'demo' }),
    {
      headers: {
        'Content-Type': 'application/json',
      },
      tags: {
        flow: 'checkout',
        step: 'login',
      },
    },
  );

  check(loginRes, {
    'login status is 200': (r) => r.status === 200,
  });

  const token = loginRes.json('token');

  const checkoutRes = http.post(
    `${baseUrl}/api/checkout`,
    JSON.stringify({ sku: 'SKU-001', quantity: 1 }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      tags: {
        flow: 'checkout',
        step: 'checkout',
      },
    },
  );

  check(checkoutRes, {
    'checkout status is 200': (r) => r.status === 200,
  });

  sleep(thinkTimeSeconds);
}
