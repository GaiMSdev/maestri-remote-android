package app.maestri.remote.presentation.canvas

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import app.maestri.remote.domain.model.AgentStatus
import app.maestri.remote.domain.model.AgentType
import app.maestri.remote.domain.model.Node
import app.maestri.remote.domain.repository.ConnectionState
import app.maestri.remote.R
import app.maestri.remote.domain.usecase.VoiceCommandResult
import app.maestri.remote.presentation.ombro.OmbroBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    onNodeClick: (String, String?) -> Unit,
    onNextStepClick: (String, String) -> Unit,
    onNotesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val canWrite by viewModel.canWrite.collectAsState()
    val voiceResult by viewModel.voiceResult.collectAsState()
    val listState = rememberLazyListState()
    
    val showFab by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }

    var selectedNode by remember { mutableStateOf<Node?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showKillAllDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun handleConnect(pin: String, host: String? = null) {
        if (isBiometricEnabled) {
            viewModel.biometricHelper.authenticate(
                activity = context as FragmentActivity,
                onSuccess = { viewModel.connect(pin, host) },
                onError = { error ->
                    scope.launch {
                        snackbarHostState.showSnackbar(error)
                    }
                }
            )
        } else {
            viewModel.connect(pin, host)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.canvas_title)) },
                actions = {
                    IconButton(onClick = onNotesClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.notes))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    if (uiState is CanvasUiState.Success) {
                        val state = (uiState as CanvasUiState.Success)
                        val connectionState = state.connectionState
                        val hasWorkspace = state.workspace != null

                        if (connectionState is ConnectionState.Connected) {
                            IconButton(onClick = { viewModel.disconnect() }) {
                                Icon(Icons.Default.LinkOff, contentDescription = stringResource(R.string.disconnect))
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.kill_all_agents), color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red) },
                                        enabled = canWrite,
                                        onClick = {
                                            showOverflowMenu = false
                                            showKillAllDialog = true
                                        }
                                    )
                                }
                            }
                        } else if (connectionState is ConnectionState.Disconnected && hasWorkspace) {
                            IconButton(onClick = { showConnectDialog = true }) {
                                Icon(Icons.Default.Link, contentDescription = stringResource(R.string.connect))
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (canWrite && uiState is CanvasUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.executeVoiceCommand("Restart failing agents") },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    text = { Text(stringResource(R.string.fix_issues)) },
                    expanded = showFab,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            AnimatedContent(
                targetState = uiState,
                label = "UiStateTransition"
            ) { state ->
                when (state) {
                    is CanvasUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is CanvasUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is CanvasUiState.Success -> {
                        CanvasContent(
                            state = state,
                            onConnect = { pin, host -> handleConnect(pin, host) },
                            onNodeClick = { node ->
                                selectedNode = node
                                showSheet = true
                            },
                            onNextStepClick = onNextStepClick,
                            onRefreshOmbro = viewModel::requestOmbroSummary,
                            onSendInput = viewModel::sendInput,
                            canWrite = canWrite,
                            listState = listState
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = voiceResult != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = voiceResult?.format() ?: "",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (showSheet && selectedNode != null) {
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false },
                    sheetState = sheetState
                ) {
                    val isConnected = (uiState as? CanvasUiState.Success)?.connectionState is ConnectionState.Connected
                    NodeActionMenu(
                        node = selectedNode!!,
                        isConnected = isConnected && canWrite,
                        onAction = { action ->
                            showSheet = false
                            when (action) {
                                "terminal" -> onNodeClick(selectedNode!!.id, null)
                                "stop" -> viewModel.stopNode(selectedNode!!.id)
                                "restart" -> viewModel.restartNode(selectedNode!!.id)
                            }
                        }
                    )
                }
            }

            if (showConnectDialog) {
                ConnectDialog(
                    onDismiss = { showConnectDialog = false },
                    onConnect = { pin, host -> handleConnect(pin, host) }
                )
            }

            if (showKillAllDialog) {
                AlertDialog(
                    onDismissRequest = { showKillAllDialog = false },
                    title = { Text(stringResource(R.string.kill_all_confirm_title)) },
                    text = { Text(stringResource(R.string.kill_all_confirm_text)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.killAllNodes()
                                showKillAllDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(stringResource(R.string.kill_all_agents))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showKillAllDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String?) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_to_mac)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_pin_description))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text(stringResource(R.string.pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP Address (Optional)") },
                    placeholder = { Text("e.g. 192.168.1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Only if discovery fails") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(pin, host.takeIf { it.isNotBlank() })
                    onDismiss()
                },
                enabled = pin.length == 6,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.connect))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun NodeActionMenu(
    node: Node,
    isConnected: Boolean,
    onAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.open_terminal)) },
            leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            modifier = Modifier
                .clickable { onAction("terminal") }
                .minimumInteractiveComponentSize()
        )
        ListItem(
            headlineContent = { 
                Text(
                    text = stringResource(R.string.stop_agent),
                    color = if (isConnected) Color.Unspecified else Color.Gray
                ) 
            },
            leadingContent = { 
                Icon(
                    Icons.Default.Warning, 
                    contentDescription = null, 
                    tint = if (isConnected) Color.Red else Color.Gray
                ) 
            },
            modifier = (if (isConnected) Modifier.clickable { onAction("stop") } else Modifier)
                .minimumInteractiveComponentSize()
        )
        ListItem(
            headlineContent = { 
                Text(
                    text = stringResource(R.string.restart_agent),
                    color = if (isConnected) Color.Unspecified else Color.Gray
                ) 
            },
            leadingContent = { 
                Icon(
                    Icons.Default.Refresh, 
                    contentDescription = null,
                    tint = if (isConnected) Color.Unspecified else Color.Gray
                ) 
            },
            modifier = (if (isConnected) Modifier.clickable { onAction("restart") } else Modifier)
                .minimumInteractiveComponentSize()
        )
    }
}

