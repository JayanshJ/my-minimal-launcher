package com.minimal.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Boolean,
    private val onSettingsClick: ((String) -> Unit)? = null,
    private val onContactClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ── Item model ────────────────────────────────────────────────────────────

    sealed class Item {
        data class Header(val title: String) : Item()
        data class App(val info: AppInfo) : Item()
        data class SettingsResult(val label: String, val action: String) : Item()
        data class Contact(val name: String, val number: String) : Item()
    }

    private companion object {
        const val TYPE_HEADER         = 0
        const val TYPE_APP_LIST       = 1
        const val TYPE_APP_GRID       = 2
        const val TYPE_SETTINGS       = 3
        const val TYPE_CONTACT        = 4
    }

    // ── Display properties ────────────────────────────────────────────────────

    var fontSizeSp: Float = 17f
    var typeface: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    var gridMode: Boolean = false
    var textGravity: Int = android.view.Gravity.START

    // ── Data ──────────────────────────────────────────────────────────────────

    private var items: List<Item> = emptyList()

    fun submitList(newItems: List<Item>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val old = items[o]; val new = newItems[n]
                return when {
                    old is Item.Header         && new is Item.Header         -> old.title == new.title
                    old is Item.App            && new is Item.App            -> old.info.packageName == new.info.packageName
                    old is Item.SettingsResult && new is Item.SettingsResult -> old.action == new.action
                    old is Item.Contact        && new is Item.Contact        -> old.name == new.name
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    fun getSpanSize(position: Int): Int =
        if (items.getOrNull(position) is Item.Header) 2 else 1

    // ── ViewHolders ───────────────────────────────────────────────────────────

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_section_header)
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tv_app_label)
    }

    class SearchResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tv_result_label)
        val hint: TextView  = view.findViewById(R.id.tv_result_hint)
    }

    // ── RecyclerView overrides ────────────────────────────────────────────────

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Header         -> TYPE_HEADER
        is Item.App            -> if (gridMode) TYPE_APP_GRID else TYPE_APP_LIST
        is Item.SettingsResult -> TYPE_SETTINGS
        is Item.Contact        -> TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER   -> HeaderViewHolder(inf.inflate(R.layout.item_section_header, parent, false))
            TYPE_APP_GRID -> AppViewHolder(inf.inflate(R.layout.item_app_grid, parent, false))
            TYPE_SETTINGS,
            TYPE_CONTACT  -> SearchResultViewHolder(inf.inflate(R.layout.item_search_result, parent, false))
            else          -> AppViewHolder(inf.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderViewHolder).title.text = item.title

            is Item.App -> with(holder as AppViewHolder) {
                label.text     = item.info.label
                label.textSize = fontSizeSp
                label.typeface = typeface
                label.gravity  = textGravity
                label.setTextColor(Color.WHITE)

                itemView.setOnClickListener     { onAppClick(item.info) }
                itemView.setOnLongClickListener { onAppLongClick(item.info) }
                itemView.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN ->
                            v.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.75f)
                                .setDuration(70).setInterpolator(DecelerateInterpolator()).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                                .setDuration(220).setInterpolator(OvershootInterpolator(1.8f)).start()
                    }
                    false
                }
            }

            is Item.SettingsResult -> with(holder as SearchResultViewHolder) {
                label.text = item.label
                hint.text  = "settings →"
                itemView.setOnClickListener { onSettingsClick?.invoke(item.action) }
            }

            is Item.Contact -> with(holder as SearchResultViewHolder) {
                label.text = item.name
                hint.text  = "call →"
                itemView.setOnClickListener { onContactClick?.invoke(item.number) }
            }
        }
    }
}
