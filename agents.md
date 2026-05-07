# Maestri Remote — agents.md

## Agent role in this project

When you work on Maestri Remote, you are building a **mobile observer and controller** for an AI agent orchestration system. You will write code that streams live terminal output, models graph relationships between agents, and adapts a spatial desktop paradigm to a touch interface. Keep `claude.md` open as your primary reference.

Your job is to:
1. Understand the full context of a task before writing code.
2. Work in small, reviewable increments.
3. Preserve the design principles in `claude.md` — especially "read-first, act-second" and "no cloud, no telemetry".
4. Always leave the codebase in a buildable, testable state.

---

## How to approach a new task

```
1. Read the task description completely.
2. Identify which layer(s) are touched: data / domain / presentation.
3. Check if a domain model already exists or needs to be created.
4. Write the use case first (pure domain logic, no Android dependencies).
5. Write unit tests for the use case.
6. Implement the repository / data layer.
7. Wire into ViewModel.
8. Build the Compose UI last.
9. Run `./gradlew testDebugUnitTest` before marking done.
```

Do not skip steps 4–5 to get to the UI faster. The domain layer is the stable core; UI is the volatile shell.

---

## WebSocket message schema

All messages between Android and the Mac bridge are newline-delimited JSON. Every message has a `type` field.

### Inbound (Mac → Android)

```jsonc
// Full workspace snapshot (sent on connect)
{ "type": "workspace_snapshot", "workspace": { /* Workspace object */ } }

// Agent status changed
{ "type": "node_status", "nodeId": "abc123", "status": "RUNNING" }

// Terminal output line
{ "type": "terminal_line", "nodeId": "abc123", "line": "Building project...", "seq": 142 }

// Ombro summary ready
{ "type": "ombro_summary", "generatedAt": "2025-05-04T22:00:00Z", "text": "...", "nextSteps": ["..."] }

// Note updated
{ "type": "note_updated", "note": { "id": "n1", "title": "Plan", "content": "# Plan\n..." } }

// Connection closed by Mac
{ "type": "session_end", "reason": "user_quit" }
```

### Outbound (Android → Mac)

```jsonc
// Send input to an agent's terminal
{ "type": "terminal_input", "nodeId": "abc123", "text": "ls -la\n" }

// Request a fresh Ombro summary
{ "type": "ombro_request" }

// Update a note (optimistic: send immediately, reconcile on ack)
{ "type": "note_update", "noteId": "n1", "content": "# Updated plan\n..." }

// Ping (keepalive)
{ "type": "ping" }
```

### Handling unknown message types

Always use a `when` with an `else` branch. Unknown types must be **silently ignored**, not thrown as exceptions. The Mac app may add new message types; Android must be forward-compatible.

```kotlin
when (message.type) {
    "workspace_snapshot" -> handleSnapshot(message)
    "node_status"        -> handleNodeStatus(message)
    "terminal_line"      -> handleTerminalLine(message)
    "ombro_summary"      -> handleOmbro(message)
    "note_updated"       -> handleNoteUpdate(message)
    "session_end"        -> handleSessionEnd(message)
    else                 -> { /* ignore — forward compat */ }
}
```

---

## Connection state machine

```
DISCONNECTED
    │  user taps Connect / app comes to foreground
    ▼
DISCOVERING          ← mDNS scan for _maestri._tcp.local
    │  host found
    ▼
CONNECTING           ← WebSocket handshake + PIN auth
    │  auth OK + workspace_snapshot received
    ▼
CONNECTED            ← streaming, fully live
    │  network loss / Mac app quit
    ▼
RECONNECTING         ← exponential backoff (1s, 2s, 4s… max 30s)
    │  reconnected           │  user taps Disconnect
    ▼                        ▼
CONNECTED            DISCONNECTED
```

Model this as a `sealed interface ConnectionState` and expose it from `ConnectionRepository`. The UI should never manage reconnect timers — that belongs in the data layer.

---

## Terminal rendering

Terminal output is the heart of the app. Get it right.

