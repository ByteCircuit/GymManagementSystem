package org.example.t3gymmanagementsystem

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderState { IDLE, READING, PROCESSING, SUCCESS, UNKNOWN_CARD, ERROR, DISCONNECTED }

data class NfcUiState(
    val state: ReaderState = ReaderState.IDLE,
    val connected: Boolean = false,
    val readerMessage: String = "Looking for the reader...",
    val result: CheckInResult? = null,
    val errorMessage: String? = null,
)

/**
 * Turns card-reader events into what the screen shows.
 *
 * - The previous member is cleared the moment a new card is detected, before any lookup starts.
 * - Every scan gets a sequence number. A lookup may only update the screen while its number is still the
 *   latest, so a slow lookup for card A can never replace the result of card B.
 * - Lookups are not cancelled, because a check-in writes to the database. They finish, and are then ignored
 *   by the screen if a newer scan has happened.
 * - Lookups run beside the event collector, so the reader stays responsive while the database is busy.
 *
 * Call [run] from a coroutine that lives as long as the screen. All calls are expected on one thread (the UI thread).
 */
class NfcCheckInController(
    private val events: Flow<ReaderEvent>,
    private val lookup: suspend (String) -> CheckInResult,
    private val manualLookup: suspend (Long) -> CheckInResult,
    private val onLookupDone: () -> Unit = {},
    private val idleAfterMillis: Long = 5_000,
) {
    private val _state = MutableStateFlow(NfcUiState())
    val state: StateFlow<NfcUiState> = _state.asStateFlow()

    private var runScope: CoroutineScope? = null
    private var sequence = 0L
    private var idleJob: Job? = null

    suspend fun run(): Unit = coroutineScope {
        runScope = this
        try {
            events.collect { handle(it) }
        } finally {
            runScope = null
            idleJob = null
        }
    }

    /** Check in a member chosen by staff. Shown in the same place as a card tap. */
    fun manualCheckIn(memberId: Long) {
        startLookup { manualLookup(memberId) }
    }

    private fun invalidate() {
        sequence++
        idleJob?.cancel()
    }

    private fun handle(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.Status -> {
                if (!event.connected) {
                    invalidate()
                    _state.value = NfcUiState(ReaderState.DISCONNECTED, false, event.message)
                } else {
                    _state.update {
                        if (it.state == ReaderState.DISCONNECTED) NfcUiState(ReaderState.IDLE, true, event.message)
                        else it.copy(connected = true, readerMessage = event.message)
                    }
                }
            }
            ReaderEvent.Reading -> {
                invalidate()
                _state.update { it.copy(state = ReaderState.READING, connected = true, result = null, errorMessage = null) }
            }
            is ReaderEvent.Scan -> startLookup { lookup(event.uid) }
            is ReaderEvent.ReadFailed -> {
                invalidate()
                val id = sequence
                _state.update { it.copy(state = ReaderState.ERROR, result = null, errorMessage = event.message) }
                scheduleIdle(id)
            }
            ReaderEvent.Removed -> {
                // A card taken away before its ID was read leaves nothing to show.
                _state.update { if (it.state == ReaderState.READING) it.copy(state = ReaderState.IDLE) else it }
            }
        }
    }

    private fun startLookup(block: suspend () -> CheckInResult) {
        val scope = runScope ?: return
        invalidate()
        val id = sequence
        _state.update { it.copy(state = ReaderState.PROCESSING, result = null, errorMessage = null) }
        scope.launch {
            var found: CheckInResult? = null
            var failure: String? = null
            try {
                found = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: "Check-in failed."
            }
            onLookupDone()
            if (id != sequence) return@launch // a newer scan has taken over the screen
            val outcome = found
            if (outcome != null) {
                val next = if (outcome.status == CheckInStatus.UnknownCard) ReaderState.UNKNOWN_CARD else ReaderState.SUCCESS
                _state.update { it.copy(state = next, result = outcome, errorMessage = null) }
            } else {
                _state.update { it.copy(state = ReaderState.ERROR, result = null, errorMessage = failure) }
            }
            scheduleIdle(id)
        }
    }

    private fun scheduleIdle(id: Long) {
        val scope = runScope ?: return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleAfterMillis)
            if (id == sequence) {
                _state.update {
                    it.copy(
                        state = if (it.connected) ReaderState.IDLE else ReaderState.DISCONNECTED,
                        result = null,
                        errorMessage = null,
                    )
                }
            }
        }
    }
}