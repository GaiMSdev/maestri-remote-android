package app.maestri.remote.presentation.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.maestri.remote.R
import app.maestri.remote.domain.repository.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun TerminalScreen(
    nodeId: String,  // read by TerminalViewModel via SavedStateHandle
    onBackClick: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    
    val lines by viewModel.lines.collectAsState()
    val canWrite by viewModel.canWrite.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Handle initial command
    LaunchedEffect(viewModel.initialCommand) {
        viewModel.initialCommand?.let {
            inputText = it
        }
    }

    // Auto-scroll logic
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isAtBottom = lastVisibleIndex >= lines.size - 2 // Small buffer
            if (isAtBottom || lastVisibleIndex == 0) {
                listState.animateScrollToItem(lines.size - 1)
            }
        }
    }

     Scaffold(
         topBar = {
TopAppBar(
                  title = { Text(stringResource(R.string.terminal_title)) },
                  navigationIcon = {
                      IconButton(onClick = onBackClick) {
                          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                      }
                  }
              )
         },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        enabled = isConnected && canWrite,
                        placeholder = { 
                            Text(
                                if (!isConnected) stringResource(R.string.terminal_disconnected)
                                else if (!canWrite) stringResource(R.string.terminal_readonly) 
                                else stringResource(R.string.terminal_placeholder)
                            ) 
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        enabled = isConnected && canWrite && inputText.isNotBlank(),
                        onClick = {
                            viewModel.sendInput(inputText)
                            inputText = ""
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(lines, key = { it.sequence }) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = line.text,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
IconButton(
                              onClick = {
                                  val clip = ClipData.newPlainText("Terminal Line", line.text)
                                  clipboardManager.setPrimaryClip(clip)
                              },
                              modifier = Modifier.size(48.dp)
                          ) {
                              Icon(
                                  imageVector = Icons.Default.ContentCopy,
                                  contentDescription = stringResource(R.string.copy_line),
                                  tint = Color.Green.copy(alpha = 0.5f),
                                  modifier = Modifier.size(20.dp)
                              )
                          }
                    }
                }
            }
        }
    }
}
