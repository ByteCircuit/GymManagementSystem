package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.MembershipService
import org.example.t3gymmanagementsystem.services.PhotoSupport
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class MemberPhotoTest {
    private lateinit var dir: Path
    private lateinit var service: MembershipService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-photo")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        service = MembershipService(db, SettingsRepository(db), MemberRepository(db)) { LocalDate.of(2026, 1, 15) }
        service.savePricing(Pricing(100_000, null))
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun registerOne(): Long {
        val r = service.register(
            NewMember("Juan Dela Cruz", "09171234567", "", "", "", MembershipKind.Monthly, isStudent = false)
        )
        assertIs<Outcome.Ok<Registration>>(r)
        return r.value.memberId
    }

    @Test
    fun photoRoundTripsInTheDatabase() {
        val id = registerOne()
        assertNull(service.getPhoto(id))
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        assertIs<Outcome.Ok<Unit>>(service.savePhoto(id, bytes))
        assertContentEquals(bytes, service.getPhoto(id))
    }

    @Test
    fun savingForUnknownMemberOrEmptyBytesFails() {
        assertIs<Outcome.Fail>(service.savePhoto(999, byteArrayOf(1)))
        val id = registerOne()
        assertIs<Outcome.Fail>(service.savePhoto(id, ByteArray(0)))
    }

    @Test
    fun normalizeShrinksLargeImagesToFourHundredPixels() {
        val file = File(dir.toFile(), "big.png")
        ImageIO.write(BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB), "png", file)
        val out = ImageIO.read(PhotoSupport.normalize(file).inputStream())
        assertEquals(400, out.width)
        assertEquals(200, out.height)
    }

    @Test
    fun normalizeKeepsSmallImagesAndRejectsNonImages() {
        val small = File(dir.toFile(), "small.png")
        ImageIO.write(BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "png", small)
        val out = ImageIO.read(PhotoSupport.normalize(small).inputStream())
        assertEquals(100, out.width)
        val notImage = File(dir.toFile(), "note.png").also { it.writeText("not an image") }
        assertFailsWith<IllegalStateException> { PhotoSupport.normalize(notImage) }
    }
}