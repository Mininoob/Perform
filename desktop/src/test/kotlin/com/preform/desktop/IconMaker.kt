package com.preform.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.preform.desktop.ui.BookMark
import com.preform.desktop.ui.colorOn
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Draws the app icon (the book mark on an Ink tile) at every size and writes the Windows
 * `.ico` and macOS `.icns` from it: `./gradlew :desktop:test --tests '*IconMaker*' -Dicons.out=desktop/icons`.
 */
class IconMaker {

    @Test
    fun make() {
        val out = System.getProperty("icons.out")
        assumeTrue("Set icons.out to make icons", !out.isNullOrBlank())
        val dir = Path.of(out!!).createDirectories()

        val sizes = listOf(16, 24, 32, 48, 64, 128, 256, 512, 1024)
        val png = sizes.associateWith(::render)

        dir.resolve("icon.png").writeBytes(png.getValue(512))
        dir.resolve("icon.ico").writeBytes(ico(listOf(16, 24, 32, 48, 64, 128, 256).map { it to png.getValue(it) }))
        dir.resolve("icon.icns").writeBytes(
            icns(
                listOf(
                    "icp4" to png.getValue(16), "icp5" to png.getValue(32), "ic11" to png.getValue(32),
                    "ic12" to png.getValue(64), "ic07" to png.getValue(128), "ic13" to png.getValue(256),
                    "ic08" to png.getValue(256), "ic14" to png.getValue(512), "ic09" to png.getValue(512),
                    "ic10" to png.getValue(1024),
                ),
            ),
        )
    }

    /** The tile follows Apple's grid: about 80% of the canvas, rounded at roughly 22%. */
    private fun render(size: Int): ByteArray {
        val ink = Color(0xFF131416)
        val accent = com.preform.desktop.ThemeAccent.BLUE.colorOn(com.preform.desktop.ThemeBase.INK)
        val small = size <= 32 // at tiny sizes, the book alone reads better than a tile
        val scene = ImageComposeScene(size, size, Density(1f)) {
            Box(Modifier.fillMaxSize().padding(if (small) 0.dp else (size * 0.1f).dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (small) Modifier else Modifier.background(ink, RoundedCornerShape((size * 0.18f).dp)))
                        .padding(if (small) 0.dp else (size * 0.10f).dp),
                ) {
                    Image(com.preform.desktop.ui.SeriesMark(), null, Modifier.fillMaxSize())
                }
            }
        }
        val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        return bytes
    }

    /** An ICO holding PNG images (Windows Vista and later read these). */
    private fun ico(images: List<Pair<Int, ByteArray>>): ByteArray {
        val header = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = 6 + 16 * images.size
        images.forEach { (size, data) ->
            header.put((if (size >= 256) 0 else size).toByte()).put((if (size >= 256) 0 else size).toByte())
            header.put(0).put(0).putShort(1).putShort(32).putInt(data.size).putInt(offset)
            offset += data.size
        }
        return ByteArrayOutputStream().apply {
            write(header.array())
            images.forEach { write(it.second) }
        }.toByteArray()
    }

    /** An ICNS of PNG entries, which macOS reads for every size. */
    private fun icns(entries: List<Pair<String, ByteArray>>): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            entries.forEach { (type, data) ->
                out.writeBytes(type)
                out.writeInt(8 + data.size)
                out.write(data)
            }
        }
        val all = ByteArrayOutputStream()
        DataOutputStream(all).use { out ->
            out.writeBytes("icns")
            out.writeInt(8 + body.size())
            out.write(body.toByteArray())
        }
        return all.toByteArray()
    }
}
