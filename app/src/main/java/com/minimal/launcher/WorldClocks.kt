package com.minimal.launcher

/** City → timezone ID pairs shown in the world clock picker. */
object WorldClocks {

    val zones: List<Pair<String, String>> = listOf(
        "los angeles"  to "America/Los_Angeles",
        "denver"       to "America/Denver",
        "chicago"      to "America/Chicago",
        "new york"     to "America/New_York",
        "são paulo"    to "America/Sao_Paulo",
        "london"       to "Europe/London",
        "paris"        to "Europe/Paris",
        "berlin"       to "Europe/Berlin",
        "cairo"        to "Africa/Cairo",
        "dubai"        to "Asia/Dubai",
        "mumbai"       to "Asia/Kolkata",
        "bangkok"      to "Asia/Bangkok",
        "singapore"    to "Asia/Singapore",
        "hong kong"    to "Asia/Hong_Kong",
        "tokyo"        to "Asia/Tokyo",
        "sydney"       to "Australia/Sydney",
        "auckland"     to "Pacific/Auckland",
    )

    fun labelFor(zoneId: String): String =
        zones.firstOrNull { it.second == zoneId }?.first
            ?: zoneId.substringAfterLast("/").replace("_", " ").lowercase()
}
