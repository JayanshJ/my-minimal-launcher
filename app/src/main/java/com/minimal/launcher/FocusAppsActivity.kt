package com.minimal.launcher

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimal.launcher.databinding.ActivityFocusAppsBinding

class FocusAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusAppsBinding
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: FocusAppAdapter
    private var allApps: List<AppEntry> = emptyList()

    data class AppEntry(val label: String, val packageName: String, var allowed: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        window.statusBarColor   = Color.BLACK
        window.navigationBarColor = Color.BLACK

        loadApps()

        adapter = FocusAppAdapter(allApps) { entry ->
            if (entry.allowed) prefs.allowApp(entry.packageName)
            else prefs.disallowApp(entry.packageName)
            // Re-apply whitelist immediately if dumb-phone mode is on
            if (prefs.dumbPhoneEnabled) {
                AppLockManager.applyWhitelist(this, prefs.getAllowedPackages())
            }
        }

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.lowercase() ?: ""
                adapter.filter(query)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadApps() {
        val pm      = packageManager
        val allowed = prefs.getAllowedPackages()
        allApps = pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                      pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { info ->
                AppEntry(
                    label       = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    allowed     = info.packageName in allowed
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.allowed }.thenBy { it.label.lowercase() })
    }

    inner class FocusAppAdapter(
        private val source: List<AppEntry>,
        private val onToggle: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<FocusAppAdapter.VH>() {

        private var displayed = source.toMutableList()

        fun filter(query: String) {
            displayed = if (query.isEmpty()) source.toMutableList()
            else source.filter { it.label.lowercase().contains(query) }.toMutableList()
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView    = view.findViewById(R.id.tv_app_name)
            val toggle: TextView  = view.findViewById(R.id.tv_allowed)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_focus_app, parent, false))

        override fun getItemCount() = displayed.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = displayed[position]
            holder.name.text = entry.label

            if (entry.allowed) {
                holder.toggle.text      = "allowed"
                holder.toggle.setTextColor(Color.WHITE)
                holder.name.setTextColor(Color.WHITE)
            } else {
                holder.toggle.text      = "blocked"
                holder.toggle.setTextColor(Color.parseColor("#444444"))
                holder.name.setTextColor(Color.parseColor("#666666"))
            }

            holder.itemView.setOnClickListener {
                entry.allowed = !entry.allowed
                onToggle(entry)
                notifyItemChanged(position)
            }
        }
    }
}
