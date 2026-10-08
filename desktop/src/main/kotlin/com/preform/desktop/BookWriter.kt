package com.preform.desktop

import org.apache.pdfbox.Loader
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Calendar
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.outputStream

/** Information to write into a book file. Only what the file is missing is written. */
data class BookFill(
    val title: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val year: Int? = null,
    /** A JPEG for an EPUB with no cover. */
    val cover: ByteArray? = null,
) {
    val isEmpty: Boolean
        get() = title == null && author == null && genres.isEmpty() && description == null && year == null && cover == null
}

/**
 * Writes book information into a copy of an EPUB or PDF, the way Calibre does, so it
 * travels with the file to any reader. Never overwrites what the file already says, and
 * leaves alone files it can't safely change (DRM, encrypted PDFs).
 */
object BookWriter {

    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val OPF = "http://www.idpf.org/2007/opf"
    private const val COVER_ID = "preform-cover"

    /** Writes [fill] into a copy of [source] at [target]; false when there was nothing to write or it can't be changed. */
    fun writeEpub(source: Path, fill: BookFill, target: Path): Boolean = ZipFile(source.toFile()).use { zip ->
        // Encrypted content (DRM, or fonts obfuscated against the package id): don't touch.
        if (zip.getEntry("META-INF/encryption.xml") != null) return false

        val container = BookMetadata.parseXml(zip.getInputStream(zip.getEntry("META-INF/container.xml")).readBytes())
        val opfPath = (container.getElementsByTagNameNS("*", "rootfile").item(0) as Element).getAttribute("full-path")
        val opf = BookMetadata.parseXml(zip.getInputStream(zip.getEntry(opfPath) ?: return false).readBytes())
        val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        val metadata = opf.getElementsByTagNameNS(OPF, "metadata").item(0) as? Element
            ?: opf.getElementsByTagNameNS("*", "metadata").item(0) as? Element
            ?: return false
        var changed = false

        fun has(tag: String) = metadata.getElementsByTagNameNS(DC, tag).let { list ->
            (0 until list.length).any { list.item(it).textContent.isNotBlank() }
        }
        fun add(tag: String, value: String) {
            metadata.appendChild(opf.createElementNS(DC, "dc:$tag").apply { textContent = value })
            changed = true
        }

        fill.title?.takeIf { !has("title") }?.let { add("title", it) }
        fill.author?.takeIf { !has("creator") }?.let { add("creator", it) }
        if (!has("subject")) fill.genres.forEach { add("subject", it) }
        fill.description?.takeIf { !has("description") }?.let { add("description", it) }
        fill.year?.takeIf { !has("date") }?.let { add("date", it.toString()) }

        val coverBytes = fill.cover?.takeIf { !hasCover(opf) }
        if (coverBytes != null) {
            addCover(opf, metadata)
            changed = true
        }
        if (!changed) return false

        ZipOutputStream(target.outputStream()).use { out ->
            // The mimetype entry first and stored, as EPUB requires; then everything else as it was.
            val names = zip.entries().toList().map { it.name }.sortedBy { if (it == "mimetype") 0 else 1 }
            for (name in names) {
                val original = zip.getEntry(name)
                val bytes = if (name == opfPath) serialize(opf) else zip.getInputStream(original).readBytes()
                out.putEntry(name, bytes, stored = original.method == ZipEntry.STORED)
            }
            if (coverBytes != null) out.putEntry("$opfDir$COVER_ID.jpg", coverBytes, stored = false)
        }
        true
    }

    /** Writes [fill] into a copy of a PDF's document information; false when nothing to write or it's encrypted. */
    fun writePdf(source: Path, fill: BookFill, target: Path): Boolean = Loader.loadPDF(source.toFile()).use { pdf ->
        if (pdf.isEncrypted) return false
        val info = pdf.documentInformation
        var changed = false
        fun missing(value: String?) = value.isNullOrBlank()

        fill.title?.takeIf { missing(info.title) }?.let { info.title = it; changed = true }
        fill.author?.takeIf { missing(info.author) }?.let { info.author = it; changed = true }
        fill.description?.takeIf { missing(info.subject) }?.let { info.subject = it; changed = true }
        fill.genres.takeIf { it.isNotEmpty() && missing(info.keywords) }?.let { info.keywords = it.joinToString(", "); changed = true }
        fill.year?.takeIf { missing(info.getCustomMetadataValue("PublicationYear")) }?.let {
            info.setCustomMetadataValue("PublicationYear", it.toString())
            changed = true
        }
        if (!changed) return false
        info.modificationDate = Calendar.getInstance()
        pdf.save(target.toFile())
        true
    }

    private fun hasCover(opf: Document): Boolean {
        val items = opf.getElementsByTagNameNS("*", "item")
        if ((0 until items.length).any { (items.item(it) as Element).getAttribute("properties").split(' ').contains("cover-image") }) return true
        val metas = opf.getElementsByTagNameNS("*", "meta")
        return (0 until metas.length).any { (metas.item(it) as Element).getAttribute("name") == "cover" }
    }

    /** Declares the cover both ways: EPUB 3 readers look for the property, EPUB 2 ones for the meta. */
    private fun addCover(opf: Document, metadata: Element) {
        val manifest = opf.getElementsByTagNameNS("*", "manifest").item(0) as Element
        val epub3 = opf.documentElement.getAttribute("version").startsWith("3")
        manifest.appendChild(
            opf.createElementNS(OPF, "item").apply {
                setAttribute("id", COVER_ID)
                setAttribute("href", "$COVER_ID.jpg")
                setAttribute("media-type", "image/jpeg")
                if (epub3) setAttribute("properties", "cover-image")
            },
        )
        metadata.appendChild(
            opf.createElementNS(OPF, "meta").apply {
                setAttribute("name", "cover")
                setAttribute("content", COVER_ID)
            },
        )
    }

    private fun serialize(document: Document): ByteArray = ByteArrayOutputStream().also { out ->
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }.transform(DOMSource(document), StreamResult(out))
    }.toByteArray()

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray, stored: Boolean) {
        val entry = ZipEntry(name)
        if (stored) {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }
}
