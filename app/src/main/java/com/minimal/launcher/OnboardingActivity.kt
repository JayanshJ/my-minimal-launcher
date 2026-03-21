package com.minimal.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.minimal.launcher.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    // ─── Page model ──────────────────────────────────────────────────────────

    private data class Page(
        val title: String,
        val body: String,
        val showDefaultButton: Boolean = false,
        val isFinal: Boolean = false
    )

    private val pages = listOf(
        Page(
            title = "minimal.",
            body  = "your phone, simplified."
        ),
        Page(
            title = "your apps.",
            body  = "swipe up from anywhere\nto open the app drawer."
        ),
        Page(
            title = "pin what matters.",
            body  = "long-press any app in the drawer\nto pin it to your home screen."
        ),
        Page(
            title = "gestures.",
            body  = "swipe left or right on the home screen\nto instantly launch any app."
        ),
        Page(
            title = "set as default.",
            body  = "make minimal your home screen\nso it opens on the home button.",
            showDefaultButton = true
        ),
        Page(
            title = "you're set.",
            body  = "",
            isFinal = true
        )
    )

    // ─── State ────────────────────────────────────────────────────────────────

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: PrefsManager
    private var currentPage = 0
    private val dots = mutableListOf<View>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        buildDots()
        showPage(0, animate = false)

        binding.btnNext.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            advance()
        }

        binding.btnSetDefault.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            promptDefaultLauncher()
        }
    }

    // ─── Page navigation ──────────────────────────────────────────────────────

    private fun advance() {
        if (pages[currentPage].isFinal) {
            finishOnboarding()
        } else {
            showPage(currentPage + 1, animate = true)
        }
    }

    private fun showPage(index: Int, animate: Boolean) {
        currentPage = index
        val page = pages[index]

        if (animate) {
            // Crossfade content
            val contentViews = listOf(binding.tvTitle, binding.tvBody, binding.btnSetDefault)
            contentViews.forEach { v ->
                v.animate().alpha(0f).setDuration(120).withEndAction {
                    applyPageContent(page)
                    v.animate().alpha(1f).setDuration(200).start()
                }.start()
            }
        } else {
            applyPageContent(page)
        }

        // Dots
        updateDots(index)

        // Next button label
        binding.btnNext.text = if (page.isFinal) "let's go →" else "next →"
    }

    private fun applyPageContent(page: Page) {
        binding.tvTitle.text = page.title
        binding.tvBody.text  = page.body

        if (page.showDefaultButton) {
            if (binding.btnSetDefault.visibility != View.VISIBLE) {
                binding.btnSetDefault.alpha      = 0f
                binding.btnSetDefault.visibility = View.VISIBLE
                binding.btnSetDefault.animate().alpha(1f).setDuration(200).start()
            }
        } else {
            binding.btnSetDefault.visibility = View.GONE
        }
    }

    // ─── Dot indicator ────────────────────────────────────────────────────────

    private fun buildDots() {
        val sizePx    = (6 * resources.displayMetrics.density).toInt()
        val marginPx  = (8 * resources.displayMetrics.density).toInt()

        pages.forEachIndexed { i, _ ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                    if (i < pages.lastIndex) it.marginEnd = marginPx
                }
                background = makeCircle(if (i == 0) Color.WHITE else Color.parseColor("#555555"))
            }
            binding.llDots.addView(dot)
            dots.add(dot)
        }
    }

    private fun updateDots(activeIndex: Int) {
        dots.forEachIndexed { i, dot ->
            val target = if (i == activeIndex) Color.WHITE else Color.parseColor("#555555")
            (dot.background as? GradientDrawable)?.setColor(target)
        }
    }

    private fun makeCircle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    // ─── Default launcher prompt ──────────────────────────────────────────────

    private fun promptDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),
                    REQ_ROLE_HOME
                )
                return
            }
        }
        // Pre-Q fallback: open home selector
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        // Role result — nothing else needed; role is held or not.
    }

    // ─── Finish ───────────────────────────────────────────────────────────────

    private fun finishOnboarding() {
        prefs.hasSeenOnboarding = true
        // MainActivity is already beneath us in the back stack — just pop back to it
        finish()
    }

    companion object {
        private const val REQ_ROLE_HOME = 1010
    }
}
