# Architectural Plan: Connection Layer

This document outlines the architecture and implementation details for the Connection Layer of Maestri Remote Mobil.

## 1. Kotlin DTOs (Data Transfer Objects)

We use `kotlinx.serialization` for polymorphic JSON handling. The `MessageDto` sealed interface acts as the base for all incoming and outgoing messages.

### Message Definitions (`app/maestri/remote/data/network/model/MessageDto.kt`)

```kotlin
@Serializable
sealed interface MessageDto {
    val type: String
}

@Serializable
@SerialName("workspace_snapshot")
data class WorkspaceSnapshotDto(
    override val type: String = "workspace_snapshot",
    val workspace: WorkspaceDto
) : MessageDto

@Serializable
@SerialName("node_status")
data class NodeStatusDto(
    override val type: String = "node_status",
    val nodeId: String,
    val status: String
) : MessageDto

@Serializable
@SerialName("terminal_line")
data class TerminalLineDto(
    override val type: String = "terminal_line",
    val nodeId: String,
    val line: String,
    val seq: Int
) : MessageDto

// Support structures
@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val nodes: List<NodeDto>,
    val notes: List<NoteDto>,
    val connections: List<ConnectionDto>
)

@Serializable
data class NodeDto(
    val id: String,
    val label: String,
    val agentType: String,
    val status: String,
    val connectedTo: List<String>
)
```

## 2. ConnectionRepositoryImpl Structure

The `ConnectionRepositoryImpl` manages the lifecycle of the connection, from mDNS discovery to WebSocket communication.

### Class Structure

```kotlin
@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val nsdManager: NsdManager,
    private val wifiManager: WifiManager, // Required for MulticastLock
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : ConnectionRepository {
    
    // Internal State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _workspace = MutableStateFlow<Workspace?>(null)
    
    // WebSocket & Jobs
    private var webSocket: WebSocket? = null
    private var discoveryJob: Job? = null
    private var reconnectionJob: Job? = null
    private var heartbeatJob: Job? = null
    
    // Android 14 mDNS Lock
    private var multicastLock: WifiManager.MulticastLock? = null

    // Implementation of ConnectionRepository methods...
}
```

## 3. NsdManager Callback Logic

### Discovery Strategy
1.  **Service Type:** `_maestri._tcp`
2.  **MulticastLock:** Must be acquired in `startDiscovery()` and released in `stopDiscovery()` to ensure packet reception on Android 14+.
3.  **API 34+ Compatibility:** Use `registerServiceInfoCallback` on Android 14+, falling back to `resolveService` on older versions.

```kotlin
// In startDiscovery()
acquireMulticastLock()
nsdManager.discoverServices("_maestri._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

// In stopDiscovery()
releaseMulticastLock()
nsdManager.stopServiceDiscovery(discoveryListener)

private fun acquireMulticastLock() {
    multicastLock = wifiManager.createMulticastLock("MaestriDiscovery").apply {
        setReferenceCounted(false)
        acquire()
    }
}

private fun releaseMulticastLock() {
    multicastLock?.let { if (it.isHeld) it.release() }
}

private fun createDiscoveryListener(pin: String) = object : NsdManager.DiscoveryListener {
    override fun onServiceFound(service: NsdServiceInfo) {
        if (service.serviceType == "_maestri._tcp.") {
            resolveService(service, pin)
        }
    }
    // ...
}

private fun resolveService(service: NsdServiceInfo, pin: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        nsdManager.registerServiceInfoCallback(service, Runnable::run, object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                nsdManager.unregisterServiceInfoCallback(this)
                val host = serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                connectToHost(host ?: return, serviceInfo.port, pin)
            }
            // ... handle errors/lost
        })
    } else {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                connectToHost(serviceInfo.host.hostAddress, serviceInfo.port, pin)
            }
            // ...
        })
    }
}
```

## 4. WebSocket Message Handling & Heartbeat