- Buffer lines per `nodeId` in a `Map<String, ArrayDeque<TerminalLine>>` with a **max capacity of 2000 lines** per node. Drop from the front when exceeded.
- Emit updates via `Flow<TerminalUpdate>`. Do not collect in the data layer; let the ViewModel collect.
- Render with a `LazyColumn` in reverse (newest at bottom). Autoscroll to bottom unless the user has manually scrolled up.
- ANSI escape codes: strip for display unless you implement a proper ANSI parser. Partial ANSI rendering is worse than none.
- Touch target for "copy line" must be reachable with one thumb (right-aligned, min 48dp).

---

## Ombro integration

Ombro summaries are the **highest-value content** for a mobile user. Treat them accordingly.

- Show the latest summary as a **banner at the top of the canvas screen**, collapsible, always visible if present.
- Surface `nextSteps` as tappable chips. Tapping a next step should pre-fill the terminal input for the relevant agent (if one can be inferred) — but never send automatically.
- Store the last summary in DataStore so it survives app restarts and offline sessions.
- If no summary exists yet, show a skeleton placeholder, not an empty state.

---

## Offline / stale state

When the connection is lost:

1. Keep the last `Workspace` snapshot in memory and optionally in Room.
2. Show a persistent `TopAppBar` banner: *"Disconnected — showing last known state"* with a reconnect button.
3. Disable all write actions (terminal input, note editing) while disconnected.
4. Resume streaming and reconcile state when reconnected: request a fresh `workspace_snapshot`.

Do not show a full-screen error page. The user should still be able to read notes and past terminal output.

---

## Feature checklist (build order)

Build features in this order. Each must be independently testable before moving on.

- [x] **1. Connection layer** — WebSocket client, mDNS discovery, PIN auth, reconnect logic
- [x] **2. Workspace model** — parse snapshot, expose via Flow
- [x] **3. Canvas screen** — agent card list, status indicators, connection badges
- [x] **4. Terminal screen** — stream output for a single node, input field
- [x] **5. Ombro banner** — display summary + next step chips
- [x] **6. Notes screen** — list + Markdown viewer (read-only first, then edit)
- [x] **7. Offline mode** — stale banner, read-only enforcement, Room cache
- [x] **8. Settings / pairing** — PIN entry, saved hosts, disconnect

---

## Testing strategy

| Layer | Tool | What to test |
|---|---|---|
| Use cases | JUnit 5 + MockK | Business logic, error paths, edge cases |
| ViewModel | Turbine + MockK | State transitions, event emissions |
| Repository | JUnit 5 + OkHttp MockWebServer | WebSocket parsing, reconnect logic |
| UI | Compose UI Test | Happy path for each screen, accessibility |

Every use case must have tests before the ViewModel is written. This is not optional.

Mock the WebSocket server with `MockWebServer` — never test against a real Maestri instance.

---

## Things to never do

- **Never** send terminal input without explicit user confirmation (button press or keyboard submit).
- **Never** kill or disconnect an agent without a confirmation dialog.
- **Never** store the connection PIN in plaintext SharedPreferences. Use EncryptedSharedPreferences or DataStore with encryption.
- **Never** add a cloud SDK, analytics library, or any dependency that makes network calls outside the local LAN.
- **Never** hardcode IP addresses or ports. Everything is discoverable via mDNS with user-configurable fallback.
- **Never** crash on malformed JSON from the server. Log and skip.
- **Never** block the main thread with I/O — all network and disk operations must be on `Dispatchers.IO`.

---

## Dependency rules (Clean Architecture)

```
presentation  →  domain  ←  data
                    ↑
               (interfaces only)
```

- `presentation` imports `domain`, never `data`.
- `data` implements `domain` interfaces.
- `domain` has zero Android dependencies (no Context, no Room, no OkHttp).
- Use `@HiltViewModel` and inject use cases, never repositories, into ViewModels.

---

## Glossary

| Term | Meaning |
|---|---|
| **Node** | A single terminal/agent tile on the Maestri canvas |
| **Connection** | A directed link between two nodes enabling agent-to-agent messaging |
| **Workspace** | The full canvas state — all nodes, notes, and connections |
| **Ombro** | Maestri's on-device AI companion; summarises agent activity |
| **PTY** | Pseudo-terminal — the mechanism Maestri uses for agent-to-agent I/O on Mac |
| **Bridge** | The Mac-side server that exposes Maestri state over WebSocket to Android |
| **Conductor** | The user's role — orchestrating agents, not doing the work themselves |
