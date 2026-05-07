# Maestri Remote — claude.md

## What is this project?

**Maestri Remote** is a native Android companion app for [Maestri](https://themaestri.app), a macOS app that provides an infinite spatial canvas for orchestrating AI coding agents. Maestri lets developers position terminal nodes freely on a canvas, connect agents so they can communicate directly via PTY, and annotate work with sketches and Markdown notes — all without cloud dependency, accounts, or telemetry.

**Maestri Remote** extends this workflow to Android. The user's Mac runs Maestri as the source of truth; the Android app connects over the local network and lets the user monitor agents, read and send output, review notes, and act on Ombro's summaries — all from their phone.

The core challenge is **not** to port the infinite canvas to a small screen. It is to identify what a developer actually needs while away from their desk and serve exactly that, without losing the mental model that makes Maestri powerful.

---

## The Maestri mental model (never lose this)

| Concept | Mac | Android |
|---|---|---|
| **Canvas** | Infinite spatial workspace | Scrollable list / focused single-agent view |
| **Node** | Terminal positioned as a draggable tile | Card in a list or bottom-sheet detail view |
| **Connection** | Drawn line between two agent nodes | Shown as a badge ("→ Codex") on a card |
| **Ombro** | Floating window, watches everything | Persistent notification + summary banner |
| **Note** | Markdown file pinned to canvas | Read/edit in a dedicated Notes screen |
| **Workspace** | `.json` file on disk | Remote workspace loaded on connect |

The user is always the **conductor**, never the bottleneck. Every UI decision should reinforce that framing.

---

## Connection architecture

Maestri Remote connects to the Mac over **local network (LAN/Wi-Fi)** via a lightweight bridge server that runs alongside Maestri on the Mac. The bridge is not part of this Android project — it is a separate component — but Android must assume the following contract:

- **Transport**: WebSocket (`ws://`) over LAN. No cloud relay, consistent with Maestri's zero-telemetry philosophy.
- **Discovery**: mDNS/Bonjour (`_maestri._tcp.local`) for zero-config pairing.
- **Auth**: Shared secret (6-digit PIN shown in Maestri on Mac, entered once on Android).
- **Messages**: JSON frames. See `agents.md` for the message schema conventions.
- **Streaming**: Terminal output is streamed line-by-line. Android must buffer and render incrementally.
- **State sync**: Full workspace snapshot on connect; delta events after that.

Assume connections are **unreliable** (screen lock, Wi-Fi roaming). The app must reconnect gracefully without requiring user action.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin (100%) |
| UI | Jetpack Compose |
| Architecture | MVVM + Clean Architecture (domain / data / presentation layers) |
| Async | Kotlin Coroutines + Flow |
| DI | Hilt |
| Networking | OkHttp (WebSocket) + Kotlin serialization |
| Local storage | DataStore (preferences), Room (optional, for offline note cache) |
| Navigation | Compose Navigation |
| Testing | JUnit 5, MockK, Turbine (Flow testing) |

Do **not** introduce Retrofit — all communication is WebSocket, not REST. Do not use RxJava.

---

## Project structure

```
app/
  src/main/
    java/app/maestri/remote/
      core/              # App-level: DI modules, navigation graph, theme
      data/
        network/         # WebSocket client, message parsing, mDNS discovery
        repository/      # Implementations of domain repository interfaces
        model/           # Raw data transfer objects (DTOs), JSON serialization
      domain/
        model/           # Pure Kotlin domain models (Node, Workspace, AgentStatus…)
        repository/      # Interfaces (contracts only, no implementation)
        usecase/         # One class per use case, single `invoke` operator
      presentation/
        canvas/          # Agent list / canvas overview screen
        terminal/        # Full-screen terminal output for a single agent
        notes/           # Workspace notes list + markdown viewer/editor
        ombro/           # Ombro summary sheet
        settings/        # Connection settings, PIN pairing
        common/          # Shared composables, theme tokens
      MainViewModel.kt
      MainActivity.kt
```

---

## Key domain models

```kotlin
data class Workspace(
    val id: String,
    val name: String,
    val nodes: List<Node>,
    val notes: List<Note>,
    val connections: List<Connection>
)

data class Node(
    val id: String,
    val label: String,
    val agentType: AgentType,   // CLAUDE_CODE, CODEX, GEMINI, SHELL, UNKNOWN
    val status: AgentStatus,    // IDLE, RUNNING, WAITING, DONE, ERROR
    val connectedTo: List<String>  // IDs of peer nodes
)

data class TerminalOutput(
    val nodeId: String,
    val lines: List<TerminalLine>,
    val cursor: Int             // index of current line
)

data class OmbroSummary(
    val generatedAt: Instant,
    val text: String,
    val suggestedNextSteps: List<String>
)
```

---

## Design principles

1. **Read-first, act-second.** The primary use case is monitoring. Destructive or hard-to-reverse actions (killing an agent, sending a prompt) require explicit confirmation.

2. **Spatial context on mobile.** Show which agents are connected to which. Don't flatten everything into a homogeneous list. Grouping and relationship indicators matter.

3. **Stream terminal output, don't buffer silently.** The user should feel the agent is alive. New output should be visually apparent even when the terminal is not in focus (badge counts, status pulse).

4. **Ombro is the north star for glanceability.** If the user opens the app for 10 seconds, the Ombro summary should tell them exactly what happened and what to do next.

5. **Offline-tolerant.** Cache the last known workspace state so the app is useful even when the Mac is unreachable (read-only mode with stale indicator).

6. **No account, no cloud.** Do not add any analytics SDK, crash reporting that phones home, or authentication that requires a server. If telemetry is ever added, it must be opt-in and local-only, consistent with Maestri's philosophy.

---

## Coding conventions

- Use `sealed interface` for UI state: `Loading`, `Success(data: T)`, `Error(message: String)`.
- `ViewModel` exposes `StateFlow<UiState>` and `SharedFlow<UiEvent>` (one-shot events).
- Use cases are `suspend fun` returning `Result<T>`. Never let exceptions propagate to the ViewModel raw.
- Compose: prefer stateless composables that receive state and callbacks. Keep `@Composable` functions free of business logic.
- Strings in `strings.xml`. Do not hardcode UI strings in Kotlin.
- Write tests for use cases and ViewModels first. UI tests are secondary.
- Commit messages: `type(scope): description` — e.g. `feat(terminal): stream incremental output via Flow`.

---

## What Maestri Remote is NOT

- Not a replacement for the Mac app.
- Not a remote desktop / screen mirror.
- Not a place to configure agents or create new workspaces from scratch.
- Not a general-purpose SSH terminal client.

Stay focused: **observe, understand, and act on** what's happening in Maestri on the Mac.
