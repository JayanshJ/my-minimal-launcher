package com.minimal.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

/**
 * Wraps DevicePolicyManager to enforce a strict app whitelist when the
 * launcher is set as Device Owner via ADB:
 *
 *   adb shell dpm set-device-owner com.minimal.launcher/.MinimalDeviceAdminReceiver
 *
 * Once set, every non-whitelisted package is suspended at OS level — it cannot
 * be opened by any means, including notifications, deep links, or direct intents.
 */
object AppLockManager {

    // Packages that are always allowed regardless of whitelist (launcher + core OS)
    private val ALWAYS_ALLOWED = setOf(
        "com.minimal.launcher",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom"
    )

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, MinimalDeviceAdminReceiver::class.java)

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /** Returns true if this app is set as Device Owner. */
    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    /**
     * Suspend every installed package not in [whitelist] (or in ALWAYS_ALLOWED).
     * Safe to call repeatedly — just re-applies the current state.
     */
    fun applyWhitelist(context: Context, whitelist: Set<String>) {
        if (!isDeviceOwner(context)) return
        val admin = adminComponent(context)
        val pm    = context.packageManager

        val installed = pm.getInstalledPackages(0).map { it.packageName }
        val allow     = whitelist + ALWAYS_ALLOWED

        val toBlock  = installed.filter { it !in allow }
        val toUnlock = installed.filter { it in allow }

        runCatching { dpm(context).setPackagesSuspended(admin, toBlock.toTypedArray(),  true) }
        runCatching { dpm(context).setPackagesSuspended(admin, toUnlock.toTypedArray(), false) }
    }

    /** Lift the whitelist — unsuspend everything. */
    fun unsuspendAll(context: Context) {
        if (!isDeviceOwner(context)) return
        val admin    = adminComponent(context)
        val packages = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }.toTypedArray()
        runCatching { dpm(context).setPackagesSuspended(admin, packages, false) }
    }

    /**
     * Lock down the device:
     * - Block uninstalling this launcher
     * - Block access to app settings (prevents changing default launcher)
     * - Block sideloading APKs
     * - Block safe-boot bypass
     */
    fun applyRestrictions(context: Context) {
        if (!isDeviceOwner(context)) return
        val admin = adminComponent(context)
        // Prevent uninstalling our launcher
        runCatching { dpm(context).setUninstallBlocked(admin, context.packageName, true) }
        // Prevent accessing Apps settings — blocks changing default launcher & uninstalling anything
        runCatching { dpm(context).addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL) }
        // Block sideloading
        runCatching { dpm(context).addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS) }
        runCatching { dpm(context).addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) }
        // Block safe-boot bypass
        runCatching { dpm(context).addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
    }

    /** Remove all restrictions (called after 24h countdown expires). */
    fun removeRestrictions(context: Context) {
        if (!isDeviceOwner(context)) return
        val admin = adminComponent(context)
        runCatching { dpm(context).setUninstallBlocked(admin, context.packageName, false) }
        runCatching { dpm(context).clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL) }
        runCatching { dpm(context).clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS) }
        runCatching { dpm(context).clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) }
        runCatching { dpm(context).clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
    }
}
