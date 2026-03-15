package com.minimal.launcher

import android.content.Context

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
        get() = prefs.getBoolean(KEY_SHOW_TIMER, true)
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

    // ── Pinned apps ───────────────────────────────────────────────────────────

    fun getPinnedPackages(): Set<String> =
        prefs.getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()

    fun isPinned(packageName: String): Boolean = packageName in getPinnedPackages()

    fun togglePin(packageName: String) {
        val s = getPinnedPackages().toMutableSet()
        if (packageName in s) s.remove(packageName) else s.add(packageName)
        prefs.edit().putStringSet(KEY_PINNED, s).apply()
    }

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
        private const val KEY_LAUNCH_PREFIX  = "launch_"
    }
}
