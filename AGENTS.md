# AGENTS.md

## mem0 memory usage (mandatory)

The `mem0` MCP server is available in this project. Follow these rules:

### At session start
- Call `search_memory` with a query covering the current task (e.g. "payment project work state", "reserve cap", "what was the last task") to recall prior context.
- If no relevant memories exist, proceed normally and save state after.

### Throughout the session
- **Save key decisions, progress, and environment quirks** to mem0 as you discover them. Prefer short, self-contained facts with enough context to be reused in a fresh session:
  - Architecture decisions (e.g. "₹500 per-user cap on ACTIVE reservations enforced in `ReservationService.reserveFunds` under PESSIMISTIC_WRITE account lock")
  - Commands that work / are required (e.g. "`.\mvnw.cmd -q -pl ledger-service compile`", "Supabase connect needs `connect_timeout=90`", "creds in `set-env.ps1`")
  - Work-state checkpoints (e.g. "DB cleaned: 3 ACTIVE reservations -> 1; next: rebuild ledger-service and verify with concurrent reserve calls")
  - Gotchas discovered (e.g. "`FOR UPDATE cannot be applied to the nullable side of an outer join`", "opencode config is not hot-reloaded; user must restart")
- Use `add_memory` for new facts and `add_memory` with metadata/aliases for distinct topics. Avoid duplicating facts already stored; `search_memory` first if unsure.

### At task completion (or when pausing)
- Save a short "work state" summary memory so the next session can resume without the user pasting context.

### Rules
- mem0 is a complement to reading code, never a substitute. Verify facts against the repo when behavior matters.
- Memories can go stale; note the date when saving time-sensitive facts.
- Never store secrets (API keys, passwords, JWT secrets) in mem0.
