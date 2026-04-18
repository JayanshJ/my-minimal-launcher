package com.minimal.launcher

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export all launcher settings to a JSON file in Downloads,
 * and restore them from a previously saved file.
 *
 * No extra permissions required on API 29+.
 * Security-sensitive data (PIN, dumb-phone state, countdown) is intentionally excluded.
 */
object BackupManager {

    // ── Export ────────────────────────────────────────────────────────────────

    fun export(context: Context, prefs: PrefsManager): Boolean {
        return try {
            val json = buildJson(prefs)
            val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "minimal_backup_$date.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: return false
                resolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
                cv.clear()
                cv.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, cv, null, null)
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                java.io.File(dir, fileName).writeText(json.toString(2))
            }
            true
        } catch (_: Exception) { false }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    fun import(context: Context, prefs: PrefsManager, uri: Uri): Boolean {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.bufferedReader().readText() }
                ?: return false
            applyJson(prefs, JSONObject(text))
            true
        } catch (_: Exception) { false }
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private fun buildJson(prefs: PrefsManager) = JSONObject().apply {
        put("is24Hour",          prefs.is24Hour)
        put("displayMode",       prefs.displayMode)
        put("fontSize",          prefs.fontSize)
        put("autoKeyboard",      prefs.autoKeyboard)
        put("showTimer",         prefs.showTimer)
        put("tempUnit",          prefs.tempUnit)
        put("gestureLeftPkg",    prefs.gestureLeftPkg)
        put("gestureRightPkg",   prefs.gestureRightPkg)
        put("windDownEnabled",   prefs.windDownEnabled)
        put("windDownTime",      prefs.windDownTime)
        put("launcherFont",      prefs.launcherFont)
        put("launcherWeight",    prefs.launcherWeight)
        put("clockSize",         prefs.clockSize)
        put("clockWeight",       prefs.clockWeight)
        put("topPadding",        prefs.topPadding)
        put("homeAlignment",     prefs.homeAlignment)
        put("showSunriseSunset", prefs.showSunriseSunset)
        put("worldClockZone",    prefs.worldClockZone)
        put("daysUntilLabel",    prefs.daysUntilLabel)
        put("daysUntilDate",     prefs.daysUntilDate)
        put("userName",          prefs.userName)
        put("fullscreenMode",    prefs.fullscreenMode)
        put("homeTab",           prefs.homeTab)
        put("pinnedApps",  JSONArray(prefs.getPinnedPackages().toList()))
        put("hiddenApps",  JSONArray(prefs.getHiddenPackages().toList()))
        put("blockedApps", JSONArray(prefs.getBlockedPackages().toList()))
        put("delayedApps", JSONArray(prefs.getDelayedPackages().toList()))
    }

    private fun applyJson(prefs: PrefsManager, j: JSONObject) {
        fun str(k: String)  = if (j.has(k)) j.getString(k)  else null
        fun bool(k: String) = if (j.has(k)) j.getBoolean(k) else null
        fun arr(k: String): Set<String>? {
            if (!j.has(k)) return null
            val a = j.getJSONArray(k)
            return (0 until a.length()).map { a.getString(it) }.toSet()
        }

        bool("is24Hour")?.let          { prefs.is24Hour          = it }
        str("displayMode")?.let        { prefs.displayMode        = it }
        str("fontSize")?.let           { prefs.fontSize           = it }
        bool("autoKeyboard")?.let      { prefs.autoKeyboard       = it }
        bool("showTimer")?.let         { prefs.showTimer          = it }
        str("tempUnit")?.let           { prefs.tempUnit           = it }
        str("gestureLeftPkg")?.let     { prefs.gestureLeftPkg     = it }
        str("gestureRightPkg")?.let    { prefs.gestureRightPkg    = it }
        bool("windDownEnabled")?.let   { prefs.windDownEnabled    = it }
        str("windDownTime")?.let       { prefs.windDownTime       = it }
        str("launcherFont")?.let       { prefs.launcherFont       = it }
        str("launcherWeight")?.let     { prefs.launcherWeight     = it }
        str("clockSize")?.let          { prefs.clockSize          = it }
        str("clockWeight")?.let        { prefs.clockWeight        = it }
        str("topPadding")?.let         { prefs.topPadding         = it }
        str("homeAlignment")?.let      { prefs.homeAlignment      = it }
        bool("showSunriseSunset")?.let { prefs.showSunriseSunset  = it }
        str("worldClockZone")?.let     { prefs.worldClockZone     = it }
        str("daysUntilLabel")?.let     { prefs.daysUntilLabel     = it }
        str("daysUntilDate")?.let      { prefs.daysUntilDate      = it }
        str("userName")?.let           { prefs.userName           = it }
        bool("fullscreenMode")?.let    { prefs.fullscreenMode     = it }
        str("homeTab")?.let            { prefs.homeTab            = it }
        arr("pinnedApps")?.let         { prefs.setPinnedPackages(it)   }
        arr("hiddenApps")?.let         { prefs.setHiddenPackages(it)   }
        arr("blockedApps")?.let        { prefs.setBlockedPackages(it)  }
        arr("delayedApps")?.let        { prefs.setDelayedPackages(it)  }
    }
}
