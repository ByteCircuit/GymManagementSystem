package org.example.t3gymmanagementsystem.services

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.example.t3gymmanagementsystem.PickedPhoto
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** Choosing, shrinking and decoding member photos. */
object PhotoSupport {
    private const val MAX_SIDE = 400
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024

    /** Opens the system file dialog. Returns null when the user cancels; throws when the file is not a usable image. */
    fun choose(title: String = "Select member photo", maxSide: Int = MAX_SIDE): PickedPhoto? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val n = name.lowercase()
            n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
        }
        dialog.isVisible = true
        val name = dialog.file
        val dir = dialog.directory
        dialog.dispose()
        if (name == null || dir == null) return null
        val bytes = normalize(File(dir, name), maxSide)
        val preview = toBitmap(bytes) ?: error("Could not read the image.")
        return PickedPhoto(bytes, preview)
    }

    /** Reads a PNG or JPG, shrinks it to at most 400 px on its longest side and returns PNG bytes. */
    fun normalize(file: File, maxSide: Int = MAX_SIDE): ByteArray {
        if (!file.isFile) error("The file could not be found.")
        if (file.length() > MAX_FILE_BYTES) error("Choose an image smaller than 20 MB.")
        val src = ImageIO.read(file) ?: error("That file is not a PNG or JPG image.")
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(src.width, src.height))
        val w = maxOf(1, (src.width * scale).toInt())
        val h = maxOf(1, (src.height * scale).toInt())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        val bos = ByteArrayOutputStream()
        ImageIO.write(out, "png", bos)
        return bos.toByteArray()
    }

    fun toBitmap(bytes: ByteArray): ImageBitmap? =
        ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
}