package app.maestri.remote.data.repository

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import app.maestri.remote.core.notification.NotificationHelper
import app.maestri.remote.data.mapper.toDomain
import app.maestri.remote.data.mapper.toEntity
import app.maestri.remote.data.model.*
import app.maestri.remote.domain.model.AgentStatus
import app.maestri.remote.domain.model.Workspace
import app.maestri.remote.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.InterruptedIOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val nsdManager: NsdManager,
    private val wifiManager: WifiManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val terminalRepository: Provider<TerminalRepository>,
    private val ombroRepository: Provider<OmbroRepository>,
    private val noteRepository: Provider<NoteRepository>,
    private val notificationHelper: NotificationHelper,
    private val workspaceDao: app.maestri.remote.data.database.WorkspaceDao,
    private val noteDao: app.maestri.remote.data.database.NoteDao,
    private val settingsRepository: app.maestri.remote.domain.repository.SettingsRepository
) : ConnectionRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(Instant.now()))
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _workspace = MutableStateFlow<Workspace?>(null)
    override val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    private var lastSyncTime: Instant = Instant.now()
    private var lastKnownHost: String? = null
    private var lastKnownPort: Int = 8765
    private var webSocket: WebSocket? = null
    private var discoveryJob: Job? = null
    private var reconnectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private companion object {
        const val SERVICE_TYPE = "_maestri._tcp."
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val HEARTBEAT_INTERVAL_MS = DISCOVERY_TIMEOUT_MS * 3 // 30s
    }

    init {
        scope.launch {
            workspaceDao.getWorkspace().collectLatest { workspaceEntity ->
                if (workspaceEntity != null) {
                    workspaceDao.getNodesForWorkspace(workspaceEntity.id).collectLatest { nodes ->
                        _workspace.value = workspaceEntity.toDomain(nodes)
                    }
                }
            }
        }
    }

    override suspend fun connect(pin: String, host: String?) {
        if (_connectionState.value is ConnectionState.Connected) return
        if (host != null) {
            connectToHost(host, 8765, pin)
        } else {
            startDiscovery(pin)
        }
    }

    override fun disconnect() {
        stopDiscovery()
        stopHeartbeat()
        reconnectionJob?.cancel()
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        lastKnownHost = null
        _connectionState.value = ConnectionState.Disconnected(Instant.now())
        terminalRepository.get().clearAll()
    }

    override suspend fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    override suspend fun sendNodeAction(nodeId: String, action: String) {
        val dto = NodeActionDto(nodeId = nodeId, action = action)
        val jsonString = json.encodeToString<MessageDto>(dto)
        sendMessage(jsonString)
    }

    override suspend fun killAllNodes() {
        sendNodeAction("all", "stop")
    }

    private fun startDiscovery(pin: String) {
        _connectionState.value = ConnectionState.Discovering
        acquireMulticastLock()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
                _connectionState.value = ConnectionState.Disconnected(Instant.now())
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onDiscoveryStarted(serviceType: String?) {}

            override fun onDiscoveryStopped(serviceType: String?) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("maestri")) {
                    resolveService(serviceInfo, pin)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (_connectionState.value == ConnectionState.Discovering) {
                stopDiscovery()
                _connectionState.value = ConnectionState.Disconnected(Instant.now())
            }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        discoveryListener = null
        releaseMulticastLock()
    }

    private fun resolveService(service: NsdServiceInfo, pin: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsdManager.registerServiceInfoCallback(service, Runnable::run, object : NsdManager.ServiceInfoCallback {
                private fun cleanup() {
                    try { nsdManager.unregisterServiceInfoCallback(this) } catch (e: Exception) {}
                }

                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    cleanup()
                    val host = serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                    if (host != null) {
                        stopDiscovery()
                        connectToHost(host, serviceInfo.port, pin)
                    }
                }

                override fun onServiceLost() { cleanup() }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) { cleanup() }
                override fun onServiceInfoCallbackUnregistered() {}
            })
        } else {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        stopDiscovery()
                        connectToHost(host, serviceInfo.port, pin)
                    }
                }
            })
        }
    }

    private fun connectToHost(host: String, port: Int, pin: String) {
        _connectionState.value = ConnectionState.Connecting
        
        lastKnownHost = host
        lastKnownPort = port
        
        val isIpAddress = host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || host.contains(":")
        val isLocal = host.endsWith(".local") || host == "localhost"
        
        val url = if (isIpAddress || isLocal) "ws://$host:$port" else "wss://$host"
        val request = Request.Builder()
            .url(url)
            .addHeader("X-Maestri-PIN", pin)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener(pin))
    }

    private fun createWebSocketListener(pin: String) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.Connected
            
            // Send auth message for Swift bridge compatibility
            val authMsg = """{"type": "auth", "pin": "$pin"}"""
            webSocket.send(authMsg)
            
            lastSyncTime = Instant.now()
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            lastSyncTime = Instant.now()
            val dto = try {
                json.decodeFromString<MessageDto>(text)
            } catch (e: Exception) {
                null
            } ?: return

            handleMessage(dto)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = ConnectionState.Stale(lastSyncTime)
            if (t is InterruptedIOException) {
                _connectionState.value = ConnectionState.Disconnected(Instant.now())
            } else {
                startReconnection(pin)
            }
            stopHeartbeat()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.Disconnected(Instant.now())
            stopHeartbeat()
        }
    }

    private fun handleMessage(dto: MessageDto) {
        when (dto) {
            is WorkspaceSnapshotDto -> {
                val domainWorkspace = dto.workspace.toDomain()
                _connectionState.value = ConnectionState.Connected
                scope.launch {
                    workspaceDao.saveWorkspace(
                        domainWorkspace.toEntity(),
                        domainWorkspace.nodes.map { it.toEntity(domainWorkspace.id) }
                    )
                    noteRepository.get().syncNotes(domainWorkspace.notes)
                }
            }
            is NodeStatusDto -> {
                val currentWorkspace = _workspace.value
                val node = currentWorkspace?.nodes?.find { it.id == dto.nodeId }
                val newStatus = try {
                    AgentStatus.valueOf(dto.status.uppercase())
                } catch (e: Exception) {
                    null
                }

                if (newStatus == AgentStatus.NEEDS_INPUT && node?.status != AgentStatus.NEEDS_INPUT) {
                    notificationHelper.showInputRequiredNotification(dto.nodeId, node?.label ?: dto.nodeId)
                    
                    scope.launch {
                        if (settingsRepository.isAutoApproveEnabled.first()) {
                            delay(1000) // Small delay for UX
                            terminalRepository.get().sendInput(dto.nodeId, "y\n")
                        }
                    }
                }

                scope.launch {
                    workspaceDao.updateNodeStatus(dto.nodeId, dto.status)
                }
            }
            is NoteUpdatedDto -> {
                val updatedNote = dto.note.toDomain()
                _workspace.value = _workspace.value?.let { ws ->
                    ws.copy(notes = ws.notes.map { note ->
                        if (note.id == updatedNote.id) updatedNote else note
                    })
                }
                scope.launch {
                    val currentNote = noteDao.getNoteById(updatedNote.id)
                    if (currentNote?.content != updatedNote.content) {
                        noteRepository.get().syncNotes(listOf(updatedNote))
                    }
                }
            }
            is TerminalLineDto -> {
                terminalRepository.get().addLine(dto.toDomain())
            }
            is OmbroSummaryDto -> {
                scope.launch {
                    ombroRepository.get().saveSummary(dto.toDomain())
                }
            }
            else -> { /* Ignore other messages for now */ }
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                webSocket?.send("""{"type": "ping"}""")
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun startReconnection(pin: String) {
        reconnectionJob?.cancel()
        reconnectionJob = scope.launch {
            var attempt = 1
            while (isActive) {
                _connectionState.value = ConnectionState.Reconnecting(attempt, lastSyncTime)
                val delayMs = (Math.pow(2.0, attempt.toDouble()) * 1000).toLong().coerceAtMost(30000)
                delay(delayMs)
                
                val host = lastKnownHost
                if (host != null && attempt == 1) {
                    connectToHost(host, lastKnownPort, pin)
                } else if (host != null) {
                    val timeoutJob = scope.launch {
                        delay(5000)
                        if (_connectionState.value is ConnectionState.Reconnecting) {
                            startDiscovery(pin)
                        }
                    }
                    connectToHost(host, lastKnownPort, pin)
                    timeoutJob.cancel()
                } else {
                    startDiscovery(pin)
                }
                attempt++
            }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("MaestriDiscovery").apply {
                setReferenceCounted(false)
            }
        }
        multicastLock?.acquire()
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
}
