package com.minimal.launcher

/**
 * Lightweight data class representing a single installed app entry.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
)
