package com.minimal.launcher

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.graphics.Color
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.app.ActivityOptions
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

    // Calendar widget
    private val calendarHandler = Handler(Looper.getMainLooper())
    private val calendarRefreshRunnable = Runnable { loadCalendarEvents() }
    private var calendarText: String = ""

    // Battery
    private var batteryText: String = ""

    // Screen time
    private var screenTimeText: String = ""

    // Weather
    private var weatherText: String = ""
    private val weatherHandler = Handler(Looper.getMainLooper())
    private val weatherRefreshRunnable = Runnable { refreshWeather() }

    // Media polling (every 2 s while foregrounded)
    private val mediaHandler = Handler(Looper.getMainLooper())
    private val mediaPollRunnable = object : Runnable {
        override fun run() { updateMediaUI(); mediaHandler.postDelayed(this, 2_000L) }
    }

    // Pomodoro timer
    private val timerHandler      = Handler(Looper.getMainLooper())
    private var timerRunning      = false
    private var isFocusPhase      = true
    private var focusDoneCount    = 0          // completed focus sessions (long-break every 4th)
    private var remainingMs       = FOCUS_MS
    private var timerEndEpoch     = 0L         // epoch ms when current countdown ends
    private val timerTick         = Runnable { tickTimer() }

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
        requestCalendarPermission()
        requestLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        loadInstalledApps()
        loadHomeApps()
        updateClock()
        refreshBattery()
        refreshScreenTime()
        loadCalendarEvents()
        refreshWeather()
        mediaHandler.post(mediaPollRunnable)
        applyTimerVisibility()
    }

    override fun onPause() {
        super.onPause()
        mediaHandler.removeCallbacks(mediaPollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacksAndMessages(null)
        calendarHandler.removeCallbacksAndMessages(null)
        weatherHandler.removeCallbacksAndMessages(null)
        mediaHandler.removeCallbacksAndMessages(null)
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
        val now = Date()
        if (prefs.is24Hour) {
            binding.tvClock.text = clockFmt.format(now)
        } else {
            val timeStr = clockFmt.format(now)
            val ampm    = ampmFmt.format(now).lowercase()
            val span    = SpannableStringBuilder(timeStr).append("  ").append(ampm)
            val start   = timeStr.length + 2
            span.setSpan(RelativeSizeSpan(0.28f),
                start, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(Color.parseColor("#888888")),
                start, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.tvClock.text = span
        }
        binding.tvDate.text = dateFmt.format(now)
    }

    // ─── Widget (weather + calendar combined) ─────────────────────────────────

    private fun updateWidgetText() {
        val parts = mutableListOf<String>()
        // Line 1: "18°C  partly cloudy  ·  87%"
        val weatherLine = listOf(weatherText, batteryText)
            .filter { it.isNotEmpty() }
            .joinToString("  ·  ")
        if (weatherLine.isNotEmpty())    parts.add(weatherLine)
        // Line 2: "3h 24m screen time"
        if (screenTimeText.isNotEmpty()) parts.add(screenTimeText)
        // Line 3+: calendar events
        if (calendarText.isNotEmpty())   parts.add(calendarText)
        binding.tvWidget.text = parts.joinToString("\n")
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
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis, System.currentTimeMillis()
        )
        val totalMs = stats?.sumOf { it.totalTimeInForeground } ?: 0L
        val hours   = totalMs / 3_600_000L
        val minutes = (totalMs / 60_000L) % 60
        screenTimeText = when {
            totalMs <= 0L -> ""
            hours > 0L   -> "${hours}h ${minutes}m"
            else         -> "${minutes}m"
        }
        updateWidgetText()
    }

    // ─── Weather ──────────────────────────────────────────────────────────────

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        } else {
            refreshWeather()
        }
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

        val fahrenheit = prefs.tempUnit == "fahrenheit"
        lifecycleScope.launch {
            val data = WeatherManager.fetch(loc.latitude, loc.longitude, fahrenheit)
            weatherText = data?.display ?: ""
            updateWidgetText()
        }

        weatherHandler.removeCallbacks(weatherRefreshRunnable)
        weatherHandler.postDelayed(weatherRefreshRunnable, 15 * 60 * 1_000L)
    }

    // ─── Calendar ─────────────────────────────────────────────────────────────

    private fun requestCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
        } else {
            loadCalendarEvents()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CALENDAR -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                loadCalendarEvents()
            REQ_LOCATION -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                refreshWeather()
        }
    }

    private fun loadCalendarEvents() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return

        val now = System.currentTimeMillis()
        val end = now + 24 * 60 * 60 * 1_000L
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.ALL_DAY
        )
        val selection =
            "${CalendarContract.Events.DTSTART} >= ? AND " +
            "${CalendarContract.Events.DTSTART} <= ? AND " +
            "${CalendarContract.Events.DELETED} = 0"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection,
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC LIMIT 3"
            )
            val events = mutableListOf<String>()
            cursor?.let {
                val ti = it.getColumnIndex(CalendarContract.Events.TITLE)
                val si = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val ai = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                while (it.moveToNext()) {
                    val title = it.getString(ti) ?: continue
                    events.add(
                        if (it.getInt(ai) == 1) title
                        else "${timeFmt.format(Date(it.getLong(si)))}  $title"
                    )
                }
            }
            calendarText = events.joinToString("\n")
        } catch (_: Exception) {
            calendarText = ""
        } finally {
            cursor?.close()
        }
        updateWidgetText()
        calendarHandler.removeCallbacks(calendarRefreshRunnable)
        calendarHandler.postDelayed(calendarRefreshRunnable, 5 * 60 * 1_000L)
    }

    // ─── Media controls ───────────────────────────────────────────────────────

    private fun isNotificationListenerEnabled(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun getActiveMediaController(): MediaController? {
        if (!isNotificationListenerEnabled()) return null
        return try {
            val mgr = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn  = ComponentName(this, MediaNotificationListener::class.java)
            mgr.getActiveSessions(cn).firstOrNull()
        } catch (_: Exception) { null }
    }

    private fun updateMediaUI() {
        val mc = getActiveMediaController()
        if (mc == null) { binding.panelMedia.visibility = View.GONE; return }

        binding.panelMedia.visibility = View.VISIBLE

        val meta   = mc.metadata
        val title  = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
        binding.tvMediaInfo.text = when {
            title.isEmpty()  -> ""
            artist.isEmpty() -> title
            else             -> "$title  ·  $artist"
        }

        val isPlaying = mc.playbackState?.state == PlaybackState.STATE_PLAYING
        binding.btnMediaPlayPause.text = if (isPlaying) "pause" else "play"

        binding.btnMediaPrev.setOnClickListener      { mc.transportControls.skipToPrevious() }
        binding.btnMediaNext.setOnClickListener      { mc.transportControls.skipToNext() }
        binding.btnMediaPlayPause.setOnClickListener {
            if (isPlaying) mc.transportControls.pause() else mc.transportControls.play()
        }
    }

    // ─── Home app list ────────────────────────────────────────────────────────

    private fun setupHomeAppList() {
        adapter = AppListAdapter(
            onAppClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                launchApp(app)
            },
            onAppLongClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showHomeAppOptions(app)
                true
            }
        )
        binding.rvHomeApps.adapter = adapter
        binding.rvHomeApps.layoutManager = LinearLayoutManager(this)
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
        val pinned = prefs.getPinnedPackages()
        val homeApps = allInstalledApps
            .filter { it.packageName in pinned }
            .sortedBy { it.label.lowercase() }

        adapter.fontSizeSp = prefs.fontSizeSp()
        adapter.submitList(homeApps.map { AppListAdapter.Item.App(it) })

        val empty = homeApps.isEmpty()
        binding.tvEmptyHint.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvHomeApps.visibility  = if (empty) View.GONE    else View.VISIBLE
    }

    private fun launchApp(app: AppInfo) {
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
        binding.tvTimerTime.visibility  = vis
        binding.tvTimerLabel.visibility = vis
        if (!prefs.showTimer && timerRunning) pauseTimer()
    }

    private fun setupPomodoro() {
        updateTimerDisplay()
        val onTap: (View) -> Unit = {
            if (timerRunning) pauseTimer() else startTimer()
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        val onLongPress: (View) -> Boolean = {
            resetTimer()
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            true
        }
        binding.tvTimerTime.setOnClickListener(onTap)
        binding.tvTimerLabel.setOnClickListener(onTap)
        binding.tvTimerTime.setOnLongClickListener(onLongPress)
        binding.tvTimerLabel.setOnLongClickListener(onLongPress)
    }

    private fun startTimer() {
        timerRunning  = true
        timerEndEpoch = System.currentTimeMillis() + remainingMs
        timerHandler.post(timerTick)
        updateTimerDisplay()
    }

    private fun pauseTimer() {
        timerRunning  = false
        timerHandler.removeCallbacks(timerTick)
        remainingMs   = (timerEndEpoch - System.currentTimeMillis()).coerceAtLeast(0L)
        updateTimerDisplay()
    }

    private fun resetTimer() {
        timerRunning   = false
        isFocusPhase   = true
        focusDoneCount = 0
        remainingMs    = FOCUS_MS
        timerHandler.removeCallbacks(timerTick)
        updateTimerDisplay()
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

    private fun openDrawer() {
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
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        private const val REQ_CALENDAR  = 1001
        private const val REQ_LOCATION  = 1002
        private const val FOCUS_MS      = 25 * 60 * 1000L
        private const val BREAK_MS      =  5 * 60 * 1000L
        private const val LONG_BREAK_MS = 15 * 60 * 1000L
    }
}
