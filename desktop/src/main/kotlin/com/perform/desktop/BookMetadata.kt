package com.perform.desktop

import com.perform.sync.Format
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.w3c.dom.Element
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

data class Metadata(
    val title: String,
    val author: String?,
    val rightToLeft: Boolean,
    val cover: BufferedImage?,
    /** Subjects or keywords the book declares, for [com.perform.sync.Genres]. */
    val subjects: List<String> = emptyList(),
    /** No title inside the file: [title] is only its file name. */
    val titleFromFile: Boolean = false,
    val description: String? = null,
    val year: Int? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
)

/** "1965-08-01" or "1965" gives 1965. */
internal fun yearOf(date: String?): Int? = date?.let { Regex("""\b(\d{4})\b""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

/**
 * Reads title, author, reading direction and cover from a book file. Readium only runs
 * on Android, so the PC reads EPUBs itself (they're zip files with an XML manifest) and
 * uses PDFBox for PDFs. Anything unreadable degrades to the file name and no cover.
 */
object BookMetadata {

    fun formatOf(file: Path): Format? = when (file.extension.lowercase()) {
        "epub" -> Format.EPUB
        "pdf" -> Format.PDF
        else -> null
    }

    fun read(file: Path, format: Format): Metadata =
        readOrNull(file, format) ?: Metadata(file.nameWithoutExtension, null, rightToLeft = false, cover = null, titleFromFile = true)

    /** Null when the file can't be read as a book at all. */
    fun readOrNull(file: Path, format: Format): Metadata? = runCatching {
        when (format) {
            Format.EPUB -> readEpub(file)
            Format.PDF -> readPdf(file)
        }
    }.getOrNull()

    private fun readEpub(file: Path): Metadata = ZipFile(file.toFile()).use { zip ->
        val container = zip.xml("META-INF/container.xml")
        val opfPath = container.getElementsByTagNameNS("*", "rootfile").item(0)
            .let { (it as Element).getAttribute("full-path") }
        val opf = zip.xml(opfPath)
        val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        fun text(tag: String) = opf.getElementsByTagNameNS("*", tag).item(0)?.textContent?.trim()?.ifBlank { null }

        val spine = opf.getElementsByTagNameNS("*", "spine").item(0) as? Element
        val items = opf.getElementsByTagNameNS("*", "item").let { list -> (0 until list.length).map { list.item(it) as Element } }

        // EPUB 3 marks the cover item; EPUB 2 points at it from a <meta name="cover">.
        val coverHref = items.firstOrNull { it.getAttribute("properties").split(" ").contains("cover-image") }
            ?.getAttribute("href")
            ?: run {
                val metas = opf.getElementsByTagNameNS("*", "meta")
                val coverId = (0 until metas.length).map { metas.item(it) as Element }
                    .firstOrNull { it.getAttribute("name") == "cover" }?.getAttribute("content")
                items.firstOrNull { it.getAttribute("id") == coverId }?.getAttribute("href")
            }
        val cover = coverHref?.let { href ->
            zip.getEntry(opfDir + href)?.let { entry -> zip.getInputStream(entry).use { ImageIO.read(it) } }
        }

        Metadata(
            title = text("title") ?: file.nameWithoutExtension,
            titleFromFile = text("title") == null,
            author = text("creator"),
            rightToLeft = spine?.getAttribute("page-progression-direction") == "rtl",
            cover = cover,
            subjects = opf.getElementsByTagNameNS("*", "subject").let { list ->
                (0 until list.length).mapNotNull { list.item(it).textContent?.trim()?.ifBlank { null } }
            },
            description = text("description")?.let(::plainText),
            year = yearOf(text("date")),
            series = series(opf)?.first,
            seriesIndex = series(opf)?.second,
        )
    }

    /**
     * The series a book belongs to: EPUB 3's belongs-to-collection (with its position),
     * or Calibre's calibre:series metas, which most EPUB 2 books use.
     */
    private fun series(opf: Element): Pair<String, Double?>? {
        val metas = opf.getElementsByTagNameNS("*", "meta").let { list -> (0 until list.length).map { list.item(it) as Element } }
        metas.firstOrNull { it.getAttribute("property") == "belongs-to-collection" && it.textContent.isNotBlank() }?.let { collection ->
            val ref = "#" + collection.getAttribute("id")
            val position = metas.firstOrNull { it.getAttribute("refines") == ref && it.getAttribute("property") == "group-position" }
                ?.textContent?.trim()?.toDoubleOrNull()
            return collection.textContent.trim() to position
        }
        val name = metas.firstOrNull { it.getAttribute("name") == "calibre:series" }?.getAttribute("content")?.trim()?.ifBlank { null }
            ?: return null
        val index = metas.firstOrNull { it.getAttribute("name") == "calibre:series_index" }?.getAttribute("content")?.trim()?.toDoubleOrNull()
        return name to index
    }

    /** Descriptions often carry HTML; the library shows plain text. */
    private fun plainText(html: String): String? =
        html.replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifBlank { null }

    private fun readPdf(file: Path): Metadata = Loader.loadPDF(file.toFile()).use { pdf ->
        val cover = if (pdf.numberOfPages > 0) PDFRenderer(pdf).renderImageWithDPI(0, 110f) else null
        Metadata(
            title = pdf.documentInformation?.title?.trim()?.ifBlank { null } ?: file.nameWithoutExtension,
            titleFromFile = pdf.documentInformation?.title.isNullOrBlank(),
            author = pdf.documentInformation?.author?.trim()?.ifBlank { null },
            rightToLeft = false,
            cover = cover,
            subjects = listOfNotNull(pdf.documentInformation?.subject, pdf.documentInformation?.keywords)
                .flatMap { it.split(',', ';') }
                .map(String::trim)
                .filter(String::isNotEmpty),
            // A long Subject is a description (it's where the organizer writes one).
            description = pdf.documentInformation?.subject?.trim()?.takeIf { it.length > 80 },
            year = yearOf(pdf.documentInformation?.getCustomMetadataValue("PublicationYear")),
            series = pdf.documentInformation?.getCustomMetadataValue("Series")?.trim()?.ifBlank { null },
            seriesIndex = pdf.documentInformation?.getCustomMetadataValue("SeriesIndex")?.trim()?.toDoubleOrNull(),
        )
    }

    private fun ZipFile.xml(name: String) = getInputStream(getEntry(name) ?: error("missing $name")).use { stream ->
        parseXml(stream.readBytes()).documentElement
    }

    /** Parses XML from a book file: namespace-aware, and never fetching or expanding anything outside it. */
    fun parseXml(bytes: ByteArray): org.w3c.dom.Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Book files come from anywhere: a doctype is tolerated (some EPUB 2 manifests
            // have one), but nothing outside the book is ever fetched or expanded.
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    /** Scales [image] to fit within [maxPx] on its longer side and encodes it as JPEG. */
    fun jpeg(image: BufferedImage, maxPx: Int): ByteArray {
        val scale = minOf(1.0, maxPx.toDouble() / maxOf(image.width, image.height))
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, w, h, java.awt.Color.WHITE, null)
            dispose()
        }
        return java.io.ByteArrayOutputStream().also { ImageIO.write(scaled, "jpg", it) }.toByteArray()
    }
}