### Heartbeat Strategy
To maintain the connection through aggressive mobile network timeouts, a 30-second application-level ping/pong is required.

1.  **Start:** When `onOpen` is called in the WebSocket listener.
2.  **Logic:** Every 30s, send a `{"type": "ping"}` message.
3.  **Stop:** When `onClosed` or `onFailure` is called.

```kotlin
private fun startHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
        while (isActive) {
            delay(30_000)
            webSocket?.send("""{"type": "ping"}""")
        }
    }
}
```

### WebSocket Listener Logic

The WebSocket listener must decode the polymorphic `MessageDto` and update the domain state.

```kotlin
override fun onMessage(webSocket: WebSocket, text: String) {
    val dto = try {
        json.decodeFromString<MessageDto>(text)
    } catch (e: Exception) {
        return
    }

    when (dto) {
        is WorkspaceSnapshotDto -> {
            _workspace.value = dto.workspace.toDomain()
            _connectionState.value = ConnectionState.Connected
        }
        is NodeStatusDto -> {
            // Update specific node in the current workspace state
        }
        is TerminalLineDto -> {
            // Emit to a separate Flow for terminal lines
        }
    }
}
```

## 5. Dependency Injection Tasks

Since DI modules are missing, we need to create them:

1.  **NetworkModule:**
    - Provide `Json` with `ignoreUnknownKeys = true` and `classDiscriminator = "type"`.
    - Provide `OkHttpClient`.
    - Provide `NsdManager` (via `Context.getSystemService`).
2.  **RepositoryModule:**
    - Bind `ConnectionRepositoryImpl` to `ConnectionRepository`.

## 6. Tasks for Codex (Implementer)

1.  **DI Setup:** Create `di` package and implement `NetworkModule` and `DataModule`.
2.  **Domain Mapping:** Add `toDomain()` extension functions in `data/mapper/Mappers.kt` for all DTOs.
3.  **Discovery Implementation:** Complete `startDiscovery` logic in `ConnectionRepositoryImpl` with proper timeout (10s).
4.  **WebSocket Listener:** Implement the full `WebSocketListener` with heartbeat/ping handling.
5.  **State Management:** Ensure `ConnectionState` transitions are handled correctly (e.g., reset workspace on disconnect).
6.  **Error Handling:** Add a `ConnectionError` sealed class or similar to handle PIN mismatches or network failures.

## 7. Terminal Management

The `TerminalRepository` handles the volatile state of terminal buffers and bridges user input to the connection layer. It is kept separate from `ConnectionRepository` to maintain Clean Architecture boundaries and avoid bloating the connection lifecycle logic.

### TerminalRepository Interface (`app/maestri/remote/domain/repository/TerminalRepository.kt`)

```kotlin
interface TerminalRepository {
    /**
     * Streams the terminal lines for a specific node.
     * Initially emits the current buffer, then subsequent updates.
     */
    fun getLines(nodeId: String): Flow<List<TerminalLine>>

    /**
     * Buffers a new line and strips ANSI codes.
     */
    fun addLine(line: TerminalLine)

    /**
     * Sends input to the remote terminal via the connection layer.
     */
    suspend fun sendInput(nodeId: String, text: String)

    /**
     * Clears all buffers (e.g., on disconnect).
     */
    fun clearAll()
}
```

### Buffering & Streaming Strategy

1.  **Storage:** `ConcurrentHashMap<String, ArrayDeque<TerminalLine>>` ensures thread-safe access to multiple node buffers.
2.  **Limit:** Enforce a 2000-line limit per node.
3.  **ANSI Stripping:** Simple regex-based stripping during the `addLine` phase.
4.  **Reactive Updates:** A single `MutableStateFlow<Map<String, List<TerminalLine>>>` or per-node `MutableSharedFlow` to notify subscribers.

### Connection Integration

`ConnectionRepository` should be updated to include a `sendMessage(MessageDto)` method. When `ConnectionRepository` receives a `terminal_line` message, it delegates to `TerminalRepository.addLine()`.