@Composable
fun CanvasContent(
    state: CanvasUiState.Success,
    onConnect: (String, String?) -> Unit,
    onNodeClick: (Node) -> Unit,
    onNextStepClick: (String, String) -> Unit,
    onRefreshOmbro: () -> Unit,
    onSendInput: (String, String) -> Unit,
    canWrite: Boolean,
    listState: LazyListState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OmbroBanner(
            summary = state.ombroSummary,
            onRefresh = onRefreshOmbro,
            isRefreshing = state.isOmbroRefreshing,
            enabled = canWrite,
            onSuggestionClick = { command ->
                val nodes = state.workspace?.nodes ?: emptyList()
                val matchedNode = nodes.find { node ->
                    command.contains(node.label, ignoreCase = true)
                }
                val nodeId = matchedNode?.id ?: nodes.firstOrNull()?.id ?: "default"
                onNextStepClick(nodeId, command)
            }
        )

        if (state.workspace != null) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                state.summary?.let { summary ->
                    item {
                        WorkspaceSummaryCard(
                            summary = summary,
                            isDisconnected = state.connectionState is ConnectionState.Disconnected,
                            onReconnect = { onConnect("", null) }
                        )
                    }
                }

                items(state.workspace.nodes, key = { it.id }, contentType = { "NodeCard" }) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node) },
                        onSendInput = onSendInput,
                        canWrite = canWrite
                    )
                }
            }
        } else if (state.connectionState is ConnectionState.Disconnected) {
            ConnectPrompt(onConnect, workspaceName = state.workspace?.name)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.syncing_workspace))
                }
            }
        }
    }
}

@Composable
fun ConnectPrompt(onConnect: (String, String?) -> Unit, workspaceName: String? = null) {
    var pin by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Computer,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (workspaceName != null) {
            Text(
                text = workspaceName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(stringResource(R.string.connect_to_maestri), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.connect_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text(stringResource(R.string.pin_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("IP Address (Optional)") },
            placeholder = { Text("e.g. 192.168.1.50") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Manual override if Wi-Fi discovery fails") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onConnect(pin, host.takeIf { it.isNotBlank() }) },
            enabled = pin.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
        ) {
            Text(stringResource(R.string.connect))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSummaryCard(
    summary: CanvasSummary,
    isDisconnected: Boolean,
    onReconnect: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.workspace_summary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isDisconnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(stringResource(R.string.read_only), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = stringResource(R.string.active_agents, summary.activeAgentsCount),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.total_cost, summary.totalSessionCost, summary.currency),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (isDisconnected) {
                Button(
                    onClick = onReconnect,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.connect))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeCard(
    node: Node,
    onClick: () -> Unit,
    onSendInput: (String, String) -> Unit,
    canWrite: Boolean
) {
    val needsInput = node.status == AgentStatus.NEEDS_INPUT
    val infiniteTransition = rememberInfiniteTransition(label = "needsInput")
    
    val borderColor by if (needsInput) {
        infiniteTransition.animateColor(
            initialValue = MaterialTheme.colorScheme.primary,
            targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderColor"
        )
    } else {
        rememberUpdatedState(Color.Transparent)
    }

    val shadowElevation by animateDpAsState(
        targetValue = if (needsInput) 8.dp else 2.dp,
        label = "shadowElevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(shadowElevation, shape = CardDefaults.elevatedShape)
            .animateContentSize()
            .minimumInteractiveComponentSize(),
        onClick = onClick,
        shape = CardDefaults.elevatedShape,
        colors = CardDefaults.elevatedCardColors(),
        border = if (needsInput) BorderStroke(2.dp, borderColor) else null,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(status = node.status)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = node.agentType.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (node.connectedTo.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "↔ ${node.connectedTo.size}",
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

             if (node.metrics != null) {
                 HorizontalDivider(
                     modifier = Modifier.padding(vertical = 4.dp),
                     thickness = 0.5.dp,
                     color = MaterialTheme.colorScheme.outlineVariant
                 )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.burn_rate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.tokens_per_min, node.metrics.tokensPerMinute),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cost_format, node.metrics.currency, node.metrics.sessionCost),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (needsInput) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSendInput(node.id, "y\n") },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = canWrite
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.approve))
                    }
                    Button(
                        onClick = { onSendInput(node.id, "n\n") },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = canWrite
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reject))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(status: AgentStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating")
    val pulsatingColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFFFA500), 
        targetValue = Color(0xFFFFEB3B), 
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color"
    )

    val (color, label) = when (status) {
        AgentStatus.IDLE -> Color(0xFF9E9E9E) to "IDLE"
        AgentStatus.RUNNING -> Color(0xFF4CAF50) to "RUNNING"
        AgentStatus.WAITING -> Color(0xFFFFC107) to "WAITING"
        AgentStatus.DONE -> Color(0xFF2196F3) to "DONE"
        AgentStatus.ERROR -> Color(0xFFF44336) to "ERROR"
        AgentStatus.NEEDS_INPUT -> pulsatingColor to "NEEDS INPUT"
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(24.dp) 
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (status == AgentStatus.NEEDS_INPUT) Color(0xFFFFA500) else color,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        maxItemsInEachRow = maxItemsInEachRow,
        content = content
    )
}
