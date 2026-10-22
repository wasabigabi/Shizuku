# MCP bridge — authorization & audit layer

This package is the security foundation for letting a local AI agent (over the
Model Context Protocol) call privileged device operations through the Shizuku
server. It is built **security-first, on purpose**: this layer ships before any
capability tool exists, so the gate and audit trail are in place before
anything privileged can be wired in.

At this stage **no capability is exposed**. There is no transport, no socket,
and no privileged action. What lives here is only the decision machinery that
every future tool call must pass through.

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

## What comes next (not in this change)

- Manager-app UI: pairing, per-agent allowlist, confirmation prompts, audit view.
- Transport: localhost / adb-forward only, gated by a paired token.
- Capability tools, added one at a time, each with its own `DangerLevel` and
  justification. The dangerous verbs (notification read, input injection,
  install, AppOps writes) come last and individually.

## Tests

The gate's behavioural invariants are exercised by a standalone JVM test
(`GateTest`, run during development against lightweight `android.util` shims):
default-deny, allowlist enforcement, the SENSITIVE-always-confirms rule,
token-hash-only persistence, revocation, reload, and audit capture — 19 checks,
all passing.
