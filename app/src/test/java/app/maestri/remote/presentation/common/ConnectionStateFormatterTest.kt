package app.maestri.remote.presentation.common

import app.maestri.remote.domain.repository.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ConnectionStateFormatterTest {

    private val now = Instant.parse("2023-10-27T10:00:00Z")

    @Test
    fun `Connected state message`() {
        val state = ConnectionState.Connected
        assertEquals("Tilkoblet", ConnectionStateFormatter.formatMessage(state, now))
    }

    @Test
    fun `Disconnected state message`() {
        val state = ConnectionState.Disconnected(now.minus(5, ChronoUnit.MINUTES))
        assertEquals("Frakoblet — viser sist kjente tilstand", ConnectionStateFormatter.formatMessage(state, now))
    }

    @Test
    fun `Stale state message - just now`() {
        val lastSync = now.minusSeconds(30)
        val state = ConnectionState.Stale(lastSync)
        assertEquals("Sist oppdatert akkurat nå", ConnectionStateFormatter.formatMessage(state, now))
    }

    @Test
    fun `Stale state message - minutes ago`() {
        val lastSync = now.minus(5, ChronoUnit.MINUTES)
        val state = ConnectionState.Stale(lastSync)
        assertEquals("Sist oppdatert 5 min siden", ConnectionStateFormatter.formatMessage(state, now))
    }

    @Test
    fun `Stale state message - hour ago`() {
        val lastSync = now.minus(65, ChronoUnit.MINUTES)
        val state = ConnectionState.Stale(lastSync)
        assertEquals("Sist oppdatert over en time siden", ConnectionStateFormatter.formatMessage(state, now))
    }

    @Test
    fun `Reconnecting state message`() {
        val lastSync = now.minus(2, ChronoUnit.MINUTES)
        val state = ConnectionState.Reconnecting(attempt = 3, lastSync = lastSync)
        assertEquals("Gjenoppretter (Forsøk 3) — Sist oppdatert 2 min siden", ConnectionStateFormatter.formatMessage(state, now))
    }
}
