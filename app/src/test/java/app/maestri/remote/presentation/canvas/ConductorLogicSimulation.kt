package app.maestri.remote.presentation.canvas

import app.maestri.remote.core.haptics.HapticOrchestrator
import app.maestri.remote.core.security.BiometricHelper
import app.maestri.remote.domain.model.*
import app.maestri.remote.domain.repository.*
import app.maestri.remote.domain.usecase.VoiceCommandUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConductorLogicSimulation {

    private val testDispatcher = StandardTestDispatcher()
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    private val workspaceFlow = MutableStateFlow<Workspace?>(null)
    private val isHapticsEnabledFlow = MutableStateFlow(true)

    private val connectionRepository = mockk<ConnectionRepository>(relaxed = true) {
        every { connectionState } returns connectionStateFlow
        every { workspace } returns workspaceFlow
    }
    private val ombroRepository = mockk<OmbroRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { isHapticsEnabled } returns isHapticsEnabledFlow
    }
    private val terminalRepository = mockk<TerminalRepository>(relaxed = true)
    private val voiceCommandUseCase = mockk<VoiceCommandUseCase>(relaxed = true)
    private val hapticOrchestrator = mockk<HapticOrchestrator>(relaxed = true)
    private val biometricHelper = mockk<BiometricHelper>(relaxed = true)

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

    @Test
    fun `SIMULATION - Haptic heartbeat triggers on ERROR status change`() = runTest {
        val node = Node("1", "Agent", AgentType.CLAUDE_CODE, AgentStatus.RUNNING, emptyList())
        val failingNode = node.copy(status = AgentStatus.ERROR)
        
        // Step 1: Initial state (Running)
        workspaceFlow.value = Workspace("ws1", "Test", listOf(node), emptyList(), emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { hapticOrchestrator.triggerFeedback(AgentStatus.ERROR) }

        // Step 2: Change to ERROR
        workspaceFlow.value = Workspace("ws1", "Test", listOf(failingNode), emptyList(), emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        // VERIFICATION: Did the haptic heartbeat fire?
        verify(exactly = 1) { hapticOrchestrator.triggerFeedback(AgentStatus.ERROR) }
        println("✅ Haptic Heartbeat confirmed for ERROR state.")
    }

    @Test
    fun `SIMULATION - Haptic heartbeat triggers on NEEDS_INPUT status change`() = runTest {
        val node = Node("1", "Agent", AgentType.CLAUDE_CODE, AgentStatus.RUNNING, emptyList())
        val inputNode = node.copy(status = AgentStatus.NEEDS_INPUT)
        
        workspaceFlow.value = Workspace("ws1", "Test", listOf(node), emptyList(), emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        workspaceFlow.value = Workspace("ws1", "Test", listOf(inputNode), emptyList(), emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { hapticOrchestrator.triggerFeedback(AgentStatus.NEEDS_INPUT) }
        println("✅ Haptic Heartbeat confirmed for NEEDS_INPUT state.")
    }

    @Test
    fun `SIMULATION - Natural Language Control executes command`() = runTest {
        val input = "Restart failing agents"
        
        // Trigger voice command
        viewModel.executeVoiceCommand(input)
        testDispatcher.scheduler.advanceUntilIdle()

        // VERIFICATION: Did the use case get called?
        coVerify(exactly = 1) { voiceCommandUseCase.execute(input) }
        println("✅ Natural Language Control execution flow confirmed.")
    }
}
