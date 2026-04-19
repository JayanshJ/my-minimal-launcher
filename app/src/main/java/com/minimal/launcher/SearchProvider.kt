package com.minimal.launcher

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Settings

/**
 * Provides settings shortcuts and contact results for universal search.
 */
object SearchProvider {

    data class SettingsEntry(
        val label: String,
        val action: String,
        val keywords: List<String>
    )

    data class ContactEntry(val name: String, val number: String)

    // ── Settings shortcuts ────────────────────────────────────────────────────

    val SETTINGS = listOf(
        SettingsEntry("Wi-Fi",               Settings.ACTION_WIFI_SETTINGS,
            listOf("wifi", "wi-fi", "wireless", "internet", "network")),
        SettingsEntry("Bluetooth",            Settings.ACTION_BLUETOOTH_SETTINGS,
            listOf("bluetooth", "bt", "pair", "headphone", "speaker", "earbuds")),
        SettingsEntry("Mobile data",          Settings.ACTION_DATA_ROAMING_SETTINGS,
            listOf("mobile", "data", "cellular", "sim", "4g", "5g", "lte")),
        SettingsEntry("Airplane mode",        Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            listOf("airplane", "flight", "aeroplane", "mode")),
        SettingsEntry("Hotspot & tethering",  Settings.ACTION_WIRELESS_SETTINGS,
            listOf("hotspot", "tether", "share", "internet sharing")),
        SettingsEntry("Display",              Settings.ACTION_DISPLAY_SETTINGS,
            listOf("display", "screen", "brightness", "dark mode", "night", "wallpaper", "resolution", "refresh")),
        SettingsEntry("Sound & vibration",    Settings.ACTION_SOUND_SETTINGS,
            listOf("sound", "volume", "ringtone", "vibration", "vibrate", "notification", "alert", "silent", "mute")),
        SettingsEntry("Notifications",        Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            listOf("notification", "alerts", "dnd", "do not disturb", "banner")),
        SettingsEntry("Battery",              Settings.ACTION_BATTERY_SAVER_SETTINGS,
            listOf("battery", "power", "charging", "saver", "usage", "drain")),
        SettingsEntry("Storage",              Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            listOf("storage", "files", "space", "memory", "disk", "free")),
        SettingsEntry("Apps",                 Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
            listOf("apps", "applications", "manage", "install", "uninstall", "permissions", "default")),
        SettingsEntry("Location",             Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            listOf("location", "gps", "maps", "navigation", "position")),
        SettingsEntry("Security",             Settings.ACTION_SECURITY_SETTINGS,
            listOf("security", "lock", "fingerprint", "face unlock", "pin", "password", "pattern", "encrypt", "screen lock")),
        SettingsEntry("Privacy",              Settings.ACTION_PRIVACY_SETTINGS,
            listOf("privacy", "permissions", "microphone", "camera", "sensitive", "data")),
        SettingsEntry("Accounts",             Settings.ACTION_SYNC_SETTINGS,
            listOf("accounts", "sync", "google", "email", "calendar", "backup")),
        SettingsEntry("Language & input",     Settings.ACTION_LOCALE_SETTINGS,
            listOf("language", "locale", "keyboard", "input", "typing", "autocorrect", "spell")),
        SettingsEntry("Accessibility",        Settings.ACTION_ACCESSIBILITY_SETTINGS,
            listOf("accessibility", "talkback", "magnifier", "colour", "color", "contrast", "font size")),
        SettingsEntry("Date & time",          Settings.ACTION_DATE_SETTINGS,
            listOf("date", "time", "timezone", "clock", "24h", "format", "ntp")),
        SettingsEntry("About phone",          Settings.ACTION_DEVICE_INFO_SETTINGS,
            listOf("about", "device", "info", "version", "android", "build", "model", "imei", "serial", "update")),
        SettingsEntry("Developer options",    Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            listOf("developer", "debug", "adb", "usb", "dev"))
    )

    fun searchSettings(query: String): List<SettingsEntry> {
        if (query.length < 2) return emptyList()
        val q = query.lowercase().trim()
        return SETTINGS.filter { entry ->
            entry.label.lowercase().contains(q) ||
            entry.keywords.any { it.contains(q) }
        }.take(3)
    }

    // ── Contact search ────────────────────────────────────────────────────────

    fun searchContacts(context: Context, query: String): List<ContactEntry> {
        if (query.length < 2) return emptyList()
        val results = mutableListOf<ContactEntry>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, args,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")
            val seen = mutableSetOf<String>()
            cursor?.use {
                val nameCol   = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext() && results.size < 3) {
                    val name   = it.getString(nameCol)  ?: continue
                    val number = it.getString(numberCol) ?: continue
                    if (seen.add(name)) results.add(ContactEntry(name, number))
                }
            }
        } catch (_: Exception) { }
        finally { cursor?.close() }
        return results
    }
}
