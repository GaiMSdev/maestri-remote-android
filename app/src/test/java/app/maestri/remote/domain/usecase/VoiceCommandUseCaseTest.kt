package app.maestri.remote.domain.usecase

import app.maestri.remote.domain.model.*
import app.maestri.remote.domain.repository.ConnectionRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VoiceCommandUseCaseTest {

    private val repository = mockk<ConnectionRepository>(relaxed = true)
    private lateinit var useCase: VoiceCommandUseCase
    private val workspaceFlow = MutableStateFlow<Workspace?>(null)

    @BeforeEach
    fun setup() {
        useCase = VoiceCommandUseCase(repository)
        every { repository.workspace } returns workspaceFlow
        
        // Mock en standard workspace
        val workspace = Workspace(
            id = "ws1",
            name = "Test Workspace",
            nodes = listOf(
                Node("n1", "Agent 1", AgentType.CLAUDE_CODE, AgentStatus.RUNNING, emptyList()),
                Node("n2", "Agent 2", AgentType.SHELL, AgentStatus.ERROR, emptyList())
            ),
            notes = emptyList(),
            connections = emptyList()
        )
        workspaceFlow.value = workspace
    }

    @Test
    fun `no workspace returns NoWorkspace`() = runTest {
        workspaceFlow.value = null

        val result = useCase.execute("stop all")

        assertTrue(result is VoiceCommandResult.NoWorkspace)
    }

    @Test
    fun `stop all returns RequiresConfirmation instead of calling repository`() = runTest {
        val result = useCase.execute("stop all")
        
        coVerify(exactly = 0) { repository.killAllNodes() }
        
        assertTrue(result is VoiceCommandResult.RequiresConfirmation)
        assertEquals("stop_all", (result as VoiceCommandResult.RequiresConfirmation).action)
    }

    @Test
    fun `restart failing returns RequiresConfirmation instead of executing`() = runTest {
        val result = useCase.execute("restart failing")
        
        coVerify(exactly = 0) { repository.sendNodeAction(any(), any()) }
        
        assertTrue(result is VoiceCommandResult.RequiresConfirmation)
        assertEquals("restart_failing", (result as VoiceCommandResult.RequiresConfirmation).action)
    }

    @Test
    fun `check status returns correct count`() = runTest {
        val result = useCase.execute("check status")
        
        assertTrue(result is VoiceCommandResult.ActiveAgents)
        assertEquals(1, (result as VoiceCommandResult.ActiveAgents).count) // Kun n1 kjører (RUNNING)
    }
}
