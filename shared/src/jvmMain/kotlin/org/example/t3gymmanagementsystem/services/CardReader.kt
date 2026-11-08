package org.example.t3gymmanagementsystem.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.example.t3gymmanagementsystem.ReaderEvent
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.TerminalFactory

/**
 * Reads card IDs from a PC/SC reader such as the ACR122U using Java Smart Card I/O.
 * The ID comes from the reader's standard "get UID" command (FF CA 00 00 00), which works for both
 * NTAG and Mifare Classic cards without any key.
 *
 * While a card stays on the reader its ID is re-read every few hundred milliseconds:
 *  - the same ID again is ignored, so one tap is one scan;
 *  - a different ID is a new scan, so cards swapped quickly are each detected.
 *
 * Reconnecting: after the reader is unplugged, Java keeps a stale link to Windows' smart card service and
 * will not see the reader again. [resetPcsc] drops that link so the next look-up opens a new one. This is done
 * automatically while the reader is missing or failing, and on [requestRefresh]. It needs the JVM option
 * --add-opens=java.smartcardio/sun.security.smartcardio=ALL-UNNAMED (set in desktopApp/build.gradle.kts).
 */
object CardReader {
    private val GET_UID = byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00)
    private const val POLL_MS = 400L
    private const val RETRY_MS = 600L
    private const val MAX_READ_FAILURES = 3
    private const val MAX_ABSENT_WAIT_POLLS = 50

    @Volatile private var refreshRequested = false
    @Volatile private var factory: TerminalFactory? = null

    /** Ask the running reader loop to drop its connection and look for the reader again. */
    fun requestRefresh() {
        refreshRequested = true
    }

    private fun takeRefresh(): Boolean {
        if (!refreshRequested) return false
        refreshRequested = false
        return true
    }

    /** Forget the stale link to the smart card service. Returns false if the JVM option is missing. */
    private fun resetPcsc(): Boolean {
        factory = null
        return try {
            val type = Class.forName("sun.security.smartcardio.PCSCTerminals")
            val field = type.getDeclaredField("contextId")
            field.isAccessible = true
            field.setLong(null, 0L)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun terminalFactory(): TerminalFactory {
        factory?.let { return it }
        val created = try {
            TerminalFactory.getInstance("PC/SC", null)
        } catch (e: Exception) {
            TerminalFactory.getDefault()
        }
        factory = created
        return created
    }

    fun events(): Flow<ReaderEvent> = flow {
        var lastStatus: String? = null
        suspend fun status(message: String, connected: Boolean) {
            if (message != lastStatus) {
                lastStatus = message
                emit(ReaderEvent.Status(message, connected))
            }
        }

        while (currentCoroutineContext().isActive) {
            if (takeRefresh()) {
                resetPcsc()
                status("Refreshing the reader...", false)
                delay(400)
            }

            val terminal = pickTerminal()
            if (terminal == null) {
                val reset = resetPcsc() // so the next attempt opens a fresh link
                status(
                    if (reset) "No card reader found. Plug in the ACR122U; it is picked up again automatically."
                    else "No card reader found. Plug in the ACR122U. If it is not picked up, press Refresh reader or restart the app.",
                    false,
                )
                delay(RETRY_MS)
                continue
            }
            status("Reader ready: ${terminal.name}", true)
            val present = try {
                terminal.waitForCardPresent(500)
            } catch (e: Exception) {
                resetPcsc()
                status("Reader disconnected. Waiting for it to come back...", false)
                delay(RETRY_MS)
                continue
            }
            if (!present) continue

            emit(ReaderEvent.Reading)
            var lastUid: String? = null
            var failures = 0
            var firstPass = true
            var gaveUp = false
            while (currentCoroutineContext().isActive) {
                if (!firstPass) {
                    delay(POLL_MS)
                    if (refreshRequested) break
                    val stillThere = try { terminal.isCardPresent } catch (e: Exception) { false }
                    if (!stillThere) break
                }
                firstPass = false

                var uid: String? = null
                var failure: String? = null
                try {
                    uid = readUid(terminal)
                } catch (e: Exception) {
                    failure = e.message ?: "unknown"
                }

                if (uid != null) {
                    failures = 0
                    if (uid != lastUid) {
                        if (lastUid != null) emit(ReaderEvent.Reading)
                        emit(ReaderEvent.Scan(uid))
                        lastUid = uid
                    }
                } else {
                    failures++
                    if (lastUid == null && failures == 1) emit(ReaderEvent.ReadFailed("Could not read the card: $failure"))
                    if (failures >= MAX_READ_FAILURES) {
                        gaveUp = true
                        break
                    }
                }
            }

            // A card that cannot be read must not be retried in a loop: wait (for a while) until it is taken away.
            if (gaveUp) {
                var polls = 0
                while (currentCoroutineContext().isActive && polls < MAX_ABSENT_WAIT_POLLS && !refreshRequested &&
                    (try { terminal.isCardPresent } catch (e: Exception) { false })
                ) {
                    delay(300)
                    polls++
                }
            }
            emit(ReaderEvent.Removed)
        }
    }.flowOn(Dispatchers.IO)

    private fun pickTerminal(): CardTerminal? {
        val all = try {
            terminalFactory().terminals().list()
        } catch (e: Exception) {
            emptyList()
        }
        return all.firstOrNull { it.name.contains("ACR122", ignoreCase = true) } ?: all.firstOrNull()
    }

    private fun readUid(terminal: CardTerminal): String {
        val card = terminal.connect("*")
        try {
            val response = card.basicChannel.transmit(CommandAPDU(GET_UID))
            if (response.sw != 0x9000) error("The reader returned status %04X.".format(response.sw))
            return response.data.joinToString("") { "%02X".format(it) }
        } finally {
            try {
                card.disconnect(false)
            } catch (e: Exception) {
                // nothing more to do if the card was already removed
            }
        }
    }
}