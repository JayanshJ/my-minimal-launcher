package com.minimal.launcher

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.ActivityOptions
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.graphics.RectF
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimal.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private lateinit var packageReceiver: PackageReceiver
    private lateinit var prefs: PrefsManager
    private lateinit var gestureDetector: GestureDetector

    // All installed apps (used to resolve home-screen app labels)
    private var allInstalledApps: List<AppInfo> = emptyList()

    // Clock
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            clockHandler.postDelayed(this, 1_000L - (now % 1_000L))
        }
    }
    private var clockFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt  = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    private val ampmFmt  = SimpleDateFormat("a", Locale.getDefault())

    // Battery
    private var batteryText: String = ""

    // Screen time
    private var screenTimeText: String = ""

    // Weather
    private var weatherText: String = ""
    private var sunriseText: String = ""
    private var worldClockText: String = ""
    private var daysUntilText: String = ""
    private val weatherHandler = Handler(Looper.getMainLooper())
    private val weatherRefreshRunnable = Runnable { refreshWeather() }


    // Pomodoro timer
    private val timerHandler      = Handler(Looper.getMainLooper())
    private var timerRunning      = false
    private var isFocusPhase      = true
    private var focusDoneCount    = 0          // completed focus sessions (long-break every 4th)
    private var remainingMs       = FOCUS_MS
    private var timerEndEpoch     = 0L         // epoch ms when current countdown ends
    private val timerTick         = Runnable { tickTimer() }
    private var timerPulseAnimator: ObjectAnimator? = null

    // Animation state
    private var isFirstResume    = true
    private var lastClockMinute  = ""
    private var lastWidgetText   = ""
    private var lastGreetingHour = -1
    private var hintAnimator: ObjectAnimator? = null

    // Spotlight tour
    private var tourActive = false
    private var tourStep   = 0

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        updateClockFormat()

        // Back on home screen does nothing (standard launcher behaviour)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* intentionally empty */ }
        })

        setupGestures()
        setupClock()
        setupPomodoro()
        setupHomeAppList()
        setupPackageReceiver()
        requestRuntimePermissions()
        requestSpecialPermissions()
    }

    private fun requestSpecialPermissions() {
        promptDefaultLauncher()
    }

    private fun promptDefaultLauncher() {
        val isDefault = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName == packageName

        if (isDefault) return

        AlertDialog.Builder(this)
            .setTitle("Set as default launcher")
            .setMessage("Set Minimal as your default launcher so it opens on the home button.")
            .setPositiveButton("Set default") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = getSystemService(android.app.role.RoleManager::class.java)
                    if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                        !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        startActivityForResult(
                            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),
                            REQ_ROLE_HOME
                        )
                    }
                } else {
                    startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .setNegativeButton("Skip", null)
            .setCancelable(false)
            .show()
    }


    private fun applyClockStyle() {
        // Top padding lives on the greeting row now
        val px = (prefs.topPaddingDp() * resources.displayMetrics.density).toInt()
        binding.tvGreeting.updateLayoutParams<android.widget.LinearLayout.LayoutParams> {
            topMargin = px
        }
        // Clock-specific style
        binding.tvClock.textSize = prefs.clockSizeSp()
        binding.tvClock.typeface = prefs.clockTypeface()
        // UI font applied to everything else
        val uiTf = prefs.launcherTypeface()
        binding.tvGreeting.typeface    = uiTf
        binding.tvDate.typeface        = uiTf
        binding.tvWidget.typeface      = uiTf
        binding.tvTimerTime.typeface   = uiTf
        binding.tvTimerLabel.typeface  = uiTf
        binding.tvEmptyHint.typeface   = uiTf
        binding.tvDrawerHint.typeface  = uiTf
        adapter.typeface = uiTf
        adapter.notifyDataSetChanged()
        applyAlignment()
    }

    private fun applyAlignment() {
        val marginH = resources.getDimensionPixelSize(R.dimen.margin_h)
        val (gravity, startMargin, endMargin) = when (prefs.homeAlignment) {
            "center" -> Triple(Gravity.CENTER_HORIZONTAL, 0, 0)
            "right"  -> Triple(Gravity.END,               0, marginH)
            else     -> Triple(Gravity.START,        marginH, 0)
        }
        // tv_date is inside a FrameLayout — just set text gravity, layout is match_parent
        binding.tvDate.gravity = gravity

        listOf(
            binding.tvGreeting, binding.tvClock,
            binding.tvWidget, binding.tvTimerTime, binding.tvTimerLabel,
            binding.tvFocusAction
        ).forEach { tv ->
            tv.gravity = gravity
            tv.updateLayoutParams<android.widget.LinearLayout.LayoutParams> {
                width       = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                marginStart = startMargin
                marginEnd   = endMargin
            }
        }
        adapter.textGravity = gravity
        adapter.notifyDataSetChanged()
    }

    private fun applyFullscreen() {
        val ctrl = WindowInsetsControllerCompat(window, window.decorView)
        if (prefs.fullscreenMode) {
            ctrl.hide(WindowInsetsCompat.Type.statusBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            ctrl.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun updateGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val name = prefs.userName.trim()
        // Only recompute when the hour or name could have changed
        val phrase = when (hour) {
            in 5..11  -> "good morning"
            in 12..16 -> "good afternoon"
            in 17..20 -> "good evening"
            else      -> "good night"
        }
        val newText = if (name.isEmpty()) "$phrase." else "$phrase, ${name.lowercase()}."
        if (binding.tvGreeting.text == newText) return

        if (binding.tvGreeting.text.isEmpty()) {
            binding.tvGreeting.text = newText
        } else {
            binding.tvGreeting.animate().alpha(0f).setDuration(120).withEndAction {
                binding.tvGreeting.text = newText
                binding.tvGreeting.animate().alpha(1f).setDuration(200).start()
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-disable dumb phone mode when 24h countdown expires
        if (prefs.dumbPhoneEnabled && prefs.isDisableCountdownExpired()) {
            prefs.dumbPhoneEnabled   = false
            prefs.disableRequestedAt = -1L
            AppLockManager.removeRestrictions(this)
            AppLockManager.unsuspendAll(this)
        }
        applyFullscreen()
        applyClockStyle()
        updateGreeting()
        loadInstalledApps()
        loadHomeApps()
        updateClock()
        refreshBattery()
        refreshScreenTime()
        refreshWeather()
        updateDaysUntil()
        if (prefs.hasLastLocation()) {
            refreshSunriseSunset(prefs.lastKnownLat.toDouble(), prefs.lastKnownLng.toDouble())
        }
        applyTimerVisibility()
        hintAnimator?.resume()
        animateHomeEntry()
        animateDrawerHint()
        if (!prefs.hasSeenOnboarding && !tourActive) {
            // Wait for home animations to finish before showing the tour
            binding.root.postDelayed({ startTour() }, 900L)
        }
    }

    override fun onPause() {
        super.onPause()
        hintAnimator?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacksAndMessages(null)
        weatherHandler.removeCallbacksAndMessages(null)
        timerHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(packageReceiver)
    }

    // ─── Clock ────────────────────────────────────────────────────────────────

    private fun setupClock() {
        updateClock()
        clockHandler.post(clockRunnable)
        binding.tvClock.setOnClickListener {
            prefs.is24Hour = !prefs.is24Hour
            updateClockFormat()
            updateClock()
        }
        binding.tvClock.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
    }

    private fun updateClockFormat() {
        clockFmt = if (prefs.is24Hour)
            SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        else
            SimpleDateFormat("h:mm:ss", Locale.getDefault())
    }

    private fun updateClock() {
        updateGreeting()
        updateWorldClock()
        val now    = Date()
        val minute = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val minuteChanged = minute != lastClockMinute
        if (minuteChanged) lastClockMinute = minute

        fun applyClockText() {
            if (prefs.is24Hour) {
                binding.tvClock.text = clockFmt.format(now)
            } else {
                val timeStr = clockFmt.format(now)
                val ampm    = ampmFmt.format(now).lowercase()
                val span    = SpannableStringBuilder(timeStr).append("  ").append(ampm)
                val start   = timeStr.length + 2
                span.setSpan(RelativeSizeSpan(0.28f), start, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(ForegroundColorSpan(Color.parseColor("#AAAAAA")), start, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.tvClock.text = span
            }
        }

        if (minuteChanged && binding.tvClock.alpha == 1f && lastClockMinute.isNotEmpty()) {
            binding.tvClock.animate().alpha(0.25f).setDuration(90).withEndAction {
                applyClockText()
                binding.tvClock.animate().alpha(1f).setDuration(160).start()
            }.start()
        } else {
            applyClockText()
        }
        binding.tvDate.text = dateFmt.format(now)
    }

    // ─── Sunrise / sunset ─────────────────────────────────────────────────────

    private fun refreshSunriseSunset(lat: Double, lng: Double) {
        val pair = SunCalculator.today(lat, lng) ?: return
        val fmt = SimpleDateFormat(if (prefs.is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
        sunriseText = "↑ ${fmt.format(java.util.Date(pair.first))}  ·  ↓ ${fmt.format(java.util.Date(pair.second))}"
        updateWidgetText()
    }

    // ─── World clock ──────────────────────────────────────────────────────────

    private fun updateWorldClock() {
        val zoneId = prefs.worldClockZone
        if (zoneId.isEmpty()) { worldClockText = ""; return }
        val tz  = java.util.TimeZone.getTimeZone(zoneId)
        val fmt = SimpleDateFormat(if (prefs.is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
        fmt.timeZone = tz
        val city = WorldClocks.labelFor(zoneId)
        worldClockText = "$city  ·  ${fmt.format(java.util.Date())}"
    }

    // ─── Days until ───────────────────────────────────────────────────────────

    private fun updateDaysUntil() {
        val label   = prefs.daysUntilLabel.trim()
        val dateStr = prefs.daysUntilDate.trim()
        if (label.isEmpty() || dateStr.isEmpty()) { daysUntilText = ""; updateWidgetText(); return }

        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0);      set(java.util.Calendar.MILLISECOND, 0)
        }
        val target = try {
            java.util.Calendar.getInstance().also { cal ->
                cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)!!
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0);      cal.set(java.util.Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) { daysUntilText = ""; updateWidgetText(); return }

        val days = ((target.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
        daysUntilText = when {
            days == 0  -> "$label  ·  today"
            days == 1  -> "$label  ·  tomorrow"
            days > 1   -> "$label  ·  $days days"
            days == -1 -> "$label  ·  yesterday"
            else       -> "$label  ·  ${-days} days ago"
        }
        updateWidgetText()
    }

    // ─── Widget ───────────────────────────────────────────────────────────────

    private fun updateWidgetText() {
        val parts = mutableListOf<String>()
        val weatherLine = listOf(weatherText, batteryText)
            .filter { it.isNotEmpty() }
            .joinToString("  ·  ")
        if (weatherLine.isNotEmpty())    parts.add(weatherLine)
        if (sunriseText.isNotEmpty() && prefs.showSunriseSunset) parts.add(sunriseText)
        if (screenTimeText.isNotEmpty()) parts.add(screenTimeText)
        if (worldClockText.isNotEmpty()) parts.add(worldClockText)
        if (daysUntilText.isNotEmpty())  parts.add(daysUntilText)

        val newText = parts.joinToString("\n")
        if (newText == lastWidgetText) return
        lastWidgetText = newText
        if (binding.tvWidget.text.isNotEmpty() && binding.tvWidget.alpha == 1f) {
            binding.tvWidget.animate().alpha(0f).setDuration(80).withEndAction {
                binding.tvWidget.text = newText
                binding.tvWidget.animate().alpha(1f).setDuration(200).start()
            }.start()
        } else {
            binding.tvWidget.text = newText
        }
    }

    // ─── Battery ──────────────────────────────────────────────────────────────

    private fun refreshBattery() {
        // ACTION_BATTERY_CHANGED is a sticky broadcast — no receiver needed, just query it once
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        batteryText = if (level >= 0) "${level * 100 / scale}%" else ""
        updateWidgetText()
    }

    // ─── Screen time ──────────────────────────────────────────────────────────

    private fun hasUsageStatsPermission(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun refreshScreenTime() {
        if (!hasUsageStatsPermission()) { screenTimeText = ""; updateWidgetText(); return }

        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()

        // queryUsageStats(INTERVAL_DAILY) returns totals for the whole bucket period,
        // which may cross midnight and inflate the count. Use queryEvents instead for
        // precise per-session tracking since midnight.
        val events = usm.queryEvents(midnight, now)
        val event = android.app.usage.UsageEvents.Event()
        val foregroundStart = mutableMapOf<String, Long>()
        var totalMs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    foregroundStart[event.packageName] = event.timeStamp
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = foregroundStart.remove(event.packageName)
                    if (start != null) totalMs += event.timeStamp - start
                }
            }
        }
        // Apps still in foreground at query time
        for (start in foregroundStart.values) totalMs += now - start

        val hours   = totalMs / 3_600_000L
        val minutes = (totalMs / 60_000L) % 60
        screenTimeText = when {
            totalMs <= 0L -> ""
            hours > 0L   -> "${hours}h ${minutes}m"
            else         -> "${minutes}m"
        }
        updateWidgetText()
        updateScreenTimeBar(totalMs)
    }

    private fun updateScreenTimeBar(totalMs: Long) {
        val bar = binding.screenTimeProgress
        if (totalMs <= 0L) {
            bar.visibility = android.view.View.GONE
            return
        }
        val goalMs  = SCREEN_TIME_GOAL_MS
        val pct     = ((totalMs.toFloat() / goalMs) * 100).toInt().coerceIn(0, 100)
        val overGoal = totalMs > goalMs
        bar.progressTintList = android.content.res.ColorStateList.valueOf(
            if (overGoal) 0xFFFF6B6B.toInt() else 0xFFFFFFFF.toInt()
        )
        if (bar.visibility != android.view.View.VISIBLE) {
            bar.progress = 0
            bar.visibility = android.view.View.VISIBLE
        }
        val anim = android.animation.ObjectAnimator.ofInt(bar, "progress", bar.progress, pct)
        anim.duration = 600
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        anim.start()
    }

    // ─── Weather ──────────────────────────────────────────────────────────────

    private fun requestRuntimePermissions() {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            else refreshWeather()
        }
        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun refreshWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) { null }

        if (loc == null) return

        // Cache for sunrise/sunset (survives across onResume without needing GPS again)
        prefs.lastKnownLat = loc.latitude.toFloat()
        prefs.lastKnownLng = loc.longitude.toFloat()
        refreshSunriseSunset(loc.latitude, loc.longitude)

        val fahrenheit = prefs.tempUnit == "fahrenheit"
        lifecycleScope.launch {
            val data = WeatherManager.fetch(loc.latitude, loc.longitude, fahrenheit)
            weatherText = data?.display ?: ""
            updateWidgetText()
        }

        weatherHandler.removeCallbacks(weatherRefreshRunnable)
        weatherHandler.postDelayed(weatherRefreshRunnable, 15 * 60 * 1_000L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            permissions.forEachIndexed { i, perm ->
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    when (perm) {
                        Manifest.permission.ACCESS_COARSE_LOCATION -> refreshWeather()
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE_HOME) { /* role result handled */ }
    }



    // ─── Home app list ────────────────────────────────────────────────────────

    private fun setupHomeAppList() {
        adapter = AppListAdapter(
            onAppClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                launchApp(app)
            },
            onAppLongClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showHomeAppOptions(app)
                true
            }
        )
        binding.rvHomeApps.adapter = adapter
        binding.rvHomeApps.layoutManager = LinearLayoutManager(this)
        binding.rvHomeApps.itemAnimator = null
        binding.rvHomeApps.overScrollMode = android.view.View.OVER_SCROLL_NEVER
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val list: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        else
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)

        allInstalledApps = list
            .mapNotNull { ri ->
                val label = ri.loadLabel(pm).toString().trim()
                if (label.isEmpty()) null
                else AppInfo(label, ri.activityInfo.packageName, ri.activityInfo.name)
            }
            .sortedBy { it.label.lowercase() }
            .filter { it.packageName != packageName }
    }

    private fun loadHomeApps() {
        val homeApps = if (prefs.homeTab == "frequent") {
            prefs.getFrequent(allInstalledApps, limit = 8)
        } else {
            val pinned = prefs.getPinnedPackages()
            allInstalledApps
                .filter { it.packageName in pinned }
                .sortedBy { it.label.lowercase() }
        }

        adapter.fontSizeSp = prefs.fontSizeSp()
        adapter.submitList(homeApps.map { AppListAdapter.Item.App(it) })

        val empty = homeApps.isEmpty()
        binding.tvEmptyHint.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvHomeApps.visibility  = if (empty) View.GONE    else View.VISIBLE
    }

    private fun launchApp(app: AppInfo) {
        if (prefs.isBlocked(app.packageName)) {
            showBlockedDialog(app)
            return
        }
        if (prefs.isDelayed(app.packageName)) {
            startActivity(Intent(this, AppOpenDelayActivity::class.java).apply {
                putExtra(AppOpenDelayActivity.EXTRA_PKG,      app.packageName)
                putExtra(AppOpenDelayActivity.EXTRA_ACTIVITY, app.activityName)
                putExtra(AppOpenDelayActivity.EXTRA_LABEL,    app.label)
            })
            return
        }
        doLaunchApp(app)
    }

    private fun showBlockedDialog(app: AppInfo) {
        val builder = AlertDialog.Builder(this)
            .setTitle("opening ${app.label.lowercase()}.")
        if (prefs.isWindDownActive()) {
            builder
                .setMessage("it's past ${prefs.windDownTime}. wind-down mode is on.\nthis one can wait until tomorrow.")
                .setNegativeButton("ok", null)
        } else {
            builder
                .setMessage("this app is on your distraction list.\ndo you really want to open it?")
                .setPositiveButton("open it") { _, _ -> doLaunchApp(app) }
                .setNegativeButton("not right now", null)
        }
        builder.show()
    }

    private fun doLaunchApp(app: AppInfo) {
        prefs.recordLaunch(app.packageName)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try { startActivity(intent) } catch (_: Exception) { }
    }

    private fun openAppInfo(app: AppInfo) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun showHomeAppOptions(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Remove from home screen", "App info")) { _, which ->
                when (which) {
                    0 -> { prefs.togglePin(app.packageName); loadHomeApps() }
                    1 -> openAppInfo(app)
                }
            }
            .show()
    }

    // ─── Package receiver ─────────────────────────────────────────────────────

    private fun setupPackageReceiver() {
        packageReceiver = PackageReceiver {
            loadInstalledApps()
            loadHomeApps()
        }
        registerReceiver(packageReceiver, packageReceiver.buildIntentFilter())
    }

    // ─── Pomodoro timer ───────────────────────────────────────────────────────

    private fun applyTimerVisibility() {
        val vis = if (prefs.showTimer) View.VISIBLE else View.GONE
        binding.tvTimerTime.visibility    = vis
        binding.tvTimerLabel.visibility   = vis
        binding.tvFocusAction.visibility  = vis
        if (!prefs.showTimer && timerRunning) pauseTimer()
    }

    private fun setupPomodoro() {
        updateTimerDisplay()
        val onTap: (View) -> Unit = {
            if (timerRunning) pauseTimer() else startTimer()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        val onLongPress: (View) -> Boolean = {
            resetTimer()
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            true
        }
        binding.tvTimerTime.setOnClickListener(onTap)
        binding.tvTimerLabel.setOnClickListener(onTap)
        binding.tvTimerTime.setOnLongClickListener(onLongPress)
        binding.tvTimerLabel.setOnLongClickListener(onLongPress)

        binding.tvFocusAction.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (timerRunning) {
                resetTimer()
            } else {
                // Always restart a fresh focus session from this button
                isFocusPhase   = true
                focusDoneCount = 0
                remainingMs    = FOCUS_MS
                startTimer()
            }
        }
    }

    private fun startTimer() {
        timerRunning  = true
        timerEndEpoch = System.currentTimeMillis() + remainingMs
        timerHandler.post(timerTick)
        updateTimerDisplay()
        startTimerPulse()
    }

    private fun pauseTimer() {
        timerRunning  = false
        timerHandler.removeCallbacks(timerTick)
        remainingMs   = (timerEndEpoch - System.currentTimeMillis()).coerceAtLeast(0L)
        stopTimerPulse()
        updateTimerDisplay()
    }

    private fun resetTimer() {
        timerRunning   = false
        isFocusPhase   = true
        focusDoneCount = 0
        remainingMs    = FOCUS_MS
        timerHandler.removeCallbacks(timerTick)
        stopTimerPulse()
        updateTimerDisplay()
    }

    private fun startTimerPulse() {
        timerPulseAnimator?.cancel()
        timerPulseAnimator = ObjectAnimator.ofFloat(binding.tvTimerTime, "alpha", 1f, 0.3f, 1f).apply {
            duration     = 2600
            repeatCount  = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopTimerPulse() {
        timerPulseAnimator?.cancel()
        timerPulseAnimator = null
        binding.tvTimerTime.animate().alpha(1f).setDuration(200).start()
    }

    private fun tickTimer() {
        val left = timerEndEpoch - System.currentTimeMillis()
        if (left <= 0L) {
            onSessionFinished()
        } else {
            remainingMs = left
            updateTimerDisplay()
            timerHandler.postDelayed(timerTick, 500L) // 500 ms for sub-second accuracy
        }
    }

    private fun onSessionFinished() {
        timerRunning = false
        stopTimerPulse()
        // Scale bounce to signal the session switch
        binding.tvTimerTime.animate()
            .scaleX(1.12f).scaleY(1.12f).setDuration(160)
            .withEndAction {
                binding.tvTimerTime.animate()
                    .scaleX(1f).scaleY(1f).setDuration(300)
                    .setInterpolator(OvershootInterpolator(2.5f)).start()
            }.start()
        // Buzz to signal session end
        @Suppress("DEPRECATION")
        val vib = getSystemService(Vibrator::class.java)
        vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))

        if (isFocusPhase) {
            focusDoneCount++
            isFocusPhase = false
            remainingMs  = if (focusDoneCount % 4 == 0) LONG_BREAK_MS else BREAK_MS
        } else {
            isFocusPhase = true
            remainingMs  = FOCUS_MS
        }
        updateTimerDisplay()
    }

    private fun updateTimerDisplay() {
        val totalSecs = (remainingMs / 1000L).toInt()
        binding.tvTimerTime.text = "%02d:%02d".format(totalSecs / 60, totalSecs % 60)

        val phaseName = when {
            isFocusPhase                -> "focus"
            focusDoneCount % 4 == 0    -> "long break"
            else                        -> "break"
        }
        val fullMs = when {
            isFocusPhase             -> FOCUS_MS
            focusDoneCount % 4 == 0 -> LONG_BREAK_MS
            else                     -> BREAK_MS
        }
        binding.tvTimerLabel.text = when {
            timerRunning           -> phaseName
            remainingMs >= fullMs  -> "$phaseName  ·  tap to start"
            else                   -> "$phaseName  ·  paused"
        }

        val newActionText = if (timerRunning) "end focus" else "start focus →"
        if (binding.tvFocusAction.text != newActionText) {
            binding.tvFocusAction.animate().alpha(0f).setDuration(80).withEndAction {
                binding.tvFocusAction.text = newActionText
                binding.tvFocusAction.setTextColor(
                    if (timerRunning) android.graphics.Color.parseColor("#FF6B6B")
                    else android.graphics.Color.WHITE
                )
                binding.tvFocusAction.animate().alpha(1f).setDuration(150).start()
            }.start()
        }
    }

    // ─── Gestures ─────────────────────────────────────────────────────────────

    private fun setupGestures() {
        binding.tvDrawerHint.setOnClickListener { openDrawer() }
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = (e1?.y ?: 0f) - e2.y  // positive = swipe up

                return when {
                    dy > 120f && -velocityY > 200f && dy > abs(dx) -> {
                        openDrawer(); true
                    }
                    dy < -120f && velocityY > 200f && abs(dy) > abs(dx) -> {
                        expandNotifications(); true
                    }
                    dx < -120f && abs(dx) > abs(dy) -> {
                        launchGestureApp(prefs.gestureLeftPkg); true
                    }
                    dx > 120f && abs(dx) > abs(dy) -> {
                        launchGestureApp(prefs.gestureRightPkg); true
                    }
                    else -> false
                }
            }
        })
    }

    // ─── Entry & ambient animations ───────────────────────────────────────────

    private fun animateHomeEntry() {
        if (!isFirstResume) return
        isFirstResume = false
        val interp = FastOutSlowInInterpolator()
        listOf(
            binding.tvGreeting   to 0L,
            binding.tvClock      to 40L,
            binding.tvDate       to 80L,
            binding.tvWidget     to 100L,
            binding.tvTimerTime  to 140L,
            binding.tvTimerLabel to 140L,
            binding.rvHomeApps   to 180L,
            binding.tvEmptyHint  to 180L,
            binding.tvDrawerHint to 240L,
        ).forEach { (view, delay) ->
            view.alpha        = 0f
            view.translationY = 22f
            view.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(360)
                .setInterpolator(interp).start()
        }
    }

    private fun animateDrawerHint() {
        hintAnimator?.cancel()
        hintAnimator = ObjectAnimator.ofFloat(binding.tvDrawerHint, "translationY", 0f, -9f, 0f).apply {
            duration     = 1600
            startDelay   = 2000
            repeatCount  = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun openDrawer() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val opts = ActivityOptions.makeCustomAnimation(this, R.anim.slide_up_in, 0)
        startActivity(Intent(this, AppDrawerActivity::class.java), opts.toBundle())
    }

    private fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar") ?: return
            sb.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {}
    }

    private fun launchGestureApp(pkg: String) {
        if (pkg.isEmpty()) return
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (_: Exception) {}
    }

    // dispatchTouchEvent (not onTouchEvent) so gestures fire even when
    // a child view (RecyclerView, clock, hint button) consumes the touch.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!tourActive) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ─── Spotlight tour ───────────────────────────────────────────────────────

    private data class TourStep(
        val getTarget: (() -> View)?,   // null = no spotlight (full overlay)
        val title: String,
        val body: String,
        val isLast: Boolean = false,
        val showBlockerPicker: Boolean = false,  // tap opens app-picker before advancing
        val showNamePicker: Boolean = false      // tap opens name input before advancing
    )

    private val tourSteps: List<TourStep> by lazy {
        listOf(
            TourStep(
                null,
                "a quick tour.",
                "let's walk through everything on your home screen."
            ),
            TourStep(
                { binding.tvGreeting },
                "greeting.",
                "updates through the day — morning, afternoon, evening, night.\nwhat should we call you?",
                showNamePicker = true
            ),
            TourStep(
                { binding.tvClock },
                "clock.",
                "tap to switch between 12h and 24h.\nlong press to open settings."
            ),
            TourStep(
                { binding.tvWidget },
                "info bar.",
                "weather, battery, screen time, sunrise & sunset,\nworld clock and countdown.\nall configurable in settings."
            ),
            TourStep(
                { binding.tvTimerTime },
                "focus timer.",
                "tap to start a 25-minute session.\ntap again to pause. long press to reset.\nevery 4 sessions earns a long break."
            ),
            TourStep(
                { if (binding.rvHomeApps.visibility == View.VISIBLE) binding.rvHomeApps
                  else binding.tvEmptyHint },
                "home apps.",
                "long press any app in the app drawer to pin it here.\nlong press a pinned app to remove it."
            ),
            TourStep(
                { binding.tvDrawerHint },
                "app drawer.",
                "swipe up anywhere on this screen — or tap the arrow.\nsearch by name, or use the a–z bar on the right.\nlong press any app for options."
            ),
            TourStep(
                null,
                "gestures.",
                "swipe left or right on the home screen\nto instantly launch any app.\nassign them in settings → gestures."
            ),
            TourStep(
                null,
                "distraction blocker.",
                "pick apps you want a nudge before opening —\nyoutube, instagram, whatever pulls you in.\nyou can always change this in settings.",
                showBlockerPicker = true
            ),
            TourStep(
                null,
                "you're all set.",
                "long press the clock anytime to return to settings.\nenjoy minimal.",
                isLast = true
            )
        )
    }

    private fun startTour() {
        tourActive = true
        tourStep   = 0
        binding.tourOverlay.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.tourOverlay.alpha = 0f
        binding.tourOverlay.visibility = View.VISIBLE
        binding.tourOverlay.setOnClickListener { advanceTour() }
        renderTourStep(animate = false)
        binding.tourOverlay.animate().alpha(1f).setDuration(340)
            .setInterpolator(FastOutSlowInInterpolator()).start()
    }

    private fun advanceTour() {
        if (!tourActive) return
        val current = tourSteps[tourStep]
        if (current.showNamePicker) {
            showTourNamePicker()
            return
        }
        if (current.showBlockerPicker) {
            showTourBlockerPicker()
            return
        }
        tourStep++
        if (tourStep >= tourSteps.size) endTour() else renderTourStep(animate = true)
    }

    private fun showTourNamePicker() {
        val dp = resources.displayMetrics.density
        val field = android.widget.EditText(this).apply {
            setText(prefs.userName)
            hint = "your name"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#777777"))
            background = null
            typeface = prefs.launcherTypeface()
            textSize = 17f
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
            setSingleLine(true)
            setSelection(text.length)
            requestFocus()
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("what's your name?")
            .setView(field)
            .setPositiveButton("save") { _, _ ->
                prefs.userName = field.text.toString().trim()
                updateGreeting()
                tourStep++
                if (tourStep >= tourSteps.size) endTour() else renderTourStep(animate = true)
            }
            .setNegativeButton("skip") { _, _ ->
                tourStep++
                if (tourStep >= tourSteps.size) endTour() else renderTourStep(animate = true)
            }
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun showTourBlockerPicker() {
        val apps = allInstalledApps.sortedBy { it.label.lowercase() }
        val labels   = apps.map { it.label }.toTypedArray()
        val checked  = BooleanArray(apps.size) { prefs.isBlocked(apps[it].packageName) }
        AlertDialog.Builder(this)
            .setTitle("block distracting apps")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("done") { _, _ ->
                apps.forEachIndexed { i, app ->
                    if (checked[i]) prefs.blockApp(app.packageName)
                    else prefs.unblockApp(app.packageName)
                }
                tourStep++
                if (tourStep >= tourSteps.size) endTour() else renderTourStep(animate = true)
            }
            .setNegativeButton("skip") { _, _ ->
                tourStep++
                if (tourStep >= tourSteps.size) endTour() else renderTourStep(animate = true)
            }
            .show()
    }

    private fun renderTourStep(animate: Boolean) {
        val step = tourSteps[tourStep]

        fun apply() {
            binding.tourTitle.text = step.title
            binding.tourBody.text  = step.body
            binding.tourHint.text  = when {
                step.isLast            -> "let's go →"
                step.showNamePicker    -> "tap to enter your name →"
                step.showBlockerPicker -> "tap to choose apps →"
                else                   -> "tap anywhere to continue"
            }

            val target = step.getTarget?.invoke()
            binding.spotlightView.highlight(target?.let { getSpotlightRect(it) })
            positionTooltip(target)
        }

        if (animate) {
            binding.tourTooltip.animate().alpha(0f).setDuration(110).withEndAction {
                apply()
                binding.tourTooltip.animate().alpha(1f).setDuration(220).start()
            }.start()
        } else {
            apply()
        }
    }

    private fun getSpotlightRect(view: View): RectF {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val pad = 22 * resources.displayMetrics.density
        return RectF(
            loc[0] - pad,
            loc[1] - pad,
            (loc[0] + view.width) + pad,
            (loc[1] + view.height) + pad
        )
    }

    private fun positionTooltip(target: View?) {
        val lp = binding.tourTooltip.layoutParams as FrameLayout.LayoutParams
        if (target == null) {
            lp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            binding.tourTooltip.layoutParams = lp
            return
        }
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val targetCenterY = loc[1] + target.height / 2f
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        lp.gravity = if (targetCenterY < screenH * 0.52f)
            Gravity.BOTTOM or Gravity.START
        else
            Gravity.TOP or Gravity.START
        binding.tourTooltip.layoutParams = lp
    }

    private fun endTour() {
        tourActive = false
        prefs.hasSeenOnboarding = true
        binding.tourOverlay.animate().alpha(0f).setDuration(280)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                binding.tourOverlay.visibility = View.GONE
                binding.tourOverlay.alpha = 1f
                binding.tourOverlay.setLayerType(View.LAYER_TYPE_NONE, null)
            }.start()
    }

    companion object {
        private const val REQ_PERMISSIONS       = 1001
        private const val REQ_ROLE_HOME         = 1002
        private const val FOCUS_MS              = 25 * 60 * 1000L
        private const val BREAK_MS              =  5 * 60 * 1000L
        private const val LONG_BREAK_MS         = 15 * 60 * 1000L
        private const val SCREEN_TIME_GOAL_MS   =  4 * 60 * 60 * 1000L  // 4 hours
    }
}
