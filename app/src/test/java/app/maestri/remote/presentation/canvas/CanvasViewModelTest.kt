package app.maestri.remote.presentation.canvas

import app.maestri.remote.core.security.BiometricHelper
import app.maestri.remote.domain.model.Node
import app.maestri.remote.domain.model.Workspace
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.ConnectionState
import app.maestri.remote.domain.repository.OmbroRepository
import app.maestri.remote.domain.repository.SettingsRepository
import app.maestri.remote.domain.repository.TerminalRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CanvasViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val workspaceFlow = MutableStateFlow<Workspace?>(null)
    private val ombroFlow = MutableStateFlow<app.maestri.remote.domain.model.OmbroSummary?>(null)
    private val biometricFlow = MutableStateFlow(false)

    private val connectionRepository = mockk<ConnectionRepository>(relaxed = true) {
        every { connectionState } returns connectionStateFlow
        every { workspace } returns workspaceFlow
    }
    private val ombroRepository = mockk<OmbroRepository>(relaxed = true) {
        every { latestSummary } returns ombroFlow
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { isBiometricEnabled } returns biometricFlow
    }
    private val terminalRepository = mockk<TerminalRepository>(relaxed = true)
    private val biometricHelper = mockk<BiometricHelper>(relaxed = true)
    private val voiceCommandUseCase = mockk<VoiceCommandUseCase>(relaxed = true)
    private val hapticOrchestrator = mockk<HapticOrchestrator>(relaxed = true)

    private lateinit var viewModel: CanvasViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CanvasViewModel(
            connectionRepository, ombroRepository, settingsRepository,
            terminalRepository, voiceCommandUseCase, hapticOrchestrator, biometricHelper
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // UiState transition tests

    @Test
    fun `initial state is Loading`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitItem() is CanvasUiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state becomes Success when connection emits`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading
            connectionStateFlow.value = ConnectionState.Connected
            testDispatcher.scheduler.advanceUntilIdle()
            val success = awaitItem()
            assertTrue(success is CanvasUiState.Success)
            assertEquals(ConnectionState.Connected, (success as CanvasUiState.Success).connectionState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state reflects Disconnected with timestamp`() = runTest {
        val since = Instant.now()
        viewModel.uiState.test {
            awaitItem() // Loading
            connectionStateFlow.value = ConnectionState.Disconnected(since)
            testDispatcher.scheduler.advanceUntilIdle()
            val success = awaitItem() as CanvasUiState.Success
            val state = success.connectionState
            assertTrue(state is ConnectionState.Disconnected)
            assertEquals(since, (state as ConnectionState.Disconnected).since)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state reflects Stale with lastSync timestamp`() = runTest {
        val lastSync = Instant.now()
        viewModel.uiState.test {
            awaitItem()
            connectionStateFlow.value = ConnectionState.Stale(lastSync)
            testDispatcher.scheduler.advanceUntilIdle()
            val success = awaitItem() as CanvasUiState.Success
            val state = success.connectionState
            assertTrue(state is ConnectionState.Stale)
            assertEquals(lastSync, (state as ConnectionState.Stale).lastSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state reflects Reconnecting with attempt and lastSync`() = runTest {
        val lastSync = Instant.now()
        val attempt = 3
        viewModel.uiState.test {
            awaitItem()
            connectionStateFlow.value = ConnectionState.Reconnecting(attempt, lastSync)
            testDispatcher.scheduler.advanceUntilIdle()
            val success = awaitItem() as CanvasUiState.Success
            val state = success.connectionState
            assertTrue(state is ConnectionState.Reconnecting)
            assertEquals(attempt, (state as ConnectionState.Reconnecting).attempt)
            assertEquals(lastSync, (state as ConnectionState.Reconnecting).lastSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // canWrite tests

    @Test
    fun `canWrite is false when not Connected`() = runTest {
        connectionStateFlow.value = ConnectionState.Disconnected(Instant.now())
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.canWrite.value)
    }

    @Test
    fun `canWrite is true only when Connected`() = runTest {
        viewModel.canWrite.test {
            assertEquals(false, awaitItem()) // initial: Connecting
            
            connectionStateFlow.value = ConnectionState.Connected
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, awaitItem())
            
            connectionStateFlow.value = ConnectionState.Disconnected(Instant.now())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(false, awaitItem())

            // These should NOT emit new items because value remains false
            connectionStateFlow.value = ConnectionState.Reconnecting(attempt = 1, lastSync = Instant.now())
            testDispatcher.scheduler.advanceUntilIdle()
            
            connectionStateFlow.value = ConnectionState.Stale(Instant.now())
            testDispatcher.scheduler.advanceUntilIdle()

            connectionStateFlow.value = ConnectionState.Discovering
            testDispatcher.scheduler.advanceUntilIdle()
            
            expectNoEvents()
            assertEquals(false, viewModel.canWrite.value)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canWrite is false during Reconnecting`() = runTest {
        connectionStateFlow.value = ConnectionState.Reconnecting(attempt = 1, lastSync = Instant.now())
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.canWrite.value)
    }

    @Test
    fun `canWrite is false when Stale`() = runTest {
        connectionStateFlow.value = ConnectionState.Stale(Instant.now())
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.canWrite.value)
    }

    // Workspace in UiState

    @Test
    fun `workspace is reflected in Success state`() = runTest {
        val workspace = Workspace(
            id = "ws1", name = "Test", nodes = emptyList(),
            notes = emptyList(), connections = emptyList()
        )
        viewModel.uiState.test {
            awaitItem()
            connectionStateFlow.value = ConnectionState.Connected
            workspaceFlow.value = workspace
            testDispatcher.scheduler.advanceUntilIdle()
            val success = expectMostRecentItem() as CanvasUiState.Success
            assertEquals(workspace, success.workspace)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
