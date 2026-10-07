package com.preform.reader.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A horizontal swipe direction, always as the reader sees it from the front. */
enum class SwipeDirection { LEFT, RIGHT }

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

/** Reader preferences, persisted across launches. */
class ReaderSettings(context: Context) {

    private val store = context.applicationContext.settingsStore

    /**
     * Which way to swipe on the back of the phone to turn to the next page.
     *
     * Defaults to LEFT, matching a swipe on the front: flicking the page away from you.
     */
    val backSwipeForNext: Flow<SwipeDirection> = store.data.map { prefs ->
        prefs[BACK_SWIPE_FOR_NEXT]
            ?.let { saved -> SwipeDirection.entries.firstOrNull { it.name == saved } }
            ?: SwipeDirection.LEFT
    }

    suspend fun setBackSwipeForNext(direction: SwipeDirection) {
        store.edit { it[BACK_SWIPE_FOR_NEXT] = direction.name }
    }

    /** Whether swiping the back cover turns pages at all. On by default. */
    val backSwipeEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[BACK_SWIPE_ENABLED] ?: true
    }

    suspend fun setBackSwipeEnabled(enabled: Boolean) {
        store.edit { it[BACK_SWIPE_ENABLED] = enabled }
    }

    private companion object {
        val BACK_SWIPE_FOR_NEXT = stringPreferencesKey("back_swipe_for_next")
        val BACK_SWIPE_ENABLED = booleanPreferencesKey("back_swipe_enabled")
    }
}
