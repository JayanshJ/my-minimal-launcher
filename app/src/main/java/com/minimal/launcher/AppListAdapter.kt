package com.minimal.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ── Item model ────────────────────────────────────────────────────────────

    sealed class Item {
        data class Header(val title: String) : Item()
        data class App(val info: AppInfo) : Item()
    }

    private companion object {
        const val TYPE_HEADER   = 0
        const val TYPE_APP_LIST = 1
        const val TYPE_APP_GRID = 2
    }

    // ── Mutable display properties (set by MainActivity) ──────────────────────

    /** sp value applied to every app-row label. */
    var fontSizeSp: Float = 17f

    /** When true, inflate the compact grid cell layout instead of the list layout. */
    var gridMode: Boolean = false

    // ── Data ──────────────────────────────────────────────────────────────────

    private var items: List<Item> = emptyList()

    fun submitList(newItems: List<Item>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val old = items[o]; val new = newItems[n]
                return when {
                    old is Item.Header && new is Item.Header -> old.title == new.title
                    old is Item.App    && new is Item.App    -> old.info.packageName == new.info.packageName
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Returns the span size for a given position.
     * Used by [GridLayoutManager.SpanSizeLookup]: headers fill both columns;
     * app cells fill one column.
     */
    fun getSpanSize(position: Int): Int =
        if (items.getOrNull(position) is Item.Header) 2 else 1

    // ── ViewHolders ───────────────────────────────────────────────────────────

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_section_header)
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tv_app_label)
    }

    // ── RecyclerView overrides ────────────────────────────────────────────────

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Header -> TYPE_HEADER
        is Item.App    -> if (gridMode) TYPE_APP_GRID else TYPE_APP_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER   -> HeaderViewHolder(inf.inflate(R.layout.item_section_header, parent, false))
            TYPE_APP_GRID -> AppViewHolder(inf.inflate(R.layout.item_app_grid, parent, false))
            else          -> AppViewHolder(inf.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderViewHolder).title.text = item.title
            is Item.App    -> with(holder as AppViewHolder) {
                label.text     = item.info.label
                label.textSize = fontSizeSp
                itemView.setOnClickListener     { onAppClick(item.info) }
                itemView.setOnLongClickListener { onAppLongClick(item.info) }
            }
        }
    }
}
