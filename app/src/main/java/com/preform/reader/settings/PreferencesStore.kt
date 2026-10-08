package com.preform.reader.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** A horizontal direction or side, always as the reader sees it from the front. */
enum class SwipeDirection { LEFT, RIGHT }

/** How the outside cover turns pages while the phone is open. */
enum class CoverControls(val swipe: Boolean, val tap: Boolean) {
    OFF(swipe = false, tap = false),
    SWIPE(swipe = true, tap = false),
    TAP(swipe = false, tap = true),
    SWIPE_AND_TAP(swipe = true, tap = true),
}

/** A neutral page for the app: three light, three dark. */
enum class ThemeBase(val dark: Boolean) {
    MONO(dark = false), PAPER(dark = false), PARCHMENT(dark = false),
    NOIR(dark = true), INK(dark = true), SLATE(dark = true),
}

/** The highlight color, picked separately so any base can wear any color. No orange, by request. */
enum class ThemeAccent { MONO, RED, PINK, YELLOW, GREEN, TEAL, BLUE, INDIGO, VIOLET }

/** The app's look: the library, settings and the reader's controls. */
data class AppTheme(val base: ThemeBase, val accent: ThemeAccent) {
    companion object {
        val DEFAULT = AppTheme(ThemeBase.INK, ThemeAccent.BLUE)
    }
}

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
        val legacy = legacyAppTheme(prefs[APP_THEME])
        AppTheme(
            base = prefs[THEME_BASE]?.let(::themeBaseNamed) ?: legacy.base,
            accent = prefs[THEME_ACCENT]?.let { saved -> ThemeAccent.entries.firstOrNull { it.name == saved } } ?: legacy.accent,
        )
    }

    suspend fun setThemeBase(base: ThemeBase) = store.edit { it[THEME_BASE] = base.name }

    suspend fun setThemeAccent(accent: ThemeAccent) = store.edit { it[THEME_ACCENT] = accent.name }

    /** Day and night themes and when each starts, for switching by time of day. */
    val themeSchedule: Flow<ThemeSchedule> = read { prefs ->
        ThemeSchedule(
            enabled = prefs[SCHEDULE_ON] ?: false,
            day = prefs[DAY_THEME]?.let(::themeBaseNamed)?.takeIf { !it.dark } ?: ThemeSchedule.DEFAULT.day,
            night = prefs[NIGHT_THEME]?.let(::themeBaseNamed)?.takeIf { it.dark } ?: ThemeSchedule.DEFAULT.night,
            dayStartsAt = prefs[DAY_STARTS]?.let(LocalTime::ofSecondOfDay) ?: ThemeSchedule.DEFAULT.dayStartsAt,
            nightStartsAt = prefs[NIGHT_STARTS]?.let(LocalTime::ofSecondOfDay) ?: ThemeSchedule.DEFAULT.nightStartsAt,
        )
    }

    suspend fun updateThemeSchedule(change: (ThemeSchedule) -> ThemeSchedule) {
        val updated = change(themeSchedule.first())
        store.edit {
            it[SCHEDULE_ON] = updated.enabled
            it[DAY_THEME] = updated.day.name
            it[NIGHT_THEME] = updated.night.name
            it[DAY_STARTS] = updated.dayStartsAt.toSecondOfDay().toLong()
            it[NIGHT_STARTS] = updated.nightStartsAt.toSecondOfDay().toLong()
        }
    }

    /**
     * The theme to show right now: the chosen one, or the day or night theme when
     * switching by time of day. Re-checked every minute, so a switch lands within a
     * minute of its time, even mid-book.
     */
    val effectiveTheme: Flow<AppTheme> = combine(appTheme, themeSchedule, minuteTicks()) { chosen, schedule, now ->
        if (schedule.enabled) chosen.copy(base = schedule.baseAt(now)) else chosen
    }.distinctUntilChanged()

    // Book Mode

    /**
     * How the outside cover turns pages while the phone is open. Before this was a
     * choice it was an on/off switch for swiping; switched off still means off, and
     * otherwise it now does both.
     */
    val coverControls: Flow<CoverControls> = read { prefs ->
        coverControlsFrom(prefs[COVER_CONTROLS], legacySwipeEnabled = prefs[BACK_SWIPE_ENABLED])
    }

    suspend fun setCoverControls(controls: CoverControls) = store.edit {
        it[COVER_CONTROLS] = controls.name
        it.remove(BACK_SWIPE_ENABLED)
    }

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
        /** Superseded by THEME_BASE and THEME_ACCENT; read for the carry-over. */
        val APP_THEME = stringPreferencesKey("app_theme")
        val THEME_BASE = stringPreferencesKey("theme_base")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val SCHEDULE_ON = booleanPreferencesKey("theme_schedule_on")
        val DAY_THEME = stringPreferencesKey("theme_day")
        val NIGHT_THEME = stringPreferencesKey("theme_night")
        val DAY_STARTS = longPreferencesKey("theme_day_starts")
        val NIGHT_STARTS = longPreferencesKey("theme_night_starts")
        val BACK_SWIPE_FOR_NEXT = stringPreferencesKey("back_swipe_for_next")
        val COVER_CONTROLS = stringPreferencesKey("cover_controls")

        /** Superseded by COVER_CONTROLS; read once for the carry-over. */
        val BACK_SWIPE_ENABLED = booleanPreferencesKey("back_swipe_enabled")
    }
}

