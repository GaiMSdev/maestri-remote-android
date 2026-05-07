package app.maestri.remote.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.maestri.remote.data.model.OmbroRequestDto
import app.maestri.remote.domain.model.OmbroSummary
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.OmbroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ombro_prefs")

@Singleton
class OmbroRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val json: Json
) : OmbroRepository {

    private object PreferencesKeys {
        val LATEST_SUMMARY = stringPreferencesKey("latest_summary")
    }

    override val latestSummary: Flow<OmbroSummary?> = context.dataStore.data.map { preferences ->
        val summaryJson = preferences[PreferencesKeys.LATEST_SUMMARY]
        summaryJson?.let {
            try {
                json.decodeFromString<OmbroSummary>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun saveSummary(summary: OmbroSummary) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LATEST_SUMMARY] = json.encodeToString(summary)
        }
    }

    override suspend fun requestSummary() {
        val request = OmbroRequestDto()
        connectionRepository.sendMessage(json.encodeToString(request))
    }
}
