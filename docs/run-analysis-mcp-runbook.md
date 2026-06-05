# Run Analysis MCP Runbook

This runbook explains how to expose StressPilot run results to AI clients through MCP and how to pull a complete run dump for analysis.

## What The Tool Returns

Use the MCP tool `getRunAnalysisDump` with a StressPilot run ID.

The response is plain JSON-friendly data, not an XLSX file:

- `run`: run metadata such as ID, status, threads, duration, loop count, ramp-up time, start time, and completion time.
- `report`: the same calculated metrics used by report charts and summary sheets, including request totals, success/failure rates, response percentiles, TPS, CCUs, endpoint stats, and configured run settings.
- `logCount`: number of detailed request logs included.
- `logs`: every streamed detailed request log for that run, including endpoint ID/name, status code, success flag, response time, correlation ID, active thread count, raw request text, raw response text, and timestamp.

REST clients can read the same data from:

```text
GET http://127.0.0.1:52000/api/v1/runs/{runId}/analysis-dump
```

## Start StressPilot

Start the desktop app or run the backend directly. The default backend address is:

```text
http://127.0.0.1:52000
```

The MCP server is enabled by `src/main/resources/application.yaml`:

```yaml
spring:
  ai:
    mcp:
      server:
        transport: SSE
        sse:
          path: /mcp/sse
        message:
          path: /mcp/message
```

The MCP SSE endpoint is:

```text
http://127.0.0.1:52000/mcp/sse
```

## Configure AI Clients

These examples assume the AI client runs on the same machine as StressPilot. Keep StressPilot running while the client connects.

### Claude Code

Claude Code supports adding SSE servers from the CLI:

```bash
claude mcp add --transport sse stresspilot http://127.0.0.1:52000/mcp/sse
claude mcp list
```

Inside Claude Code, run:

```text
/mcp
```

Then ask it to call `getRunAnalysisDump` with the run ID.

### Gemini CLI

Gemini CLI selects SSE transport when an MCP server entry uses `url`.

Add this to the Gemini MCP config file used by your Gemini CLI installation:

```json
{
  "mcpServers": {
    "stresspilot": {
      "url": "http://127.0.0.1:52000/mcp/sse",
      "timeout": 30000,
      "trust": true
    }
  }
}
```

Restart Gemini CLI and ask it to call `getRunAnalysisDump` with the run ID.

### Codex CLI

Codex stores MCP servers in `~/.codex/config.toml` and supports adding URL-based MCP servers from the CLI.

Try:

```bash
codex mcp add stresspilot --url http://127.0.0.1:52000/mcp/sse
codex mcp list
```

Or add it manually:

```toml
[mcp_servers.stresspilot]
url = "http://127.0.0.1:52000/mcp/sse"
```

If a Codex version expects streamable HTTP only and rejects the SSE endpoint, use the REST endpoint above for run dumps until StressPilot is moved from SSE transport to streamable HTTP.

## Prompt Pattern

Use prompts that force the client to request the raw dump before analysis:

```text
Use the StressPilot MCP server. Call getRunAnalysisDump with runId "RUN_ID_HERE".
Analyze the full dump: summarize failures, slow endpoints, percentile/tps issues, and show examples from raw request/response logs.
```

For very large runs, the MCP response can be large. If the client truncates tool output, use the REST endpoint directly or add pagination/filtering before using the data for model analysis.

## Verification

1. Start StressPilot and confirm the backend is listening on `127.0.0.1:52000`.
2. Confirm the client can list the `stresspilot` MCP server.
3. Run a flow and copy the generated run ID.
4. Call `getRunAnalysisDump` with that run ID.
5. Confirm `logCount` equals the number of entries in `logs`.
6. Confirm request and response fields are present in representative logs.

## References

- OpenAI Codex MCP configuration: https://developers.openai.com/learn/docs-mcp
- Claude Code MCP configuration: https://code.claude.com/docs/en/mcp
- Gemini CLI MCP configuration: https://github.com/google-gemini/gemini-cli/blob/main/docs/tools/mcp-server.md
