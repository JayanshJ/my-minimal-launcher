package com.minimal.launcher

import android.content.Intent
import android.content.pm.PackageManager
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

        // Handle back: clear search first, then close drawer
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentQuery.isNotEmpty()) binding.etSearch.text.clear()
                else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Re-apply settings that may have changed
        adapter.fontSizeSp = prefs.fontSizeSp()
        refreshLayoutMode()
        loadApps()
    }

    // onWindowFocusChanged is the correct place to show the soft keyboard —
    // the window is fully laid out and attached by this point.
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

    // Slide down when exiting — use the non-deprecated API on API 34+
    override fun finish() {
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
                // Only close on a clearly downward fling, not a sideways one
                if (dy > 120f && velocityY > 400f && dy > dx) {
                    val lm = binding.rvApps.layoutManager as? LinearLayoutManager
                    // Only dismiss if the list is scrolled to the very top
                    if (lm == null || lm.findFirstCompletelyVisibleItemPosition() == 0) {
                        finish()
                        return true
                    }
                }
                return false
            }
        })
    }

    // dispatchTouchEvent so the gesture detector sees events even when
    // the RecyclerView consumes them (e.g. while scrolling).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDownDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ─── App list ─────────────────────────────────────────────────────────────

    private fun setupAppList() {
        adapter = AppListAdapter(
            onAppClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                launchApp(app)
            },
            onAppLongClick = { app ->
                binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAppOptions(app)
                true
            }
        )
        binding.rvApps.adapter = adapter
        refreshLayoutMode()
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
    }

    private fun buildListItems(): List<AppListAdapter.Item> {
        if (currentQuery.isNotBlank()) {
            return allApps
                .filter { it.label.contains(currentQuery.trim(), ignoreCase = true) }
                .map { AppListAdapter.Item.App(it) }
        }
        // Build sectioned list grouped by first character
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
                // Prefer mapping the letter to its section header position
                is AppListAdapter.Item.Header -> {
                    val ch = item.title.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
                    if (ch in 'A'..'Z') map[ch] = index
                }
                // Fall back to first app if no header (e.g. during search)
                is AppListAdapter.Item.App -> {
                    val ch = item.info.label.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
                    if (ch in 'A'..'Z' && ch !in map) map[ch] = index
                }
            }
        }
        return map
    }

    private fun refreshList() {
        val items = buildListItems()
        adapter.submitList(items)
        letterPositionMap = buildLetterMap(items)

        val searching = currentQuery.isNotBlank()
        binding.alphabetIndex.visibility = if (searching) View.GONE  else View.VISIBLE
        binding.tvWebSearch.visibility   = if (searching) View.VISIBLE else View.GONE
        if (searching) binding.tvWebSearch.text = "Search the web for \"$currentQuery\""
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
        hideKeyboard()
        prefs.recordLaunch(app.packageName)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try {
            startActivity(intent)
            finish()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open ${app.label}", Toast.LENGTH_SHORT).show()
        }
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
                "App info",
                "Hide from list"
            )) { _, which ->
                when (which) {
                    0 -> { prefs.togglePin(app.packageName); refreshList() }
                    1 -> openAppInfo(app)
                    2 -> { prefs.hideApp(app.packageName); loadApps() }
                }
            }
            .show()
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

}
