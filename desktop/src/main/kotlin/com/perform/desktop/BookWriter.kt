package com.perform.desktop

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
    /** A JPEG for an EPUB with no cover (or, with [overwrite], to replace its cover). */
    val cover: ByteArray? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    /**
     * Replace what the file says instead of only filling gaps: for details edited by hand.
     * Every field is then written as given, and a null or empty one is removed.
     */
    val overwrite: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !overwrite && title == null && author == null && genres.isEmpty() && description == null &&
            year == null && cover == null && series == null
}

/**
 * Writes book information into a copy of an EPUB or PDF, the way Calibre does, so it
 * travels with the file to any reader. Never overwrites what the file already says, and
 * leaves alone files it can't safely change (DRM, encrypted PDFs).
 */
object BookWriter {

    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val OPF = "http://www.idpf.org/2007/opf"
    private const val COVER_ID = "perform-cover"

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
        fun clear(tag: String) {
            val list = metadata.getElementsByTagNameNS(DC, tag)
            (0 until list.length).map { list.item(it) }.forEach { it.parentNode.removeChild(it); changed = true }
        }

        if (fill.overwrite) {
            // Hand edits: what the person typed replaces what the file said.
            fill.title?.let { clear("title"); add("title", it) }
            clear("creator"); fill.author?.let { add("creator", it) }
            clear("subject"); fill.genres.forEach { add("subject", it) }
            clear("description"); fill.description?.let { add("description", it) }
            clear("date"); fill.year?.let { add("date", it.toString()) }
            changed = setSeries(opf, metadata, fill.series, fill.seriesIndex) || changed
        } else {
            fill.title?.takeIf { !has("title") }?.let { add("title", it) }
            fill.author?.takeIf { !has("creator") }?.let { add("creator", it) }
            if (!has("subject")) fill.genres.forEach { add("subject", it) }
            fill.description?.takeIf { !has("description") }?.let { add("description", it) }
            fill.year?.takeIf { !has("date") }?.let { add("date", it.toString()) }
        }

        val coverBytes = fill.cover?.takeIf { fill.overwrite || !hasCover(opf) }
        if (coverBytes != null) {
            if (fill.overwrite) unmarkCovers(opf)
            addCover(opf, metadata)
            changed = true
        }
        if (!changed) return false

        ZipOutputStream(target.outputStream()).use { out ->
            // The mimetype entry first and stored, as EPUB requires; then everything else as it was.
            val coverEntry = "$opfDir$COVER_ID.jpg"
            val names = zip.entries().toList().map { it.name }
                .filter { coverBytes == null || it != coverEntry } // a new cover replaces ours from before
                .sortedBy { if (it == "mimetype") 0 else 1 }
            for (name in names) {
                val original = zip.getEntry(name)
                val bytes = if (name == opfPath) serialize(opf) else zip.getInputStream(original).readBytes()
                out.putEntry(name, bytes, stored = original.method == ZipEntry.STORED)
            }
            if (coverBytes != null) out.putEntry(coverEntry, coverBytes, stored = false)
        }
        true
    }

    /** Writes [fill] into a copy of a PDF's document information; false when nothing to write or it's encrypted. */
    fun writePdf(source: Path, fill: BookFill, target: Path): Boolean = Loader.loadPDF(source.toFile()).use { pdf ->
        if (pdf.isEncrypted) return false
        val info = pdf.documentInformation
        var changed = false
        fun missing(value: String?) = value.isNullOrBlank()

        if (fill.overwrite) {
            fill.title?.let { info.title = it }
            info.author = fill.author
            info.subject = fill.description
            info.keywords = fill.genres.takeIf { it.isNotEmpty() }?.joinToString(", ")
            info.setCustomMetadataValue("PublicationYear", fill.year?.toString())
            info.setCustomMetadataValue("Series", fill.series)
            info.setCustomMetadataValue("SeriesIndex", fill.seriesIndex?.let(::indexText))
            changed = true
        } else {
            fill.title?.takeIf { missing(info.title) }?.let { info.title = it; changed = true }
            fill.author?.takeIf { missing(info.author) }?.let { info.author = it; changed = true }
            fill.description?.takeIf { missing(info.subject) }?.let { info.subject = it; changed = true }
            fill.genres.takeIf { it.isNotEmpty() && missing(info.keywords) }?.let { info.keywords = it.joinToString(", "); changed = true }
            fill.year?.takeIf { missing(info.getCustomMetadataValue("PublicationYear")) }?.let {
                info.setCustomMetadataValue("PublicationYear", it.toString())
                changed = true
            }
        }
        if (!changed) return false
        info.modificationDate = Calendar.getInstance()
        pdf.save(target.toFile())
        true
    }

    /** "2" for 2.0, "2.5" otherwise. */
    private fun indexText(index: Double): String = if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()

    /**
     * Writes the series both ways readers look for it: EPUB 3's belongs-to-collection with
     * a group-position, and Calibre's metas. Removes any earlier series first.
     */
    private fun setSeries(opf: Document, metadata: Element, series: String?, index: Double?): Boolean {
        val metas = opf.getElementsByTagNameNS("*", "meta").let { list -> (0 until list.length).map { list.item(it) as Element } }
        val collectionIds = metas.filter { it.getAttribute("property") == "belongs-to-collection" }.map { "#" + it.getAttribute("id") }.toSet()
        val old = metas.filter {
            it.getAttribute("property") == "belongs-to-collection" ||
                it.getAttribute("refines") in collectionIds ||
                it.getAttribute("name") == "calibre:series" ||
                it.getAttribute("name") == "calibre:series_index"
        }
        old.forEach { it.parentNode.removeChild(it) }
        if (series == null) return old.isNotEmpty()

        if (opf.documentElement.getAttribute("version").startsWith("3")) {
            metadata.appendChild(opf.createElementNS(OPF, "meta").apply {
                setAttribute("property", "belongs-to-collection")
                setAttribute("id", "perform-series")
                textContent = series
            })
            metadata.appendChild(opf.createElementNS(OPF, "meta").apply {
                setAttribute("refines", "#perform-series")
                setAttribute("property", "collection-type")
                textContent = "series"
            })
            if (index != null) {
                metadata.appendChild(opf.createElementNS(OPF, "meta").apply {
                    setAttribute("refines", "#perform-series")
                    setAttribute("property", "group-position")
                    textContent = indexText(index)
                })
            }
        }
        metadata.appendChild(opf.createElementNS(OPF, "meta").apply {
            setAttribute("name", "calibre:series")
            setAttribute("content", series)
        })
        if (index != null) {
            metadata.appendChild(opf.createElementNS(OPF, "meta").apply {
                setAttribute("name", "calibre:series_index")
                setAttribute("content", indexText(index))
            })
        }
        return true
    }

    /** Stops pointing at the old cover image (the image itself stays, unused). */
    private fun unmarkCovers(opf: Document) {
        val items = opf.getElementsByTagNameNS("*", "item")
        (0 until items.length).map { items.item(it) as Element }.forEach { item ->
            if (item.getAttribute("id") == COVER_ID) {
                item.parentNode.removeChild(item)
            } else {
                val props = item.getAttribute("properties").split(' ').filter { it.isNotBlank() && it != "cover-image" }
                if (props.isEmpty()) item.removeAttribute("properties") else item.setAttribute("properties", props.joinToString(" "))
            }
        }
        val metas = opf.getElementsByTagNameNS("*", "meta")
        (0 until metas.length).map { metas.item(it) as Element }
            .filter { it.getAttribute("name") == "cover" }
            .forEach { it.parentNode.removeChild(it) }
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
