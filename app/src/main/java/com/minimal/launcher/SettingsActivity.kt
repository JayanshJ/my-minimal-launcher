package com.minimal.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.minimal.launcher.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    // All installed apps (for gesture picker and hide picker)
    private var installedApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        loadInstalledApps()
        applyAll()
        wireListeners()
    }

    override fun onResume() {
        super.onResume()
        applyAll()
    }

    // ── Load installed apps ───────────────────────────────────────────────────

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val list = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        else
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)

        installedApps = list
            .mapNotNull { ri ->
                val label = ri.loadLabel(pm).toString().trim()
                if (label.isEmpty()) null
                else AppInfo(label, ri.activityInfo.packageName, ri.activityInfo.name)
            }
            .sortedBy { it.label.lowercase() }
            .filter { it.packageName != packageName }
    }

    // ── Apply all current prefs to the UI ─────────────────────────────────────

    private fun applyAll() {
        // Clock format
        setSelected(binding.opt24h, prefs.is24Hour)
        setSelected(binding.opt12h, !prefs.is24Hour)

        // Display mode
        setSelected(binding.optList, prefs.displayMode == "list")
        setSelected(binding.optGrid, prefs.displayMode == "grid")

        // Font size
        setSelected(binding.optFontSmall,  prefs.fontSize == "small")
        setSelected(binding.optFontMedium, prefs.fontSize == "medium")
        setSelected(binding.optFontLarge,  prefs.fontSize == "large")

        // Timer visibility
        setSelected(binding.optTimerShow,  prefs.showTimer)
        setSelected(binding.optTimerHide, !prefs.showTimer)

        // Drawer keyboard
        setSelected(binding.optKeyboardOn,   prefs.autoKeyboard)
        setSelected(binding.optKeyboardOff, !prefs.autoKeyboard)

        // Temperature unit
        setSelected(binding.optCelsius,    prefs.tempUnit == "celsius")
        setSelected(binding.optFahrenheit, prefs.tempUnit == "fahrenheit")

        // Gesture labels
        binding.optGestureLeft.text  = labelForPkg(prefs.gestureLeftPkg)
        binding.optGestureRight.text = labelForPkg(prefs.gestureRightPkg)

        // Hidden apps list
        rebuildHiddenAppsUI()
    }

    private fun setSelected(tv: TextView, selected: Boolean) {
        tv.setTextColor(if (selected) Color.WHITE else Color.parseColor("#555555"))
    }

    private fun labelForPkg(pkg: String): String {
        if (pkg.isEmpty()) return "none"
        return installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    }

    // ── Wire click listeners ───────────────────────────────────────────────────

    private fun wireListeners() {
        // Clock format
        binding.opt24h.setOnClickListener { prefs.is24Hour = true;  applyAll() }
        binding.opt12h.setOnClickListener { prefs.is24Hour = false; applyAll() }

        // Display mode
        binding.optList.setOnClickListener { prefs.displayMode = "list"; applyAll() }
        binding.optGrid.setOnClickListener { prefs.displayMode = "grid"; applyAll() }

        // Font size
        binding.optFontSmall.setOnClickListener  { prefs.fontSize = "small";  applyAll() }
        binding.optFontMedium.setOnClickListener { prefs.fontSize = "medium"; applyAll() }
        binding.optFontLarge.setOnClickListener  { prefs.fontSize = "large";  applyAll() }

        // Timer visibility
        binding.optTimerShow.setOnClickListener { prefs.showTimer = true;  applyAll() }
        binding.optTimerHide.setOnClickListener { prefs.showTimer = false; applyAll() }

        // Drawer keyboard
        binding.optKeyboardOn.setOnClickListener  { prefs.autoKeyboard = true;  applyAll() }
        binding.optKeyboardOff.setOnClickListener { prefs.autoKeyboard = false; applyAll() }

        // Temperature
        binding.optCelsius.setOnClickListener    { prefs.tempUnit = "celsius";    applyAll() }
        binding.optFahrenheit.setOnClickListener { prefs.tempUnit = "fahrenheit"; applyAll() }

        // Gesture pickers
        binding.optGestureLeft.setOnClickListener  { pickGestureApp(isLeft = true) }
        binding.optGestureRight.setOnClickListener { pickGestureApp(isLeft = false) }

        // Hide app
        binding.btnHideApp.setOnClickListener { pickAppToHide() }
    }

    // ── Gesture app picker ────────────────────────────────────────────────────

    private fun pickGestureApp(isLeft: Boolean) {
        val labels = arrayOf("none") + installedApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isLeft) "Swipe left launches…" else "Swipe right launches…")
            .setItems(labels) { _, which ->
                val pkg = if (which == 0) "" else installedApps[which - 1].packageName
                if (isLeft) prefs.gestureLeftPkg = pkg else prefs.gestureRightPkg = pkg
                applyAll()
            }
            .show()
    }

    // ── Hidden apps ───────────────────────────────────────────────────────────

    private fun pickAppToHide() {
        val visible = installedApps.filter { it.packageName !in prefs.getHiddenPackages() }
        if (visible.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Hide app")
            .setItems(visible.map { it.label }.toTypedArray()) { _, which ->
                prefs.hideApp(visible[which].packageName)
                applyAll()
            }
            .show()
    }

    private fun rebuildHiddenAppsUI() {
        binding.llHiddenApps.removeAllViews()
        val hidden = prefs.getHiddenPackages()
        if (hidden.isEmpty()) {
            binding.llHiddenApps.addView(makeInfoText("none"))
            return
        }
        hidden.forEach { pkg ->
            val label = installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
            val row = makeHiddenAppRow(label, pkg)
            binding.llHiddenApps.addView(row)
        }
    }

    private fun makeHiddenAppRow(label: String, pkg: String): View {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }

        return TextView(this).apply {
            layoutParams = lp
            text = "$label  ↩ unhide"
            textSize = 15f
            setTextColor(Color.parseColor("#888888"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setOnClickListener {
                prefs.unhideApp(pkg)
                applyAll()
            }
        }
    }

    private fun makeInfoText(text: String): TextView {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
        return TextView(this).apply {
            layoutParams = lp
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#555555"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
    }
}
