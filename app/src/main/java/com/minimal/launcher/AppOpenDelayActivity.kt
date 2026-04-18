package com.minimal.launcher

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.minimal.launcher.databinding.ActivityAppOpenDelayBinding

/**
 * Full-screen 5-second countdown before opening an app.
 * The user must consciously wait — or actively cancel.
 */
class AppOpenDelayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppOpenDelayBinding
    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())

    private var remaining = DELAY_SECS
    private var cancelled = false

    private val tick = object : Runnable {
        override fun run() {
            if (cancelled) return
            remaining--
            if (remaining <= 0) {
                launchTarget()
            } else {
                animateCountdown(remaining)
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        binding = ActivityAppOpenDelayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "app"
        binding.tvDelayAppName.text  = label.lowercase()
        binding.tvDelayCount.text    = DELAY_SECS.toString()
        binding.tvDelayLabel.text    = "opening in $DELAY_SECS seconds"

        // Apply launcher typeface
        val tf = prefs.launcherTypeface()
        binding.tvDelayAppName.typeface = tf
        binding.tvDelayLabel.typeface   = tf
        binding.tvDelayCancel.typeface  = tf

        binding.tvDelayCancel.setOnClickListener {
            cancelled = true
            handler.removeCallbacks(tick)
            finish()
        }

        // Start ticking after 1 second (show "5" first)
        handler.postDelayed(tick, 1_000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
    }

    // ─── Countdown ────────────────────────────────────────────────────────────

    private fun animateCountdown(count: Int) {
        // Scale-down-out → update number → scale-up-in
        binding.tvDelayCount.animate()
            .scaleX(0.7f).scaleY(0.7f).alpha(0f)
            .setDuration(120)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                binding.tvDelayCount.text = count.toString()
                binding.tvDelayLabel.text = "opening in $count second${if (count == 1) "" else "s"}"
                binding.tvDelayCount.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }.start()
    }

    // ─── Launch ───────────────────────────────────────────────────────────────

    private fun launchTarget() {
        val pkg      = intent.getStringExtra(EXTRA_PKG)      ?: return
        val activity = intent.getStringExtra(EXTRA_ACTIVITY) ?: return
        prefs.recordLaunch(pkg)
        val i = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(pkg, activity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try { startActivity(i) } catch (_: Exception) { }
        finish()
    }

    companion object {
        const val EXTRA_PKG      = "delay_pkg"
        const val EXTRA_ACTIVITY = "delay_activity"
        const val EXTRA_LABEL    = "delay_label"
        const val DELAY_SECS     = 5
    }
}