internal fun themeBaseNamed(saved: String): ThemeBase? = ThemeBase.entries.firstOrNull { it.name == saved }

/**
 * Carries over the theme saved before bases and accents were separate: the old
 * light/dark/system choice, or one of the named color themes, each mapped to the
 * nearest neutral base wearing its color.
 */
internal fun legacyAppTheme(saved: String?): AppTheme = when (saved) {
    "LIGHT" -> AppTheme(ThemeBase.PAPER, ThemeAccent.BLUE)
    "PAPER", "OCEAN" -> AppTheme(ThemeBase.PAPER, ThemeAccent.BLUE)
    "MONO" -> AppTheme(ThemeBase.MONO, ThemeAccent.MONO)
    "NOIR" -> AppTheme(ThemeBase.NOIR, ThemeAccent.MONO)
    "PARCHMENT" -> AppTheme(ThemeBase.PARCHMENT, ThemeAccent.GREEN)
    "CRIMSON", "EMBER" -> AppTheme(ThemeBase.INK, ThemeAccent.RED)
    "ROSE" -> AppTheme(ThemeBase.PAPER, ThemeAccent.PINK)
    "SUNFLOWER" -> AppTheme(ThemeBase.PAPER, ThemeAccent.YELLOW)
    "MATCHA" -> AppTheme(ThemeBase.PAPER, ThemeAccent.GREEN)
    "FOREST" -> AppTheme(ThemeBase.INK, ThemeAccent.GREEN)
    "SLATE" -> AppTheme(ThemeBase.SLATE, ThemeAccent.TEAL)
    "MIDNIGHT" -> AppTheme(ThemeBase.SLATE, ThemeAccent.BLUE)
    "INDIGO" -> AppTheme(ThemeBase.INK, ThemeAccent.INDIGO)
    "LAVENDER" -> AppTheme(ThemeBase.PAPER, ThemeAccent.VIOLET)
    "PLUM" -> AppTheme(ThemeBase.INK, ThemeAccent.VIOLET)
    else -> AppTheme.DEFAULT // never set, the old system/dark choices, or anything unknown
}

/** Reads the saved cover controls, or carries over the on/off swipe switch that preceded them. */
internal fun coverControlsFrom(saved: String?, legacySwipeEnabled: Boolean?): CoverControls =
    saved?.let { name -> CoverControls.entries.firstOrNull { it.name == name } }
        ?: if (legacySwipeEnabled == false) CoverControls.OFF else CoverControls.SWIPE_AND_TAP

/** Switching between a light and a dark base by time of day; the accent stays. */
data class ThemeSchedule(
    val enabled: Boolean,
    val day: ThemeBase,
    val night: ThemeBase,
    val dayStartsAt: LocalTime,
    val nightStartsAt: LocalTime,
) {
    /**
     * Day runs from [dayStartsAt] up to [nightStartsAt]. Either order works: a day
     * that starts after night does (say 9 PM to 6 AM) wraps through midnight.
     */
    fun baseAt(time: LocalTime): ThemeBase {
        val isDay = if (dayStartsAt <= nightStartsAt) {
            time >= dayStartsAt && time < nightStartsAt
        } else {
            time >= dayStartsAt || time < nightStartsAt
        }
        return if (isDay) day else night
    }

    companion object {
        val DEFAULT = ThemeSchedule(
            enabled = false,
            day = ThemeBase.PAPER,
            night = ThemeBase.INK,
            dayStartsAt = LocalTime.of(7, 0),
            nightStartsAt = LocalTime.of(19, 0),
        )
    }
}

/** The time of day now, and again at the turn of every minute. */
private fun minuteTicks(): Flow<LocalTime> = flow {
    while (true) {
        val now = LocalTime.now()
        emit(now.withSecond(0).withNano(0))
        delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000))
    }
}
