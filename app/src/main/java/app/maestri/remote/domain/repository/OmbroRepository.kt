package app.maestri.remote.domain.repository

import app.maestri.remote.domain.model.OmbroSummary
import kotlinx.coroutines.flow.Flow

interface OmbroRepository {
    val latestSummary: Flow<OmbroSummary?>
    suspend fun saveSummary(summary: OmbroSummary)
    suspend fun requestSummary()
}
