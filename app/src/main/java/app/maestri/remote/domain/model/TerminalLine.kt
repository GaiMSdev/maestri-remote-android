package app.maestri.remote.domain.model

data class TerminalLine(
    val nodeId: String,
    val text: String,
    val sequence: Long
)
