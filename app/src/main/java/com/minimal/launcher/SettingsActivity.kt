package com.minimal.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.minimal.launcher.databinding.ActivitySettingsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    // All installed apps (for gesture picker and hide picker)
    private var installedApps: List<AppInfo> = emptyList()

    // File picker for backup import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val ok = BackupManager.import(this, prefs, uri)
        Toast.makeText(this,
            if (ok) "Settings restored" else "Restore failed — invalid file",
            Toast.LENGTH_SHORT).show()
        if (ok) applyAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.settings_enter, R.anim.fade_out)

        prefs = PrefsManager(this)
        loadInstalledApps()
        applyAll()
        wireListeners()
    }

    override fun onResume() {
        super.onResume()
        applyAll()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.settings_exit)
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
        // Name
        binding.optName.text = prefs.userName.ifEmpty { "tap to set" }

        // Alignment
        setSelected(binding.optAlignLeft,   prefs.homeAlignment == "left")
        setSelected(binding.optAlignCenter, prefs.homeAlignment == "center")
        setSelected(binding.optAlignRight,  prefs.homeAlignment == "right")

        // Status bar
        setSelected(binding.optStatusbarShow, !prefs.fullscreenMode)
        setSelected(binding.optStatusbarHide,  prefs.fullscreenMode)

        // Clock format
        setSelected(binding.opt24h, prefs.is24Hour)
        setSelected(binding.opt12h, !prefs.is24Hour)

        // UI font
        setSelected(binding.optFontSans,  prefs.launcherFont == "sans-serif")
        setSelected(binding.optFontSerif, prefs.launcherFont == "serif")
        setSelected(binding.optFontMono,  prefs.launcherFont == "mono")

        // UI weight
        setSelected(binding.optUiThin,    prefs.launcherWeight == "thin")
        setSelected(binding.optUiLight,   prefs.launcherWeight == "light")
        setSelected(binding.optUiRegular, prefs.launcherWeight == "regular")
        setSelected(binding.optUiMedium,  prefs.launcherWeight == "medium")

        // Clock size
        setSelected(binding.optClockSmall,  prefs.clockSize == "small")
        setSelected(binding.optClockMedium, prefs.clockSize == "medium")
        setSelected(binding.optClockLarge,  prefs.clockSize == "large")

        // Clock weight
        setSelected(binding.optWeightThin,    prefs.clockWeight == "thin")
        setSelected(binding.optWeightLight,   prefs.clockWeight == "light")
        setSelected(binding.optWeightRegular, prefs.clockWeight == "regular")

        // Home apps mode
        setSelected(binding.optHomePinned,   prefs.homeTab == "pinned")
        setSelected(binding.optHomeFrequent, prefs.homeTab == "frequent")

        // Display mode
        setSelected(binding.optList, prefs.displayMode == "list")
        setSelected(binding.optGrid, prefs.displayMode == "grid")

        // Font size
        setSelected(binding.optFontSmall,  prefs.fontSize == "small")
        setSelected(binding.optFontMedium, prefs.fontSize == "medium")
        setSelected(binding.optFontLarge,  prefs.fontSize == "large")

        // Top padding
        setSelected(binding.optPaddingCompact,  prefs.topPadding == "compact")
        setSelected(binding.optPaddingDefault,  prefs.topPadding == "default")
        setSelected(binding.optPaddingRelaxed,  prefs.topPadding == "relaxed")
        setSelected(binding.optPaddingSpacious, prefs.topPadding == "spacious")

        // Timer visibility
        setSelected(binding.optTimerShow,  prefs.showTimer)
        setSelected(binding.optTimerHide, !prefs.showTimer)

        // Drawer keyboard
        setSelected(binding.optKeyboardOn,   prefs.autoKeyboard)
        setSelected(binding.optKeyboardOff, !prefs.autoKeyboard)

        // Sunrise / sunset
        setSelected(binding.optSunriseShow,  prefs.showSunriseSunset)
        setSelected(binding.optSunriseHide, !prefs.showSunriseSunset)

        // Temperature unit
        setSelected(binding.optCelsius,    prefs.tempUnit == "celsius")
        setSelected(binding.optFahrenheit, prefs.tempUnit == "fahrenheit")

        // World clock
        binding.optWorldClock.text =
            if (prefs.worldClockZone.isEmpty()) "none"
            else WorldClocks.labelFor(prefs.worldClockZone)

        // Days until
        binding.optDaysUntil.text = buildDaysUntilLabel()

        // Gesture labels
        binding.optGestureLeft.text  = labelForPkg(prefs.gestureLeftPkg)
        binding.optGestureRight.text = labelForPkg(prefs.gestureRightPkg)

        // Wind-down
        setSelected(binding.optWindDownOn,  prefs.windDownEnabled)
        setSelected(binding.optWindDownOff, !prefs.windDownEnabled)
        binding.optWindDownTime.text = prefs.windDownTime
        binding.optWindDownTime.alpha = if (prefs.windDownEnabled) 1f else 0.3f

        // Blocked apps list
        rebuildBlockedAppsUI()
        // Hidden apps list
        rebuildHiddenAppsUI()
        // Delayed apps list
        rebuildDelayedAppsUI()
    }

    private fun setSelected(tv: TextView, selected: Boolean) {
        val target = if (selected) Color.WHITE else Color.parseColor("#777777")
        val from   = tv.currentTextColor
        if (from == target) return
        ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
            duration    = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { tv.setTextColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun labelForPkg(pkg: String): String {
        if (pkg.isEmpty()) return "none"
        return installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    }

    // ── Wire click listeners ───────────────────────────────────────────────────

    private fun wireListeners() {
        // Name
        binding.optName.setOnClickListener { pickName() }

        // Alignment
        binding.optAlignLeft.setOnClickListener   { prefs.homeAlignment = "left";   applyAll() }
        binding.optAlignCenter.setOnClickListener { prefs.homeAlignment = "center"; applyAll() }
        binding.optAlignRight.setOnClickListener  { prefs.homeAlignment = "right";  applyAll() }

        // Status bar
        binding.optStatusbarShow.setOnClickListener { prefs.fullscreenMode = false; applyAll() }
        binding.optStatusbarHide.setOnClickListener { prefs.fullscreenMode = true;  applyAll() }

        // Clock format
        binding.opt24h.setOnClickListener { prefs.is24Hour = true;  applyAll() }
        binding.opt12h.setOnClickListener { prefs.is24Hour = false; applyAll() }

        // UI font
        binding.optFontSans.setOnClickListener  { prefs.launcherFont = "sans-serif"; applyAll() }
        binding.optFontSerif.setOnClickListener { prefs.launcherFont = "serif";      applyAll() }
        binding.optFontMono.setOnClickListener  { prefs.launcherFont = "mono";       applyAll() }

        // UI weight
        binding.optUiThin.setOnClickListener    { prefs.launcherWeight = "thin";    applyAll() }
        binding.optUiLight.setOnClickListener   { prefs.launcherWeight = "light";   applyAll() }
        binding.optUiRegular.setOnClickListener { prefs.launcherWeight = "regular"; applyAll() }
        binding.optUiMedium.setOnClickListener  { prefs.launcherWeight = "medium";  applyAll() }

        // Clock size
        binding.optClockSmall.setOnClickListener  { prefs.clockSize = "small";  applyAll() }
        binding.optClockMedium.setOnClickListener { prefs.clockSize = "medium"; applyAll() }
        binding.optClockLarge.setOnClickListener  { prefs.clockSize = "large";  applyAll() }

        // Clock weight
        binding.optWeightThin.setOnClickListener    { prefs.clockWeight = "thin";    applyAll() }
        binding.optWeightLight.setOnClickListener   { prefs.clockWeight = "light";   applyAll() }
        binding.optWeightRegular.setOnClickListener { prefs.clockWeight = "regular"; applyAll() }

        // Home apps mode
        binding.optHomePinned.setOnClickListener   { prefs.homeTab = "pinned";   applyAll() }
        binding.optHomeFrequent.setOnClickListener { prefs.homeTab = "frequent"; applyAll() }

        // Display mode
        binding.optList.setOnClickListener { prefs.displayMode = "list"; applyAll() }
        binding.optGrid.setOnClickListener { prefs.displayMode = "grid"; applyAll() }

        // Font size
        binding.optFontSmall.setOnClickListener  { prefs.fontSize = "small";  applyAll() }
        binding.optFontMedium.setOnClickListener { prefs.fontSize = "medium"; applyAll() }
        binding.optFontLarge.setOnClickListener  { prefs.fontSize = "large";  applyAll() }

        // Top padding
        binding.optPaddingCompact.setOnClickListener  { prefs.topPadding = "compact";  applyAll() }
        binding.optPaddingDefault.setOnClickListener  { prefs.topPadding = "default";  applyAll() }
        binding.optPaddingRelaxed.setOnClickListener  { prefs.topPadding = "relaxed";  applyAll() }
        binding.optPaddingSpacious.setOnClickListener { prefs.topPadding = "spacious"; applyAll() }

        // Timer visibility
        binding.optTimerShow.setOnClickListener { prefs.showTimer = true;  applyAll() }
        binding.optTimerHide.setOnClickListener { prefs.showTimer = false; applyAll() }

        // Drawer keyboard
        binding.optKeyboardOn.setOnClickListener  { prefs.autoKeyboard = true;  applyAll() }
        binding.optKeyboardOff.setOnClickListener { prefs.autoKeyboard = false; applyAll() }

        // Sunrise / sunset
        binding.optSunriseShow.setOnClickListener { prefs.showSunriseSunset = true;  applyAll() }
        binding.optSunriseHide.setOnClickListener { prefs.showSunriseSunset = false; applyAll() }

        // Temperature
        binding.optCelsius.setOnClickListener    { prefs.tempUnit = "celsius";    applyAll() }
        binding.optFahrenheit.setOnClickListener { prefs.tempUnit = "fahrenheit"; applyAll() }

        // World clock + days until
        binding.optWorldClock.setOnClickListener { pickWorldClock() }
        binding.optDaysUntil.setOnClickListener  { pickDaysUntil() }

        // Gesture pickers
        binding.optGestureLeft.setOnClickListener  { pickGestureApp(isLeft = true) }
        binding.optGestureRight.setOnClickListener { pickGestureApp(isLeft = false) }

        // Wind-down
        binding.optWindDownOn.setOnClickListener  { prefs.windDownEnabled = true;  applyAll() }
        binding.optWindDownOff.setOnClickListener { prefs.windDownEnabled = false; applyAll() }
        binding.optWindDownTime.setOnClickListener { pickWindDownTime() }

        // Block app
        binding.btnBlockApp.setOnClickListener { pickAppToBlock() }

        // Hide app
        binding.btnHideApp.setOnClickListener { pickAppToHide() }

        // Delay app
        binding.btnDelayApp.setOnClickListener { pickAppToDelay() }

        // Backup export
        binding.btnBackupExport.setOnClickListener {
            val ok = BackupManager.export(this, prefs)
            Toast.makeText(this,
                if (ok) "Backup saved to Downloads" else "Export failed",
                Toast.LENGTH_SHORT).show()
        }

        // Backup import
        binding.btnBackupImport.setOnClickListener {
            importLauncher.launch("*/*")
        }

    }

    // ── World clock picker ────────────────────────────────────────────────────

    private fun pickWorldClock() {
        val entries = listOf("none" to "") + WorldClocks.zones
        val labels  = entries.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("world clock")
            .setItems(labels) { _, which ->
                prefs.worldClockZone = entries[which].second
                applyAll()
            }
            .show()
    }

    // ── Days until picker ─────────────────────────────────────────────────────

    private fun pickDaysUntil() {
        val dp = resources.displayMetrics.density
        val field = EditText(this).apply {
            setText(prefs.daysUntilLabel)
            hint = "event name  (e.g. holidays)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#777777"))
            background = null
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textSize = 17f
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
            setSingleLine(true)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("days until what?")
            .setView(field)
            .setPositiveButton("pick date") { _, _ ->
                val label = field.text.toString().trim()
                if (label.isEmpty()) return@setPositiveButton
                // Pre-fill the date picker with an existing date if available
                val cal = Calendar.getInstance()
                prefs.daysUntilDate.takeIf { it.isNotEmpty() }?.let { ds ->
                    try { cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(ds)!! }
                    catch (_: Exception) {}
                }
                DatePickerDialog(
                    this,
                    { _, y, m, d ->
                        val picked = Calendar.getInstance().also { it.set(y, m, d) }
                        prefs.daysUntilLabel = label
                        prefs.daysUntilDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(picked.time)
                        applyAll()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            .setNegativeButton("clear") { _, _ ->
                prefs.daysUntilLabel = ""
                prefs.daysUntilDate  = ""
                applyAll()
            }
            .show()
    }

    private fun buildDaysUntilLabel(): String {
        val label = prefs.daysUntilLabel.trim()
        val date  = prefs.daysUntilDate.trim()
        if (label.isEmpty()) return "tap to set"
        if (date.isEmpty())  return label
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!
            val display = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
            "$label  ·  $display"
        } catch (_: Exception) { label }
    }

    // ── Name picker ───────────────────────────────────────────────────────────

    private fun pickName() {
        val dp = resources.displayMetrics.density
        val field = EditText(this).apply {
            setText(prefs.userName)
            hint = "your name"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#777777"))
            background = null
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textSize = 17f
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
            setSingleLine(true)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("what's your name?")
            .setView(field)
            .setPositiveButton("save") { _, _ ->
                prefs.userName = field.text.toString().trim()
                applyAll()
            }
            .setNegativeButton("clear") { _, _ ->
                prefs.userName = ""
                applyAll()
            }
            .show()
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

    // ── Wind-down time picker ─────────────────────────────────────────────────

    private fun pickWindDownTime() {
        val parts = prefs.windDownTime.split(":").mapNotNull { it.toIntOrNull() }
        val hour  = parts.getOrNull(0) ?: 22
        val min   = parts.getOrNull(1) ?: 0
        TimePickerDialog(this, { _, h, m ->
            prefs.windDownTime = "%02d:%02d".format(h, m)
            applyAll()
        }, hour, min, true).show()
    }

    // ── Blocked (distraction) apps ────────────────────────────────────────────

    private fun pickAppToBlock() {
        val notBlocked = installedApps.filter { !prefs.isBlocked(it.packageName) }
        if (notBlocked.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("block an app")
            .setItems(notBlocked.map { it.label }.toTypedArray()) { _, which ->
                prefs.blockApp(notBlocked[which].packageName)
                applyAll()
            }
            .show()
    }

    private fun rebuildBlockedAppsUI() {
        binding.llBlockedApps.removeAllViews()
        val blocked = prefs.getBlockedPackages()
        if (blocked.isEmpty()) {
            binding.llBlockedApps.addView(makeInfoText("none"))
            return
        }
        blocked.forEach { pkg ->
            val label = installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
            val row = makeBlockedAppRow(label, pkg)
            binding.llBlockedApps.addView(row)
        }
    }

    private fun makeBlockedAppRow(label: String, pkg: String): View {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }

        return TextView(this).apply {
            layoutParams = lp
            text = "$label  ↩ unblock"
            textSize = 15f
            setTextColor(Color.parseColor("#AAAAAA"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setOnClickListener {
                prefs.unblockApp(pkg)
                applyAll()
            }
        }
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
            setTextColor(Color.parseColor("#AAAAAA"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setOnClickListener {
                prefs.unhideApp(pkg)
                applyAll()
            }
        }
    }

    // ── Open-delay apps ───────────────────────────────────────────────────────

    private fun pickAppToDelay() {
        val notDelayed = installedApps.filter { !prefs.isDelayed(it.packageName) }
        if (notDelayed.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("add 5s delay to app")
            .setItems(notDelayed.map { it.label }.toTypedArray()) { _, which ->
                prefs.toggleDelay(notDelayed[which].packageName)
                applyAll()
            }
            .show()
    }

    private fun rebuildDelayedAppsUI() {
        binding.llDelayedApps.removeAllViews()
        val delayed = prefs.getDelayedPackages()
        if (delayed.isEmpty()) {
            binding.llDelayedApps.addView(makeInfoText("none"))
            return
        }
        delayed.forEach { pkg ->
            val label = installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
            binding.llDelayedApps.addView(makeDelayedAppRow(label, pkg))
        }
    }

    private fun makeDelayedAppRow(label: String, pkg: String): View {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
        return TextView(this).apply {
            layoutParams = lp
            text = "$label  ↩ remove"
            textSize = 15f
            setTextColor(Color.parseColor("#AAAAAA"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setOnClickListener { prefs.toggleDelay(pkg); applyAll() }
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
            setTextColor(Color.parseColor("#777777"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
    }
}
