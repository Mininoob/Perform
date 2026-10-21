package com.perform.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Concept frames for the two leading visual directions, phone and desktop, drawn with
 * the apps' own toolkit from a real library's covers. Not product code: a picture to
 * choose from. `-Dscreenshots.data=<data dir>`; writes build/frames/.
 */
class DirectionFrames {

    private val sitka = FontFamily(
        Font(File("C:/Windows/Fonts/SitkaVF.ttf"), FontWeight.Normal),
        Font(File("C:/Windows/Fonts/SitkaVF-Italic.ttf"), FontWeight.Normal, FontStyle.Italic),
    )
    private val din = FontFamily(Font(File("C:/Windows/Fonts/bahnschrift.ttf")))

    /** A book on screen: a real cover, or the facts to typeset one. */
    private data class Book(
        val title: String,
        val author: String,
        val cover: ImageBitmap?,
        val genre: String,
        val year: Int?,
        val progress: Float = 0f,
    )

    private lateinit var books: List<Book>

    @Test
    fun render() {
        val source = System.getProperty("screenshots.data")
        assumeTrue(!source.isNullOrBlank())
        val data = Files.createTempDirectory("perform-frames")
        Path.of(source!!).toFile().copyRecursively(data.toFile())
        val service = LibraryService(AppPaths(data))
        service.refreshLists()
        val real = service.books.value.map { b ->
            Book(
                title = b.catalog.title,
                author = b.catalog.author ?: "Unknown author",
                cover = service.library.cover(b.catalog.contentId, Library.CoverSize.FULL)
                    ?.let { SkiaImage.makeFromEncoded(it.toFile().readBytes()).toComposeImageBitmap() },
                genre = b.catalog.genres.firstOrNull() ?: "Fiction",
                year = b.catalog.year,
            )
        }.filter { it.cover != null && it.title != "Sample Manual" }
        // Classic titles without covers, to show how the series sets them.
        val typeset = listOf(
            Book("Middlemarch", "George Eliot", null, "Classics", 1871),
            Book("Meditations", "Marcus Aurelius", null, "Philosophy", 180),
            Book("The Odyssey", "Homer", null, "Classics", null),
            Book("A Room of One's Own", "Virginia Woolf", null, "Nonfiction", 1929),
            Book("Walden", "Henry David Thoreau", null, "Nature", 1854),
            Book("The Art of War", "Sun Tzu", null, "Philosophy", null),
        )
        books = real.map { if (it.title == "Dune") it.copy(progress = 0.38f) else it } + typeset
        service.stop()

        val out = Path.of("build/frames").createDirectories()
        fun shot(name: String, w: Int, h: Int, density: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
            val scene = ImageComposeScene((w * density).toInt(), (h * density).toInt(), Density(density), content = content)
            repeat(3) { scene.render(it * 16_000_000L) }
            out.resolve("$name.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
        shot("a-great-ideas-phone", 412, 915, 2f) { GreatIdeasPhone() }
        shot("a-great-ideas-desktop", 1280, 820, 1f) { GreatIdeasDesktop() }
        shot("b-pelican-phone", 412, 915, 2f) { PelicanPhone() }
        shot("b-pelican-desktop", 1280, 820, 1f) { PelicanDesktop() }
    }

    // ---------------------------------------------------------------- shared

    private val inHand get() = books.first { it.title == "Dune" }

    /** Two-color grounds for typeset covers, chosen by genre: book cloth, not tints. */
    private fun typeColors(genre: String): Pair<Color, Color> = when (genre) {
        "Classics" -> Color(0xFF7A1F2B) to Color(0xFFF1E6D4)
        "Philosophy" -> Color(0xFF1E3A5F) to Color(0xFFE9E4D8)
        "Nonfiction" -> Color(0xFF2E5E4E) to Color(0xFFEDE8DC)
        "Nature" -> Color(0xFFD9C27A) to Color(0xFF1B1B1B)
        else -> Color(0xFF222222) to Color(0xFFEDE8DC)
    }

    @androidx.compose.runtime.Composable
    private fun Cover(book: Book, modifier: Modifier, radius: Dp = 3.dp, big: Boolean = false) {
        val shape = RoundedCornerShape(radius)
        Box(modifier.aspectRatio(2f / 3f).shadow(if (big) 14.dp else 4.dp, shape).clip(shape)) {
            if (book.cover != null) {
                Image(book.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                TypeCover(book, big)
            }
        }
    }

    /** The series' typeset cover: two colors, the title as the image. */
    @androidx.compose.runtime.Composable
    private fun BoxScope.TypeCover(book: Book, big: Boolean) = BoxWithConstraints(Modifier.fillMaxSize()) {
        val (ground, ink) = typeColors(book.genre)
        // Lettering scales with the cover, so a thumbnail is the same design as a jacket.
        val width = maxWidth.value
        val scale = width / 92f
        Column(Modifier.fillMaxSize().background(ground).padding((10 * scale).dp)) {
            Text(
                book.author.uppercase(),
                color = ink.copy(alpha = 0.85f),
                fontFamily = din,
                fontSize = (6.5 * scale).sp,
                letterSpacing = (1.4 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height((5 * scale).dp))
            Box(Modifier.fillMaxWidth().height(0.75.dp).background(ink.copy(alpha = 0.6f)))
            Spacer(Modifier.weight(1f))
            // Never break a word: the longest word sets the largest size that fits.
            val longest = book.title.split(' ').maxOf { it.length }
            val fits = (width - 20 * scale) / (longest * 0.62f)
            val size = minOf(15 * scale, fits)
            Text(
                book.title,
                color = ink,
                fontFamily = sitka,
                fontStyle = FontStyle.Italic,
                fontSize = size.sp,
                lineHeight = (size * 1.08f).sp,
            )
            Spacer(Modifier.height((8 * scale).dp))
            Text("PERFORM", color = ink.copy(alpha = 0.55f), fontFamily = din, fontSize = (5.5 * scale).sp, letterSpacing = (2 * scale).sp)
        }
    }

    /** Reading progress as a single rule: the line is the state, no badge. */
    @androidx.compose.runtime.Composable
    private fun ProgressRule(progress: Float, ink: Color, accent: Color, modifier: Modifier = Modifier) {
        Box(modifier.height(2.dp).background(ink.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(accent))
        }
    }

    @androidx.compose.runtime.Composable
    private fun StatusBar(ink: Color) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", color = ink, fontFamily = din, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 22.dp, height = 10.dp).border(1.dp, ink, RoundedCornerShape(2.dp)).padding(1.5.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(ink))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Label(text: String, color: Color, size: TextUnit = 10.sp) =
        Text(text.uppercase(), color = color, fontFamily = din, fontSize = size, letterSpacing = 1.6.sp)

    // ---------------------------------------------------------- A: Great Ideas

    private val paper = Color(0xFFF3F1EC)
    private val ink = Color(0xFF161616)
    private val muted = Color(0xFF7A776F)
    private val accentA = Color(0xFF2457C5)

    @androidx.compose.runtime.Composable
    private fun GreatIdeasPhone() {
        Column(Modifier.fillMaxSize().background(paper).verticalScroll(rememberScrollState())) {
            StatusBar(ink)
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", color = ink, fontFamily = sitka, fontSize = 30.sp)
                Spacer(Modifier.weight(1f))
                Label("Search", muted, 10.sp)
            }
            // The book in hand: largest on the size ramp.
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
                Cover(inHand, Modifier.width(132.dp), big = true)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Label("Reading", accentA)
                    Spacer(Modifier.height(10.dp))
                    Text(inHand.title, color = ink, fontFamily = sitka, fontSize = 28.sp, lineHeight = 30.sp)
                    Text(inHand.author, color = muted, fontFamily = sitka, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    Spacer(Modifier.height(18.dp))
                    ProgressRule(inHand.progress, ink, accentA, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("38%  ·  Book One, Chapter 12", color = muted, fontFamily = din, fontSize = 11.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(ink).padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text("Continue", color = paper, fontFamily = din, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("Shelves", "Genres", "All").forEachIndexed { i, t ->
                    Column {
                        Text(t, color = if (i == 0) ink else muted, fontFamily = din, fontSize = 14.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(if (i == 0) 24.dp else 0.dp).height(2.dp).background(ink))
                    }
                }
            }
            Shelf("Fiction", books.filter { it.cover != null }.take(4) + books.first { it.title == "Middlemarch" })
            Shelf("Classics & ideas", books.filter { it.cover == null })
            Shelf("Nonfiction", books.filter { it.title in setOf("Cosmos", "A Room of One's Own", "Walden") })
        }
    }

    @androidx.compose.runtime.Composable
    private fun Shelf(name: String, list: List<Book>) {
        Column(Modifier.padding(top = 26.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.Bottom) {
                Text(name, color = ink, fontFamily = sitka, fontSize = 19.sp)
                Spacer(Modifier.width(8.dp))
                Text("${list.size}", color = muted, fontFamily = din, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Label("See all", muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { b ->
                    Column(Modifier.width(92.dp)) {
                        Cover(b, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(b.title, color = ink, fontFamily = sitka, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(b.author.substringAfterLast(' '), b.year?.toString()).joinToString(" · "), color = muted, fontFamily = din, fontSize = 9.5.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun GreatIdeasDesktop() {
        val dark = Color(0xFF131416)
        val raised = Color(0xFF1B1C1F)
        val text = Color(0xFFE6E3DC)
        val dim = Color(0xFF8F8C85)
        val accent = Color(0xFF8AB4F8)
        Row(Modifier.fillMaxSize().background(dark)) {
            // Sidebar: native in structure, the series' type in voice.
            Column(Modifier.width(232.dp).fillMaxHeight().background(raised).padding(horizontal = 22.dp, vertical = 24.dp)) {
                Text("Perform Library", color = text, fontFamily = sitka, fontSize = 20.sp)
                Spacer(Modifier.height(28.dp))
                Label("Library", dim)
                Spacer(Modifier.height(10.dp))
                SideItem("All books", "14", text, dim, selected = true, accent = accent)
                SideItem("Reading", "1", text, dim)
                SideItem("Recently added", null, text, dim)
                Spacer(Modifier.height(22.dp))
                Label("Shelves", dim)
                Spacer(Modifier.height(10.dp))
                listOf("Classics" to "3", "Fiction" to "4", "Nonfiction" to "2", "Philosophy" to "2").forEach { (n, c) -> SideItem(n, c, text, dim) }
                Spacer(Modifier.height(22.dp))
                Label("Checks", dim)
                Spacer(Modifier.height(10.dp))
                SideItem("No cover", "6", text, dim)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFF8FD18A), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("Sharing with 1 phone", color = dim, fontFamily = din, fontSize = 11.sp)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 36.dp, vertical = 28.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("All books", color = text, fontFamily = sitka, fontSize = 34.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("14", color = dim, fontFamily = din, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Text("Organize", color = accent, fontFamily = din, fontSize = 13.sp)
                    Spacer(Modifier.width(22.dp))
                    Text("Add books", color = text, fontFamily = din, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))
                val grid = books.take(12)
                grid.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(bottom = 22.dp)) {
                        row.forEach { b ->
                            Column(Modifier.width(118.dp)) {
                                Box {
                                    Cover(b, Modifier.fillMaxWidth())
                                    if (b == inHand) {
                                        Box(Modifier.matchParentSize().border(2.dp, accent, RoundedCornerShape(3.dp)))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(b.title, color = text, fontFamily = sitka, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(b.author, b.year?.toString()).joinToString(" · "), color = dim, fontFamily = din, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (b.progress > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    ProgressRule(b.progress, text, accent, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            // Details: the book's full card, the third fold of the same object.
            Column(Modifier.width(300.dp).fillMaxHeight().background(raised).padding(28.dp)) {
                Cover(inHand, Modifier.width(170.dp).align(Alignment.CenterHorizontally), big = true)
                Spacer(Modifier.height(24.dp))
                Text(inHand.title, color = text, fontFamily = sitka, fontSize = 30.sp)
                Text("${inHand.author}  ·  ${inHand.year ?: ""}", color = dim, fontFamily = sitka, fontStyle = FontStyle.Italic, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                ProgressRule(0.38f, text, accent, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("38% on Z Fold", color = dim, fontFamily = din, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Set on the desert planet Arrakis, Dune is the story of the boy Paul Atreides, heir to a noble family tasked with ruling an inhospitable world.",
                    color = text.copy(alpha = 0.85f), fontFamily = sitka, fontSize = 13.sp, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                Label("Science fiction · Fantasy", dim)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SideItem(name: String, count: String?, text: Color, dim: Color, selected: Boolean = false, accent: Color = text) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(2.dp).height(14.dp).background(if (selected) accent else Color.Transparent))
            Spacer(Modifier.width(10.dp))
            Text(name, color = if (selected) text else text.copy(alpha = 0.8f), fontFamily = din, fontSize = 13.sp, modifier = Modifier.weight(1f))
            count?.let { Text(it, color = dim, fontFamily = din, fontSize = 11.sp) }
        }
    }

    // ------------------------------------------------------------- B: Pelican

    private val fields = listOf(
        "Fiction" to Color(0xFF0E7C86),
        "Classics" to Color(0xFFB23A48),
        "Ideas" to Color(0xFF2F4A9E),
    )

    @androidx.compose.runtime.Composable
    private fun PelicanPhone() {
        val ground = Color(0xFFF4F1EC)
        Column(Modifier.fillMaxSize().background(ground).verticalScroll(rememberScrollState())) {
            StatusBar(ink)
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PERFORM", color = ink, fontFamily = din, fontSize = 18.sp, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                Text("Shelves  Genres  All", color = muted, fontFamily = din, fontSize = 12.sp)
            }
            // The first field carries the book in hand.
            Column(Modifier.fillMaxWidth().background(fields[0].second).padding(22.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Fiction", color = Color.White, fontFamily = din, fontSize = 34.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("4 books", color = Color.White.copy(alpha = 0.7f), fontFamily = din, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
                Row {
                    Cover(inHand, Modifier.width(118.dp), big = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.padding(top = 4.dp)) {
                        Text("CONTINUE", color = Color.White.copy(alpha = 0.75f), fontFamily = din, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(inHand.title, color = Color.White, fontFamily = din, fontSize = 30.sp)
                        Text(inHand.author, color = Color.White.copy(alpha = 0.8f), fontFamily = din, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        ProgressRule(0.38f, Color.White, Color.White, Modifier.width(160.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("38%", color = Color.White.copy(alpha = 0.8f), fontFamily = din, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    books.filter { it.cover != null && it != inHand }.take(4).forEach { Cover(it, Modifier.width(78.dp)) }
                }
            }
            fields.drop(1).forEachIndexed { i, (name, color) ->
                val list = if (i == 0) books.filter { it.genre == "Classics" } + books.filter { it.title == "Emma" } else books.filter { it.cover == null && it.genre != "Classics" }
                Column(Modifier.fillMaxWidth().background(color).padding(horizontal = 22.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(name, color = Color.White, fontFamily = din, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("${list.size} books", color = Color.White.copy(alpha = 0.7f), fontFamily = din, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.take(4).forEach { Cover(it, Modifier.width(78.dp)) }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PelicanDesktop() {
        val ground = Color(0xFFF4F1EC)
        val (shelf, color) = fields[0]
        Row(Modifier.fillMaxSize().background(ground)) {
            Column(Modifier.width(232.dp).fillMaxHeight().padding(horizontal = 22.dp, vertical = 24.dp)) {
                Text("PERFORM LIBRARY", color = ink, fontFamily = din, fontSize = 14.sp, letterSpacing = 2.5.sp)
                Spacer(Modifier.height(30.dp))
                Label("Library", muted)
                Spacer(Modifier.height(10.dp))
                SideItem("All books", "14", ink, muted)
                SideItem("Recently added", null, ink, muted)
                Spacer(Modifier.height(22.dp))
                Label("Shelves", muted)
                Spacer(Modifier.height(10.dp))
                fields.forEach { (n, c) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(c))
                        Spacer(Modifier.width(10.dp))
                        Text(n, color = ink, fontFamily = din, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (n == "Fiction") "4" else "3", color = muted, fontFamily = din, fontSize = 11.sp)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                // The selected shelf's field frames the work area.
                Column(Modifier.fillMaxWidth().background(color).padding(horizontal = 36.dp, vertical = 26.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(shelf, color = Color.White, fontFamily = din, fontSize = 46.sp)
                        Spacer(Modifier.width(14.dp))
                        Text("4 books · 1 reading", color = Color.White.copy(alpha = 0.75f), fontFamily = din, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                        Spacer(Modifier.weight(1f))
                        Text("ORGANIZE", color = Color.White, fontFamily = din, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
                }
                Row(Modifier.padding(horizontal = 36.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    (listOf(inHand) + books.filter { it.cover != null && it != inHand }.take(3)).forEach { b ->
                        Column(Modifier.width(150.dp)) {
                            Cover(b, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Text(b.title, color = ink, fontFamily = din, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.author, color = muted, fontFamily = din, fontSize = 11.sp, maxLines = 1)
                            if (b.progress > 0) {
                                Spacer(Modifier.height(6.dp))
                                ProgressRule(b.progress, ink, color, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp).height(1.dp).background(ink.copy(alpha = 0.1f)))
                Row(Modifier.padding(horizontal = 36.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(fields[1].second))
                    Spacer(Modifier.width(10.dp))
                    Text("Classics", color = ink, fontFamily = din, fontSize = 20.sp)
                    Spacer(Modifier.width(18.dp))
                    Box(Modifier.size(14.dp).background(fields[2].second))
                    Spacer(Modifier.width(10.dp))
                    Text("Ideas", color = ink, fontFamily = din, fontSize = 20.sp)
                }
            }
        }
    }
}
