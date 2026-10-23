# MCP bridge — authorization & audit layer

This package is the security foundation for letting a local AI agent (over the
Model Context Protocol) call privileged device operations through the Shizuku
server. It is built **security-first, on purpose**: this layer ships before any
capability tool exists, so the gate and audit trail are in place before
anything privileged can be wired in.

The first read-only capability (`packages.list`), a loopback-only agent
transport, and the human confirmation channel now exist. The transport is
**disabled by default**; the device does not listen for agents until the user
turns it on from the manager. Every agent request still passes through the
gate, so an enabled transport grants nothing on its own. A tool the gate marks
`REQUIRE_CONFIRMATION` is parked until the user approves it in the manager —
no approval, no run.

## Components

| Class | Responsibility |
|-------|----------------|
| `DangerLevel` | Risk class of a tool: `READ_ONLY`, `STATE_CHANGE`, `SENSITIVE`. |
| `McpTool` / `McpToolRegistry` | Static descriptor of a tool and the complete, auditable set of known tools. Ships empty; capability modules register here later. |
| `McpPolicy` | Persisted model: paired agents and their allowlists (JSON). |
| `Tokens` | Pairing-token generation (256-bit `SecureRandom`), SHA-256 hashing, constant-time compare. |
| `McpPolicyManager` | Pairing, revocation, allowlist/trust edits, token→agent resolution. Atomic JSON persistence, matching `ShizukuConfigManager`. |
| `McpDecision` | `ALLOW` / `DENY` / `REQUIRE_CONFIRMATION`. |
| `McpGate` | The single enforcement point. Every call is authorized and audited here. |
| `McpAuditLog` | Append-only JSONL trail of every decision and outcome, with a bounded in-memory tail for the manager UI. |
| `CapabilityTool` | A runnable tool: its descriptor plus an `execute`. |
| `PackagesListTool` | Lists installed packages (`READ_ONLY`). |
| `SettingsGetTool` / `SettingsPutTool` | Read / write one setting. `get` is `READ_ONLY`; `put` is `STATE_CHANGE` (confirmation-gated). |
| `SettingsAccess` / `ProviderSettingsAccess` | Settings I/O abstraction; the real impl drives the system `settings` content provider. |
| `McpExecutor` | Runs capabilities strictly behind the gate; publishes their descriptors into the registry. |
| `McpTransportServer` | Loopback-only, newline-delimited JSON request/response server for agents. Disabled by default. |
| `McpConfirmationManager` | Parks calls that need a human decision; blocks the agent call until approve/reject/timeout. Deny-by-default. |

## Security invariants

1. **No capability runs without an `ALLOW` from the gate** (or a
   `REQUIRE_CONFIRMATION` the user then confirms).
2. **A `SENSITIVE` tool always requires a fresh human confirmation.** It can
   never be pre-trusted away. Reading secrets (notifications can carry 2FA
   codes) and irreversible actions (install, input injection) are `SENSITIVE`.
3. **Pairing tokens are never persisted in plaintext** — only their SHA-256
   hash. The raw token is shown to the user once, at pairing.
4. **Unknown token, unknown tool, or a tool not on the agent's allowlist → deny.**
   Default-deny; an agent can only do what it was explicitly granted.
5. **Everything is auditable.** Every authorize check, confirmation result, and
   execution outcome is recorded.

## Transport protocol

Loopback TCP, one JSON object per line, one JSON response per line. Reached
from a dev machine via `adb forward tcp:7231 tcp:7231`.

```
-> {"token":"<token>","tool":"packages.list","args":{}}
<- {"result":[ {"packageName":"...","versionName":"...","uid":...}, ... ]}

-> {"token":"<token>","op":"list"}
<- {"result":[ {"name":"packages.list","dangerLevel":"READ_ONLY"}, ... ]}
```

Errors come back as `{"error":"...","code":"denied|unauthenticated|confirmation_required|..."}`.

## Confirmation flow

1. Agent calls a tool the gate marks `REQUIRE_CONFIRMATION` (any `SENSITIVE`
   tool, or a non-trusted `STATE_CHANGE` tool).
2. The executor parks the call in `McpConfirmationManager` and blocks the
   transport thread; the agent's socket call waits.
3. The manager polls `listPending`, shows the request, and the user approves or
   rejects it (`resolveConfirmation`).
4. On approve the tool runs and the result returns to the agent; on reject or
   timeout the agent gets `{"error":...,"code":"rejected"}` and the tool body
   never runs.

## What comes next (not in this change)

- More capability tools, added one at a time, each with its own `DangerLevel`
  and justification. The dangerous verbs (notification read, input injection,
  install, AppOps writes) come last and individually, behind this confirmation.

## Tests

The behavioural invariants are exercised by a standalone JVM test (`GateTest`,
run during development against lightweight `android.util` shims): default-deny,
allowlist enforcement, the SENSITIVE-always-confirms rule, token-hash-only
persistence, revocation, reload, audit capture, the manager bridge's JSON
contract, executor routing (allowed read-only runs; sensitive body never runs
without confirmation; per-agent `listForToken` filtering), and a real loopback
transport round-trip (echo, bad-token denial, missing-token, `op:list`), and
the confirmation channel (approve runs the tool; reject and timeout never run
it; `listPending`/`resolveConfirmation` over the bridge), and the settings
tools against a fake `SettingsAccess` (namespace/key validation; `get` runs
freely; `put` parks and writes only after approval) — 74 checks, all passing.
`PackagesListTool` and `ProviderSettingsAccess` need real system services and
are verified by compilation only.
