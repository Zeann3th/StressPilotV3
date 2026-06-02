# JMeter Checkout Benchmark Runbook

This runbook mirrors the current StressPilot YAML example:

- Active environment variable: `baseUrl`
- Flow type: `DEFAULT`
- Step 1: `POST /api/login`
- Extract: `$.token` into `token`
- Step 2: `POST /api/checkout`
- Inject: `Authorization: Bearer ${token}`
- Success condition: HTTP status `200` for both requests

The test plan is in `docs/benchmarks/jmeter-checkout-benchmark.jmx`.

## Profiles

Use one of these profiles for comparable StressPilot, JMeter, and k6 runs.

| Profile | Threads | Ramp-up | Duration | Delay |
| --- | ---: | ---: | ---: | ---: |
| Baseline | 10 | 10s | 1m | 1s |
| Medium | 50 | 30s | 5m | 1s |
| High | 100 | 60s | 10m | 1s |
| Higher | 250 | 120s | 15m | 1s |
| Soak | 100 | 120s | 30m | 1s |

## Run Commands

Baseline:

```bash
jmeter -n -t docs/benchmarks/jmeter-checkout-benchmark.jmx -l target/jmeter-baseline.jtl -e -o target/jmeter-baseline-report -Jprotocol=http -Jhost=localhost -Jport=8080 -Jthreads=10 -Jramp=10 -Jduration=60 -Jdelay_ms=1000
```

Medium:

```bash
jmeter -n -t docs/benchmarks/jmeter-checkout-benchmark.jmx -l target/jmeter-medium.jtl -e -o target/jmeter-medium-report -Jprotocol=http -Jhost=localhost -Jport=8080 -Jthreads=50 -Jramp=30 -Jduration=300 -Jdelay_ms=1000
```

High:

```bash
jmeter -n -t docs/benchmarks/jmeter-checkout-benchmark.jmx -l target/jmeter-high.jtl -e -o target/jmeter-high-report -Jprotocol=http -Jhost=localhost -Jport=8080 -Jthreads=100 -Jramp=60 -Jduration=600 -Jdelay_ms=1000
```

Higher:

```bash
jmeter -n -t docs/benchmarks/jmeter-checkout-benchmark.jmx -l target/jmeter-higher.jtl -e -o target/jmeter-higher-report -Jprotocol=http -Jhost=localhost -Jport=8080 -Jthreads=250 -Jramp=120 -Jduration=900 -Jdelay_ms=1000
```

Soak:

```bash
jmeter -n -t docs/benchmarks/jmeter-checkout-benchmark.jmx -l target/jmeter-soak.jtl -e -o target/jmeter-soak-report -Jprotocol=http -Jhost=localhost -Jport=8080 -Jthreads=100 -Jramp=120 -Jduration=1800 -Jdelay_ms=1000
```

## Target Override

For staging:

```bash
jmeter -n -t docs/benchmarks/jmeter-checkout-benchmark.jmx -l target/jmeter-staging.jtl -e -o target/jmeter-staging-report -Jprotocol=https -Jhost=staging.example.com -Jport=443 -Jthreads=100 -Jramp=60 -Jduration=600 -Jdelay_ms=1000
```

## Notes

- Clear or use a new report output directory before each run; JMeter fails if the dashboard output directory already exists.
- Keep `threads`, `ramp`, `duration`, and `delay_ms` identical to the StressPilot run when comparing tools.
- The JMeter plan uses one looped user journey per thread: login, extract token, checkout, delay.
- The login and checkout response assertions check HTTP status `200`.
