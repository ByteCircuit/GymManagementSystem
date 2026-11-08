package org.example.t3gymmanagementsystem

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NfcCheckInControllerTest {
    private val events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    private var lookups = 0

    private fun ok(name: String, status: CheckInStatus = CheckInStatus.Welcome) =
        CheckInResult(status, name, "T3-0001", "Membership runs until Feb 15, 2026.", "09:00 AM")

    private fun harness(
        idleMillis: Long = 60_000,
        lookup: suspend (String) -> CheckInResult,
        manual: suspend (Long) -> CheckInResult = { error("not used") },
        body: suspend CoroutineScope.(NfcCheckInController) -> Unit,
    ) = runBlocking {
        val controller = NfcCheckInController(events, lookup, manual, { lookups++ }, idleMillis)
        val job = launch { controller.run() }
        withTimeout(10_000) {
            events.subscriptionCount.first { it > 0 }
            body(controller)
        }
        job.cancel()
    }

    private suspend fun NfcCheckInController.await(predicate: (NfcUiState) -> Boolean): NfcUiState =
        withTimeout(5_000) { state.first(predicate) }

    @Test
    fun aSlowOlderLookupNeverReplacesTheNewerCard() = harness(
        lookup = { uid ->
            if (uid == "AAAAAAAA") { delay(300); ok("Card A") } else { delay(20); ok("Card B") }
        },
    ) { c ->
        events.emit(ReaderEvent.Scan("AAAAAAAA"))
        events.emit(ReaderEvent.Scan("BBBBBBBB"))
        val shown = c.await { it.state == ReaderState.SUCCESS }
        assertEquals("Card B", shown.result?.memberName)
        delay(600) // card A's lookup finishes in this time
        assertEquals("Card B", c.state.value.result?.memberName)
        assertEquals(2, lookups) // both lookups still ran and finished
    }

    @Test
    fun aSlowOlderFailureDoesNotShowAnError() = harness(
        lookup = { uid ->
            if (uid == "AAAAAAAA") { delay(200); error("boom") } else ok("Card B")
        },
    ) { c ->
        events.emit(ReaderEvent.Scan("AAAAAAAA"))
        events.emit(ReaderEvent.Scan("BBBBBBBB"))
        c.await { it.state == ReaderState.SUCCESS }
        delay(400)
        assertEquals(ReaderState.SUCCESS, c.state.value.state)
        assertEquals("Card B", c.state.value.result?.memberName)
    }

    @Test
    fun theOldMemberIsClearedAtOnce() = harness(lookup = { uid ->
        if (uid == "AAAAAAAA") ok("Card A") else { delay(300); ok("Card B") }
    }) { c ->
        events.emit(ReaderEvent.Scan("AAAAAAAA"))
        assertEquals("Card A", c.await { it.state == ReaderState.SUCCESS }.result?.memberName)

        events.emit(ReaderEvent.Reading)
        val reading = c.await { it.state == ReaderState.READING }
        assertNull(reading.result)

        events.emit(ReaderEvent.Scan("BBBBBBBB"))
        val processing = c.await { it.state == ReaderState.PROCESSING }
        assertNull(processing.result)
        assertEquals("Card B", c.await { it.state == ReaderState.SUCCESS }.result?.memberName)
    }

    @Test
    fun anUnknownCardIsReported() = harness(lookup = { CheckInResult(CheckInStatus.UnknownCard, detail = "not assigned") }) { c ->
        events.emit(ReaderEvent.Scan("04A1B2C3"))
        assertEquals(ReaderState.UNKNOWN_CARD, c.await { it.state == ReaderState.UNKNOWN_CARD }.state)
    }

    @Test
    fun aFailedLookupShowsAnErrorThenRecovers() = harness(lookup = { uid ->
        if (uid == "AAAAAAAA") error("database is locked") else ok("Card B")
    }) { c ->
        events.emit(ReaderEvent.Scan("AAAAAAAA"))
        val failed = c.await { it.state == ReaderState.ERROR }
        assertEquals("database is locked", failed.errorMessage)
        events.emit(ReaderEvent.Scan("BBBBBBBB"))
        assertEquals("Card B", c.await { it.state == ReaderState.SUCCESS }.result?.memberName)
    }

    @Test
    fun anUnreadableCardShowsAnError() = harness(lookup = { ok("x") }) { c ->
        events.emit(ReaderEvent.ReadFailed("Could not read the card: no response"))
        val shown = c.await { it.state == ReaderState.ERROR }
        assertEquals("Could not read the card: no response", shown.errorMessage)
    }

    @Test
    fun disconnectAndReconnect() = harness(lookup = { ok("Card A") }) { c ->
        events.emit(ReaderEvent.Status("Reader ready: ACR122U", true))
        assertEquals(true, c.await { it.connected }.connected)

        events.emit(ReaderEvent.Status("No card reader found.", false))
        val gone = c.await { it.state == ReaderState.DISCONNECTED }
        assertEquals(false, gone.connected)

        events.emit(ReaderEvent.Status("Reader ready: ACR122U", true))
        assertEquals(ReaderState.IDLE, c.await { it.state == ReaderState.IDLE && it.connected }.state)

        events.emit(ReaderEvent.Scan("AAAAAAAA"))
        assertEquals("Card A", c.await { it.state == ReaderState.SUCCESS }.result?.memberName)
    }

    @Test
    fun returnsToIdleAfterTheTimeoutAndANewCardOverridesIt() = harness(idleMillis = 150, lookup = { uid -> ok(uid) }) { c ->
        events.emit(ReaderEvent.Status("Reader ready: ACR122U", true))
        events.emit(ReaderEvent.Scan("AAAAAAAA"))
        c.await { it.state == ReaderState.SUCCESS }
        val idle = c.await { it.state == ReaderState.IDLE }
        assertNull(idle.result)
        assertEquals(true, idle.connected)

        events.emit(ReaderEvent.Scan("BBBBBBBB"))
        c.await { it.state == ReaderState.SUCCESS }
        events.emit(ReaderEvent.Scan("CCCCCCCC")) // lands before the idle timer of the previous scan fires
        assertEquals("CCCCCCCC", c.await { it.state == ReaderState.SUCCESS && it.result?.memberName == "CCCCCCCC" }.result?.memberName)
    }

    @Test
    fun aManualCheckInShowsInTheSamePlace() = harness(
        lookup = { ok("from card") },
        manual = { id -> ok("Member $id") },
    ) { c ->
        c.manualCheckIn(7)
        assertEquals("Member 7", c.await { it.state == ReaderState.SUCCESS }.result?.memberName)
    }

    @Test
    fun aCardRemovedBeforeItWasReadLeavesTheReaderIdle() = harness(lookup = { ok("x") }) { c ->
        events.emit(ReaderEvent.Reading)
        c.await { it.state == ReaderState.READING }
        events.emit(ReaderEvent.Removed)
        c.await { it.state == ReaderState.IDLE }
    }
}