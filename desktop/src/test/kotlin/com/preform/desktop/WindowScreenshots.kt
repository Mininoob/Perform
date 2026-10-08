package com.preform.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.preform.desktop.ui.CoverCache
import com.preform.desktop.ui.LibraryWindow
import com.preform.desktop.ui.Place
import com.preform.desktop.ui.PreformTheme
import com.preform.desktop.ui.WindowState
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Renders the window off-screen to PNGs for review, from a copy of a real data folder:
 * `./gradlew :desktop:test --tests '*WindowScreenshots*' -Dscreenshots.data=<data dir>`.
 * Skipped in normal test runs.
 */
class WindowScreenshots {

    @Test
    fun render() {
        val source = System.getProperty("screenshots.data")
        assumeTrue("Set screenshots.data to render", !source.isNullOrBlank())
        val data = Files.createTempDirectory("preform-shots")
        Path.of(source!!).toFile().copyRecursively(data.toFile())
        val out = Path.of("build/screenshots").createDirectories()
        // Point the copy at a test library, never the real one named in its settings.
        System.getProperty("screenshots.library")?.takeIf { it.isNotBlank() }?.let { library ->
            val paths = AppPaths(data)
            AppConfig.loadOrCreate(paths, Platform.current).copy(libraryRoot = library, lookUpOnline = false, writeIntoFiles = false).save(paths)
        }

        val service = LibraryService(AppPaths(data))
        runCatching { service.library.scan() }
        service.refreshLists()
        val covers = CoverCache(service.library)
        val books = service.books.value

        fun shot(name: String, base: ThemeBase, accent: ThemeAccent, setup: WindowState.() -> Unit) {
            val state = WindowState().apply(setup)
            val scene = ImageComposeScene(1240, 820, Density(1f)) {
                PreformTheme(base, accent) { LibraryWindow(service, covers, state) }
            }
            // Covers load on a background thread; give them a few frames.
            repeat(12) { i ->
                scene.render(i * 100_000_000L)
                Thread.sleep(120)
            }
            val png = scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            out.resolve("$name.png").writeBytes(png)
            scene.close()
        }

        val dune = books.firstOrNull { it.catalog.title == "Dune" }?.catalog?.contentId
        shot("all-ink", ThemeBase.INK, ThemeAccent.BLUE) { dune?.let { selection = setOf(it) } }
        shot("folder-paper", ThemeBase.PAPER, ThemeAccent.GREEN) { place = Place.Folder("Fiction") }
        shot("phones-slate", ThemeBase.SLATE, ThemeAccent.VIOLET) { place = Place.Devices }
        shot("settings-mono", ThemeBase.MONO, ThemeAccent.RED) { place = Place.Settings }
        shot("organize-ink", ThemeBase.INK, ThemeAccent.BLUE) {
            allBooks = books
            visible = books
            dialog = com.preform.desktop.ui.Dialog.Organize
        }
        books.firstOrNull { it.catalog.title == "Dune" }?.let { book ->
            shot("edit-paper", ThemeBase.PAPER, ThemeAccent.TEAL) { dialog = com.preform.desktop.ui.Dialog.Edit(book) }
        }
        val uncategorized = books.filter { it.catalog.genres.isEmpty() }
        shot("uncategorized-ink", ThemeBase.INK, ThemeAccent.BLUE) { place = Place.Genre(com.preform.sync.Genres.UNCATEGORIZED) }
        if (uncategorized.isNotEmpty()) {
            shot("sort-ink", ThemeBase.INK, ThemeAccent.BLUE) { dialog = com.preform.desktop.ui.Dialog.Sort(uncategorized) }
            shot("sort-paper", ThemeBase.PAPER, ThemeAccent.GREEN) { dialog = com.preform.desktop.ui.Dialog.Sort(uncategorized) }
        }
        if (uncategorized.isNotEmpty()) {
            // On the copy only: a genre of the library's own, still empty.
            service.addGenre("To Read Next")
            shot("autogenre-ink", ThemeBase.INK, ThemeAccent.BLUE) {
                place = Place.Genre(com.preform.sync.Genres.UNCATEGORIZED)
                dialog = com.preform.desktop.ui.Dialog.AutoGenre(uncategorized)
            }
            shot("customgenre-paper", ThemeBase.PAPER, ThemeAccent.GREEN) { place = Place.Genre(com.preform.sync.Genres.UNCATEGORIZED) }
        }
        service.stop()
    }
}
