package com.minimal.launcher

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class MinimalDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) = Unit
    override fun onDisabled(context: Context, intent: Intent) = Unit
}
