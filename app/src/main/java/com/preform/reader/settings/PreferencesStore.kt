package com.preform.reader.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A horizontal swipe direction, always as the reader sees it from the front. */
enum class SwipeDirection { LEFT, RIGHT }

enum class AppTheme { SYSTEM, LIGHT, DARK }

// The file name predates this class (it was ReaderSettings); keeping it means settings
// saved by earlier builds carry over.
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

/** App-wide settings, persisted across launches. Per-book overrides live in the library DB. */
class PreferencesStore(context: Context) {

    private val store = context.applicationContext.settingsStore

    private fun <T> read(transform: (Preferences) -> T): Flow<T> =
        store.data.map(transform).distinctUntilChanged()

    // Reading

    val globalReading: Flow<ReadingPreferences> = read { ReadingPreferences.fromJson(it[GLOBAL_READING]) }

    suspend fun updateGlobalReading(change: (ReadingPreferences) -> ReadingPreferences) {
        store.edit { it[GLOBAL_READING] = change(ReadingPreferences.fromJson(it[GLOBAL_READING])).toJson() }
    }

    suspend fun resetGlobalReading() {
        store.edit { it.remove(GLOBAL_READING) }
    }

    /** Off by default: holding the screen awake is the expensive part (measured). */
    val keepScreenOn: Flow<Boolean> = read { it[KEEP_SCREEN_ON] ?: false }

    suspend fun setKeepScreenOn(value: Boolean) = store.edit { it[KEEP_SCREEN_ON] = value }

    // Library

    /** The main library folder's SAF tree URI, or null before one is chosen. */
    val libraryFolder: Flow<String?> = read { it[LIBRARY_FOLDER] }

    suspend fun libraryFolderNow(): String? = libraryFolder.first()

    suspend fun setLibraryFolder(treeUri: String?) = store.edit {
        if (treeUri == null) it.remove(LIBRARY_FOLDER) else it[LIBRARY_FOLDER] = treeUri
    }

    // Appearance

    val appTheme: Flow<AppTheme> = read { prefs ->
        prefs[APP_THEME]?.let { saved -> AppTheme.entries.firstOrNull { it.name == saved } } ?: AppTheme.SYSTEM
    }

    suspend fun setAppTheme(theme: AppTheme) = store.edit { it[APP_THEME] = theme.name }

    // Book Mode

    /** Remembered, so turning Book Mode on once sticks rather than resetting every launch. */
    val bookMode: Flow<Boolean> = read { it[BOOK_MODE] ?: false }

    suspend fun setBookMode(value: Boolean) = store.edit { it[BOOK_MODE] = value }

    /** Whether swiping the back cover turns pages at all. On by default. */
    val backSwipeEnabled: Flow<Boolean> = read { it[BACK_SWIPE_ENABLED] ?: true }

    suspend fun setBackSwipeEnabled(enabled: Boolean) = store.edit { it[BACK_SWIPE_ENABLED] = enabled }

    /**
     * Which way to swipe on the back of the phone to turn to the next page.
     *
     * Defaults to LEFT, matching a swipe on the front: flicking the page away from you.
     */
    val backSwipeForNext: Flow<SwipeDirection> = read { prefs ->
        prefs[BACK_SWIPE_FOR_NEXT]?.let { saved -> SwipeDirection.entries.firstOrNull { it.name == saved } }
            ?: SwipeDirection.LEFT
    }

    suspend fun setBackSwipeForNext(direction: SwipeDirection) =
        store.edit { it[BACK_SWIPE_FOR_NEXT] = direction.name }

    private companion object {
        val GLOBAL_READING = stringPreferencesKey("global_reading")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LIBRARY_FOLDER = stringPreferencesKey("library_folder")
        val APP_THEME = stringPreferencesKey("app_theme")
        val BOOK_MODE = booleanPreferencesKey("book_mode")
        val BACK_SWIPE_FOR_NEXT = stringPreferencesKey("back_swipe_for_next")
        val BACK_SWIPE_ENABLED = booleanPreferencesKey("back_swipe_enabled")
    }
}
