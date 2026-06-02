# Project Import and Benchmark Equivalence

This document describes the StressPilot project YAML format and how to build equivalent tests in JMeter and k6 for later benchmark comparisons.

## Import Compatibility

Legacy project imports still work.

Older exports used a single top-level `environment` list:

```yaml
stresspilot:
  project:
    name: Legacy Demo
    description: Old single-environment project
  environment:
    - name: baseUrl
      value: http://localhost:8080
    - name: token
      value: demo
      active: false
```

When this shape is imported, StressPilot creates one project environment named `Default`, marks it active, and imports the old variables into it.

The preferred format is now `environments`, which allows multiple named environments:

```yaml
stresspilot:
  project:
    name: Multi Env Demo
    description: Project with local and staging variables
  environments:
    - name: Local
      active: true
      variables:
        - name: baseUrl
          value: http://localhost:8080
        - name: token
          value: local-token
    - name: Staging
      variables:
        - name: baseUrl
          value: https://staging.example.com
        - name: token
          value: staging-token
```

Rules:

- Use `environments` for new files.
- Keep `environment` only for legacy files.
- If both are present, `environments` is the project environment set used by the importer.
- If no environment is marked `active: true`, the first imported environment becomes the active environment.
- Variable `active` defaults to `true`.

## Flow Type

Flows can now include `type`:

```yaml
flows:
  - name: Login Flow
    description: Create session and fetch profile
    type: DEFAULT
    steps:
      - name: Login
        type: ENDPOINT
        endpoint: login
        post_process:
          extract:
            - name: token
              value: $.token
      - name: Profile
        type: ENDPOINT
        endpoint: profile
        pre_process:
          inject:
            - name: Authorization
              value: Bearer ${token}
```

Supported built-in flow types are `DEFAULT`, `BREAKPOINT`, and `DISTRIBUTED`. If `type` is omitted, it defaults to `DEFAULT`.

During import, a flow that cannot be created is skipped and the importer continues with the next flow. This is intentional for shared project files where a user may not have a plugin that owns a specific flow type or step behavior.

## Equivalent Benchmark Flow

For apples-to-apples comparisons across StressPilot, JMeter, and k6, keep these inputs identical:

- Same target base URL.
- Same endpoint order.
- Same request method, path, headers, body, and query parameters.
- Same think time or delay.
- Same virtual users and duration.
- Same success condition.
- Same token/correlation extraction and injection.

Example StressPilot project:

```yaml
stresspilot:
  project:
    name: Checkout Benchmark
  environments:
    - name: Local
      active: true
      variables:
        - name: baseUrl
          value: http://localhost:8080
  endpoints:
    - id: login
      name: Login
      type: HTTP
      method: POST
      url: ${baseUrl}/api/login
      headers:
        Content-Type: application/json
      body:
        username: demo
        password: demo
      success_condition: status == 200
    - id: checkout
      name: Checkout
      type: HTTP
      method: POST
      url: ${baseUrl}/api/checkout
      headers:
        Content-Type: application/json
      body:
        sku: SKU-001
        quantity: 1
      success_condition: status == 200
  flows:
    - name: Login Then Checkout
      type: DEFAULT
      steps:
        - name: Login
          type: ENDPOINT
          endpoint: login
          post_process:
            extract:
              - name: token
                value: $.token
        - name: Checkout
          type: ENDPOINT
          endpoint: checkout
          pre_process:
            inject:
              - name: Authorization
                value: Bearer ${token}
```

## JMeter Equivalent

Create one Thread Group with the same load profile as the StressPilot run:

- Number of Threads: same as StressPilot users.
- Ramp-up Period: same ramp-up.
- Duration or Loop Count: same test length.

Inside the Thread Group:

1. Add an HTTP Request Defaults element:
   - Server Name or IP: `localhost`
   - Port: `8080`
   - Protocol: `http`

2. Add HTTP Header Manager:
   - `Content-Type: application/json`

3. Add HTTP Request named `Login`:
   - Method: `POST`
   - Path: `/api/login`
   - Body Data:

```json
{"username":"demo","password":"demo"}
```

4. Add JSON Extractor under `Login`:
   - Name of created variable: `token`
   - JSON Path expression: `$.token`

5. Add Response Assertion under `Login`:
   - Field to Test: Response Code
   - Pattern: `200`

6. Add HTTP Request named `Checkout`:
   - Method: `POST`
   - Path: `/api/checkout`
   - Body Data:

```json
{"sku":"SKU-001","quantity":1}
```

7. Add Header Manager under `Checkout` or at the Thread Group level:
   - `Authorization: Bearer ${token}`

8. Add Response Assertion under `Checkout`:
   - Field to Test: Response Code
   - Pattern: `200`

For delays, add a Constant Timer between requests with the same delay configured in the StressPilot step.

## k6 Equivalent

Use the same virtual users, duration, endpoints, request bodies, headers, and extraction logic:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '1m',
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const loginRes = http.post(
    `${baseUrl}/api/login`,
    JSON.stringify({ username: 'demo', password: 'demo' }),
    { headers: { 'Content-Type': 'application/json' } }
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
    }
  );

  check(checkoutRes, {
    'checkout status is 200': (r) => r.status === 200,
  });

  sleep(1);
}
```

Run it with:

```bash
k6 run -e BASE_URL=http://localhost:8080 checkout-benchmark.js
```

## Benchmark Notes

When comparing results, record:

- Tool and version.
- Test machine CPU, memory, OS, and network location.
- Target service version and deployment mode.
- Virtual users, ramp-up, duration, and delay.
- Whether response bodies are parsed for extraction.
- Whether results include warm-up time.

StressPilot, JMeter, and k6 do not have identical runtimes, schedulers, metrics aggregation, or HTTP client internals. Compare the same user journey and load profile, then interpret differences as tool/runtime overhead plus target behavior.
