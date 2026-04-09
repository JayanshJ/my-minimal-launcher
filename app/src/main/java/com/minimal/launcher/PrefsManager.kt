package com.minimal.launcher

import android.content.Context
import android.graphics.Typeface

/**
 * Single source of truth for all persisted launcher state.
 */
class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── Clock format ──────────────────────────────────────────────────────────

    var is24Hour: Boolean
        get() = prefs.getBoolean(KEY_24H, true)
        set(value) { prefs.edit().putBoolean(KEY_24H, value).apply() }

    // ── Display mode: "list" | "grid" ─────────────────────────────────────────

    var displayMode: String
        get() = prefs.getString(KEY_DISPLAY_MODE, "list") ?: "list"
        set(value) { prefs.edit().putString(KEY_DISPLAY_MODE, value).apply() }

    // ── Font size: "small" | "medium" | "large" ───────────────────────────────

    var fontSize: String
        get() = prefs.getString(KEY_FONT_SIZE, "medium") ?: "medium"
        set(value) { prefs.edit().putString(KEY_FONT_SIZE, value).apply() }

    fun fontSizeSp(): Float = when (fontSize) {
        "small"  -> 14f
        "large"  -> 21f
        else     -> 17f
    }

    // ── Auto-show keyboard when drawer opens ──────────────────────────────────

    var autoKeyboard: Boolean
        get() = prefs.getBoolean(KEY_AUTO_KEYBOARD, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_KEYBOARD, value).apply() }

    // ── Pomodoro timer visibility ─────────────────────────────────────────────

    var showTimer: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIMER, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_TIMER, value).apply() }

    // ── Temperature unit: "celsius" | "fahrenheit" ────────────────────────────

    var tempUnit: String
        get() = prefs.getString(KEY_TEMP_UNIT, "celsius") ?: "celsius"
        set(value) { prefs.edit().putString(KEY_TEMP_UNIT, value).apply() }

    // ── Gesture assignments (package names, empty = no gesture) ───────────────

    var gestureLeftPkg: String
        get() = prefs.getString(KEY_GESTURE_LEFT, "") ?: ""
        set(value) { prefs.edit().putString(KEY_GESTURE_LEFT, value).apply() }

    var gestureRightPkg: String
        get() = prefs.getString(KEY_GESTURE_RIGHT, "") ?: ""
        set(value) { prefs.edit().putString(KEY_GESTURE_RIGHT, value).apply() }

    // ── Hidden apps ───────────────────────────────────────────────────────────

    fun getHiddenPackages(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    fun hideApp(packageName: String) {
        val s = getHiddenPackages().toMutableSet().also { it.add(packageName) }
        prefs.edit().putStringSet(KEY_HIDDEN, s).apply()
    }

    fun unhideApp(packageName: String) {
        val s = getHiddenPackages().toMutableSet().also { it.remove(packageName) }
        prefs.edit().putStringSet(KEY_HIDDEN, s).apply()
    }

    // ── Blocked (distraction) apps ────────────────────────────────────────────

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()

    fun isBlocked(packageName: String): Boolean = packageName in getBlockedPackages()

    fun blockApp(packageName: String) {
        val s = getBlockedPackages().toMutableSet().also { it.add(packageName) }
        prefs.edit().putStringSet(KEY_BLOCKED, s).apply()
    }

    fun unblockApp(packageName: String) {
        val s = getBlockedPackages().toMutableSet().also { it.remove(packageName) }
        prefs.edit().putStringSet(KEY_BLOCKED, s).apply()
    }

    // ── Wind-down (hard-block after a set time) ───────────────────────────────

    var windDownEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIND_DOWN_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_WIND_DOWN_ENABLED, value).apply() }

    // Stored as "HH:mm", e.g. "22:00"
    var windDownTime: String
        get() = prefs.getString(KEY_WIND_DOWN_TIME, "22:00") ?: "22:00"
        set(value) { prefs.edit().putString(KEY_WIND_DOWN_TIME, value).apply() }

    // Active from wind-down time until 9am (spans midnight)
    fun isWindDownActive(): Boolean {
        if (!windDownEnabled) return false
        val parts   = windDownTime.split(":").mapNotNull { it.toIntOrNull() }
        val windMin = (parts.getOrNull(0) ?: 22) * 60 + (parts.getOrNull(1) ?: 0)
        val cal     = java.util.Calendar.getInstance()
        val nowMin  = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return nowMin >= windMin || nowMin < 9 * 60
    }

    // ── Pinned apps ───────────────────────────────────────────────────────────

    fun getPinnedPackages(): Set<String> =
        prefs.getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()

    fun isPinned(packageName: String): Boolean = packageName in getPinnedPackages()

    fun togglePin(packageName: String) {
        val s = getPinnedPackages().toMutableSet()
        if (packageName in s) s.remove(packageName) else s.add(packageName)
        prefs.edit().putStringSet(KEY_PINNED, s).apply()
    }

    // ── UI font family: "sans-serif" | "serif" | "mono" ──────────────────────

    var launcherFont: String
        get() = prefs.getString(KEY_LAUNCHER_FONT, "sans-serif") ?: "sans-serif"
        set(value) { prefs.edit().putString(KEY_LAUNCHER_FONT, value).apply() }

    // ── UI font weight: "thin" | "light" | "regular" | "medium" ──────────────
    // Only meaningful when launcherFont == "sans-serif"; serif/mono use NORMAL.

    var launcherWeight: String
        get() = prefs.getString(KEY_LAUNCHER_WEIGHT, "light") ?: "light"
        set(value) { prefs.edit().putString(KEY_LAUNCHER_WEIGHT, value).apply() }

    fun launcherTypeface(): Typeface {
        val family = when (launcherFont) {
            "serif" -> "serif"
            "mono"  -> "monospace"
            else    -> when (launcherWeight) {
                "thin"   -> "sans-serif-thin"
                "regular"-> "sans-serif"
                "medium" -> "sans-serif-medium"
                else     -> "sans-serif-light"   // default
            }
        }
        return Typeface.create(family, Typeface.NORMAL)
    }

    // ── Clock size: "small" | "medium" | "large" ─────────────────────────────

    var clockSize: String
        get() = prefs.getString(KEY_CLOCK_SIZE, "medium") ?: "medium"
        set(value) { prefs.edit().putString(KEY_CLOCK_SIZE, value).apply() }

    fun clockSizeSp(): Float = when (clockSize) {
        "small" -> 56f
        "large" -> 88f
        else    -> 72f
    }

    // ── Clock weight: "thin" | "light" | "regular" ───────────────────────────

    var clockWeight: String
        get() = prefs.getString(KEY_CLOCK_WEIGHT, "thin") ?: "thin"
        set(value) { prefs.edit().putString(KEY_CLOCK_WEIGHT, value).apply() }

    fun clockTypeface(): Typeface {
        // Font family follows the global launcherFont setting.
        // Weight variants only exist for sans-serif; serif/mono fall back to NORMAL.
        val family = when (launcherFont) {
            "serif" -> "serif"
            "mono"  -> "monospace"
            else    -> when (clockWeight) {
                "light"   -> "sans-serif-light"
                "regular" -> "sans-serif"
                else      -> "sans-serif-thin"
            }
        }
        return Typeface.create(family, Typeface.NORMAL)
    }

    // ── Top padding (clock distance from top) ────────────────────────────────

    var topPadding: String
        get() = prefs.getString(KEY_TOP_PADDING, "default") ?: "default"
        set(value) { prefs.edit().putString(KEY_TOP_PADDING, value).apply() }

    fun topPaddingDp(): Float = when (topPadding) {
        "compact"  -> 8f
        "relaxed"  -> 80f
        "spacious" -> 120f
        else       -> 48f   // default
    }

    // ── Home screen text alignment: "left" | "center" | "right" ─────────────

    var homeAlignment: String
        get() = prefs.getString(KEY_HOME_ALIGNMENT, "left") ?: "left"
        set(value) { prefs.edit().putString(KEY_HOME_ALIGNMENT, value).apply() }

    // ── Sunrise / sunset visibility ───────────────────────────────────────────

    var showSunriseSunset: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SUNRISE, true)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_SUNRISE, value).apply() }

    // ── Cached location (for sunrise/sunset without waiting for GPS) ──────────

    var lastKnownLat: Float
        get() = prefs.getFloat(KEY_LAST_LAT, Float.MAX_VALUE)
        set(value) { prefs.edit().putFloat(KEY_LAST_LAT, value).apply() }

    var lastKnownLng: Float
        get() = prefs.getFloat(KEY_LAST_LNG, Float.MAX_VALUE)
        set(value) { prefs.edit().putFloat(KEY_LAST_LNG, value).apply() }

    fun hasLastLocation() = lastKnownLat != Float.MAX_VALUE

    // ── World clock timezone ───────────────────────────────────────────────────

    var worldClockZone: String
        get() = prefs.getString(KEY_WORLD_CLOCK, "") ?: ""
        set(value) { prefs.edit().putString(KEY_WORLD_CLOCK, value).apply() }

    // ── Days until ────────────────────────────────────────────────────────────

    var daysUntilLabel: String
        get() = prefs.getString(KEY_DAYS_LABEL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DAYS_LABEL, value).apply() }

    var daysUntilDate: String          // stored as "yyyy-MM-dd"
        get() = prefs.getString(KEY_DAYS_DATE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DAYS_DATE, value).apply() }

    // ── Greeting name ─────────────────────────────────────────────────────────

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USER_NAME, value).apply() }

    // ── Fullscreen (hide status bar) ──────────────────────────────────────────

    var fullscreenMode: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN, false)
        set(value) { prefs.edit().putBoolean(KEY_FULLSCREEN, value).apply() }

    // ── Onboarding ────────────────────────────────────────────────────────────

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING, value).apply() }

    // ── Launch counts ─────────────────────────────────────────────────────────

    fun recordLaunch(packageName: String) {
        val key = "$KEY_LAUNCH_PREFIX$packageName"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun getLaunchCount(packageName: String): Int =
        prefs.getInt("$KEY_LAUNCH_PREFIX$packageName", 0)

    fun getFrequent(pool: List<AppInfo>, limit: Int = 5): List<AppInfo> =
        pool.filter { getLaunchCount(it.packageName) > 0 }
            .sortedByDescending { getLaunchCount(it.packageName) }
            .take(limit)

    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val PREF_FILE          = "launcher_prefs"
        private const val KEY_24H            = "clock_24h"
        private const val KEY_DISPLAY_MODE   = "display_mode"
        private const val KEY_FONT_SIZE      = "font_size"
        private const val KEY_TEMP_UNIT      = "temp_unit"
        private const val KEY_GESTURE_LEFT   = "gesture_left"
        private const val KEY_GESTURE_RIGHT  = "gesture_right"
        private const val KEY_AUTO_KEYBOARD  = "auto_keyboard"
        private const val KEY_SHOW_TIMER     = "show_timer"
        private const val KEY_HIDDEN         = "hidden_apps"
        private const val KEY_PINNED         = "pinned_apps"
        private const val KEY_BLOCKED           = "blocked_apps"
        private const val KEY_WIND_DOWN_ENABLED = "wind_down_enabled"
        private const val KEY_WIND_DOWN_TIME    = "wind_down_time"
        private const val KEY_LAST_LAT         = "last_lat"
        private const val KEY_LAST_LNG         = "last_lng"
        private const val KEY_WORLD_CLOCK      = "world_clock_zone"
        private const val KEY_DAYS_LABEL       = "days_until_label"
        private const val KEY_DAYS_DATE        = "days_until_date"
        private const val KEY_USER_NAME        = "user_name"
        private const val KEY_FULLSCREEN       = "fullscreen_mode"
        private const val KEY_LAUNCHER_FONT   = "launcher_font"
        private const val KEY_LAUNCHER_WEIGHT = "launcher_weight"
        private const val KEY_CLOCK_SIZE     = "clock_size"
        private const val KEY_CLOCK_WEIGHT   = "clock_weight"
        private const val KEY_TOP_PADDING    = "top_padding"
        private const val KEY_LAUNCH_PREFIX  = "launch_"
        private const val KEY_ONBOARDING     = "onboarding_done"
        private const val KEY_SHOW_SUNRISE    = "show_sunrise_sunset"
        private const val KEY_HOME_ALIGNMENT  = "home_alignment"
    }
}
