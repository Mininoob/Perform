package com.preform.desktop

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

/** Tiny but real books for tests: an EPUB 3 with a cover, and a PDF. */
object TestBooks {

    fun epub(
        file: Path,
        title: String,
        author: String = "Test Author",
        rtl: Boolean = false,
        withCover: Boolean = true,
        body: String = "Hello",
        subjects: List<String> = emptyList(),
    ): Path {
        file.parent.createDirectories()
        ZipOutputStream(file.outputStream()).use { zip ->
            // The mimetype entry comes first and is stored, as the EPUB spec requires.
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                },
            )
            zip.write(mimetype)
            fun add(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
            }
            add(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
            )
            val coverItem = if (withCover) """<item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>""" else ""
            add(
                "OEBPS/content.opf",
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier>
                  <dc:title>$title</dc:title><dc:creator>$author</dc:creator><dc:language>en</dc:language>${subjects.joinToString("") { "<dc:subject>$it</dc:subject>" }}</metadata>
                  <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>$coverItem</manifest>
                  <spine${if (rtl) " page-progression-direction=\"rtl\"" else ""}><itemref idref="c1"/></spine></package>""".toByteArray(),
            )
            add("OEBPS/c1.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>$body</p></body></html>".toByteArray())
            if (withCover) add("OEBPS/cover.jpg", jpeg())
        }
        return file
    }

    fun pdf(file: Path, pages: Int = 3): Path {
        file.parent.createDirectories()
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.documentInformation.title = "Test PDF"
            doc.save(file.toFile())
        }
        return file
    }

    fun jpeg(): ByteArray {
        val image = BufferedImage(120, 180, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(122, 74, 43); fillRect(0, 0, 120, 180); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }
}
