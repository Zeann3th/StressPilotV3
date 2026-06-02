# k6 Checkout Benchmark Runbook

This runbook mirrors the current StressPilot YAML example:

- Active environment variable: `baseUrl`
- Flow type: `DEFAULT`
- Step 1: `POST /api/login`
- Extract: `$.token` into `token`
- Step 2: `POST /api/checkout`
- Inject: `Authorization: Bearer ${token}`
- Success condition: HTTP status `200` for both requests

The script is in `docs/benchmarks/k6-checkout-benchmark.js`.

## Profiles

The k6 script reads `PROFILE` and picks matching VUs/time. These are aligned with the JMeter runbook.

| Profile | VUs | Ramp-up | Hold | Ramp-down | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| `baseline` | 10 | 10s | 1m | 10s | 1m20s |
| `medium` | 50 | 30s | 5m | 30s | 6m |
| `high` | 100 | 60s | 10m | 60s | 12m |
| `higher` | 250 | 120s | 15m | 120s | 19m |
| `soak` | 100 | 120s | 30m | 120s | 34m |

## Run Commands

Baseline:

```bash
k6 run -e PROFILE=baseline -e BASE_URL=http://localhost:8080 docs/benchmarks/k6-checkout-benchmark.js
```

Medium:

```bash
k6 run -e PROFILE=medium -e BASE_URL=http://localhost:8080 docs/benchmarks/k6-checkout-benchmark.js
```

High:

```bash
k6 run -e PROFILE=high -e BASE_URL=http://localhost:8080 docs/benchmarks/k6-checkout-benchmark.js
```

Higher:

```bash
k6 run -e PROFILE=higher -e BASE_URL=http://localhost:8080 docs/benchmarks/k6-checkout-benchmark.js
```

Soak:

```bash
k6 run -e PROFILE=soak -e BASE_URL=http://localhost:8080 docs/benchmarks/k6-checkout-benchmark.js
```

## Target Override

For staging:

```bash
k6 run -e PROFILE=high -e BASE_URL=https://staging.example.com docs/benchmarks/k6-checkout-benchmark.js
```

## Summary Export

For machine-readable output:

```bash
k6 run -e PROFILE=high -e BASE_URL=http://localhost:8080 --summary-export target/k6-high-summary.json docs/benchmarks/k6-checkout-benchmark.js
```

## Notes

- Keep the selected profile aligned with the StressPilot and JMeter runs.
- k6 stages include ramp-down, so total wall-clock time is longer than the hold period.
- The script uses one looped user journey per VU: login, extract token, checkout, delay.
- The login and checkout checks assert HTTP status `200`.
