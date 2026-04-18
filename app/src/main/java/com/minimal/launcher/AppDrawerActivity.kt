package com.minimal.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimal.launcher.databinding.ActivityAppDrawerBinding
import kotlin.math.abs

class AppDrawerActivity : AppCompatActivity() {

    private companion object {
        const val RC_CONTACTS = 1001
    }

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppListAdapter
    private lateinit var prefs: PrefsManager
    private lateinit var packageReceiver: PackageReceiver

    private var allApps: List<AppInfo> = emptyList()
    private var currentQuery: String = ""
    private var letterPositionMap: Map<Char, Int> = emptyMap()
    private lateinit var swipeDownDetector: GestureDetector

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        setupSwipeDown()
        setupAppList()
        setupSearch()
        setupAlphabetIndex()
        setupPackageReceiver()
        loadApps()

        binding.tvCloseHandle.setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentQuery.isNotEmpty()) binding.etSearch.text.clear()
                else finish()
            }
        })
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

    override fun onResume() {
        super.onResume()
        applyFullscreen()
        val tf = prefs.launcherTypeface()
        adapter.fontSizeSp = prefs.fontSizeSp()
        adapter.typeface   = tf
        binding.etSearch.typeface = tf
        refreshLayoutMode()
        loadApps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.autoKeyboard) {
            binding.etSearch.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CONTACTS) refreshList()
    }

    override fun finish() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.slide_down_out)
        }
        super.finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_down_out)
        }
    }

    // ─── Swipe-down to dismiss ────────────────────────────────────────────────

    private fun setupSwipeDown() {
        swipeDownDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                val dy = e2.y - (e1?.y ?: 0f)
                val dx = abs(e2.x - (e1?.x ?: 0f))
                if (dy > 120f && velocityY > 400f && dy > dx) {
                    val lm = binding.rvApps.layoutManager as? LinearLayoutManager
                    if (lm == null || lm.findFirstCompletelyVisibleItemPosition() == 0) {
                        finish(); return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDownDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ─── App list ─────────────────────────────────────────────────────────────

    private fun setupAppList() {
        adapter = AppListAdapter(
            onAppClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                launchApp(app)
            },
            onAppLongClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showAppOptions(app)
                true
            },
            onSettingsClick = { action ->
                try { startActivity(Intent(action)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            },
            onContactClick = { number ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
            }
        )
        binding.rvApps.adapter = adapter
        binding.rvApps.itemAnimator = null
        refreshLayoutMode()

        // Request contacts permission so search can surface contacts
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_CONTACTS), RC_CONTACTS)
        }
    }

    private fun refreshLayoutMode() {
        val isGrid = prefs.displayMode == "grid"
        adapter.gridMode   = isGrid
        adapter.fontSizeSp = prefs.fontSizeSp()

        val currentIsGrid = binding.rvApps.layoutManager is GridLayoutManager
        if (binding.rvApps.layoutManager == null || isGrid != currentIsGrid) {
            binding.rvApps.layoutManager = if (isGrid) {
                GridLayoutManager(this, 2).also { glm ->
                    glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(pos: Int) = adapter.getSpanSize(pos)
                    }
                }
            } else {
                LinearLayoutManager(this)
            }
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val list = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        else
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)

        val hidden = prefs.getHiddenPackages()
        allApps = list
            .mapNotNull { ri ->
                val label = ri.loadLabel(pm).toString().trim()
                if (label.isEmpty()) null
                else AppInfo(label, ri.activityInfo.packageName, ri.activityInfo.name)
            }
            .sortedBy { it.label.lowercase() }
            .filter { it.packageName != packageName && it.packageName !in hidden }

        refreshList()
        scheduleListAnimation()
    }

    private fun buildListItems(): List<AppListAdapter.Item> {
        if (currentQuery.isNotBlank()) {
            val q = currentQuery.trim()
            val items = mutableListOf<AppListAdapter.Item>()

            // Settings matches
            val settingsMatches = SearchProvider.searchSettings(q)
            if (settingsMatches.isNotEmpty()) {
                items.add(AppListAdapter.Item.Header("Settings"))
                settingsMatches.forEach { items.add(AppListAdapter.Item.SettingsResult(it.label, it.action)) }
            }

            // Contact matches (only if permission granted)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) {
                val contactMatches = SearchProvider.searchContacts(this, q)
                if (contactMatches.isNotEmpty()) {
                    items.add(AppListAdapter.Item.Header("Contacts"))
                    contactMatches.forEach { items.add(AppListAdapter.Item.Contact(it.name, it.number)) }
                }
            }

            // App matches
            val appMatches = allApps.filter { it.label.contains(q, ignoreCase = true) }
            if (appMatches.isNotEmpty()) {
                if (items.isNotEmpty()) items.add(AppListAdapter.Item.Header("Apps"))
                appMatches.forEach { items.add(AppListAdapter.Item.App(it)) }
            }

            return items
        }

        val items = mutableListOf<AppListAdapter.Item>()
        var currentSection: Char? = null
        allApps.forEach { app ->
            val ch = app.label.firstOrNull()?.uppercaseChar()
            val section = if (ch != null && ch in 'A'..'Z') ch else '#'
            if (section != currentSection) {
                currentSection = section
                items.add(AppListAdapter.Item.Header(section.toString()))
            }
            items.add(AppListAdapter.Item.App(app))
        }
        return items
    }

    private fun buildLetterMap(items: List<AppListAdapter.Item>): Map<Char, Int> {
        val map = mutableMapOf<Char, Int>()
        items.forEachIndexed { index, item ->
            when (item) {
                is AppListAdapter.Item.Header -> {
                    val ch = item.title.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
                    if (ch in 'A'..'Z') map[ch] = index
                }
                is AppListAdapter.Item.App -> {
                    val ch = item.info.label.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
                    if (ch in 'A'..'Z' && ch !in map) map[ch] = index
                }
                is AppListAdapter.Item.SettingsResult,
                is AppListAdapter.Item.Contact -> Unit
            }
        }
        return map
    }

    private var isFirstLoad = true

    private fun scheduleListAnimation() {
        if (!isFirstLoad) return
        isFirstLoad = false
        val anim = AnimationUtils.loadAnimation(this, R.anim.item_fade_slide_in)
        val controller = LayoutAnimationController(anim).apply { delay = 0.04f }
        binding.rvApps.layoutAnimation = controller
        binding.rvApps.scheduleLayoutAnimation()
    }

    private fun refreshList() {
        val items = buildListItems()
        adapter.submitList(items)
        letterPositionMap = buildLetterMap(items)

        val searching = currentQuery.isNotBlank()
        binding.alphabetIndex.visibility = if (searching) View.GONE else View.VISIBLE
        if (searching) {
            binding.tvWebSearch.text = "Search the web for \"$currentQuery\""
            if (binding.tvWebSearch.visibility != View.VISIBLE) {
                binding.tvWebSearch.alpha = 0f
                binding.tvWebSearch.visibility = View.VISIBLE
                binding.tvWebSearch.animate().alpha(1f).setDuration(150).start()
            }
        } else {
            if (binding.tvWebSearch.visibility == View.VISIBLE) {
                binding.tvWebSearch.animate().alpha(0f).setDuration(100).withEndAction {
                    binding.tvWebSearch.visibility = View.GONE
                }.start()
            }
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString() ?: ""
                refreshList()
            }
        })
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                launchWebSearch(binding.etSearch.text.toString()); true
            } else false
        }
        binding.tvWebSearch.setOnClickListener { launchWebSearch(binding.etSearch.text.toString()) }
    }

    private fun launchWebSearch(query: String) {
        if (query.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
    }

    // ─── Alphabet index ───────────────────────────────────────────────────────

    private fun setupAlphabetIndex() {
        binding.alphabetIndex.onLetterSelected = { letter ->
            letterPositionMap[letter]?.let { pos ->
                (binding.rvApps.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(pos, 0)
            }
        }
    }

    // ─── Package receiver ─────────────────────────────────────────────────────

    private fun setupPackageReceiver() {
        packageReceiver = PackageReceiver { loadApps() }
        registerReceiver(packageReceiver, packageReceiver.buildIntentFilter())
    }

    // ─── App actions ──────────────────────────────────────────────────────────

    private fun launchApp(app: AppInfo) {
        if (prefs.isBlocked(app.packageName)) {
            showBlockedDialog(app)
            return
        }
        doLaunchApp(app)
    }

    private fun showBlockedDialog(app: AppInfo) {
        hideKeyboard()
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
        hideKeyboard()
        prefs.recordLaunch(app.packageName)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try { startActivity(intent); finish() }
        catch (_: Exception) { Toast.makeText(this, "Could not open ${app.label}", Toast.LENGTH_SHORT).show() }
    }

    private fun openAppInfo(app: AppInfo) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun showAppOptions(app: AppInfo) {
        val isOnHome = prefs.isPinned(app.packageName)
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf(
                if (isOnHome) "Remove from home screen" else "Add to home screen",
                "Hide from list",
                "App info"
            )) { _, which ->
                when (which) {
                    0 -> { prefs.togglePin(app.packageName); refreshList() }
                    1 -> { prefs.hideApp(app.packageName); loadApps() }
                    2 -> openAppInfo(app)
                }
            }
            .show()
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}
