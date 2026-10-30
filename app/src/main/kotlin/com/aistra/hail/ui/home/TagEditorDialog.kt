package com.aistra.hail.ui.home

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.abs

class TagEditorDialog(private val home: HomeFragment) {
    private val context: Context get() = home.requireContext()
    private val originalTags = HailData.tags.toList()
    private val workingTags = originalTags.toMutableList()
    private lateinit var recyclerView: RecyclerView
    private lateinit var tagAdapter: TagAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    fun show() {
        val selectedTagId = home.selectedTagId()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20), context.dp(8), context.dp(20), context.dp(8))
        }
        val hint = TextView(context).apply {
            text = context.getString(R.string.tag_editor_hint)
            textSize = 13f
            alpha = 0.78f
            setPadding(0, 0, 0, context.dp(8))
        }
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
        }
        tagAdapter = TagAdapter()
        recyclerView.adapter = tagAdapter

        val addButton = MaterialButton(context).apply {
            text = context.getString(R.string.action_tag_add)
            setOnClickListener { promptTagName(null) }
        }

        content.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, context.dp(320)
        ))
        content.addView(addButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder.bindingAdapterPosition <= 0) makeMovementFlags(0, 0)
                else super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }).also { it.attachToRecyclerView(recyclerView) }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_manage_tags)
            .setView(content)
            .setPositiveButton(R.string.action_save) { _, _ -> save(selectedTagId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun moveItem(from: Int, to: Int): Boolean {
        if (!TagOrder.move(workingTags, from, to)) return false
        tagAdapter.notifyItemMoved(from, to)
        tagAdapter.notifyItemRangeChanged(minOf(from, to), abs(from - to) + 1)
        return true
    }

    private fun promptTagName(position: Int?) {
        val oldTag = position?.let { workingTags.getOrNull(it) }
        val editText = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(oldTag?.first.orEmpty())
            setSelection(text?.length ?: 0)
        }
        val inputLayout = TextInputLayout(context).apply {
            hint = context.getString(R.string.tag)
            addView(editText, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val inputContainer = FrameLayout(context).apply {
            setPadding(context.dp(24), context.dp(8), context.dp(24), 0)
            addView(inputLayout)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (oldTag == null) R.string.action_tag_add else R.string.action_tag_rename)
            .setView(inputContainer)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = editText.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    editText.error = context.getString(R.string.tag_name_required)
                    return@setOnClickListener
                }
                if (workingTags.any {
                    it.second != oldTag?.second && it.first.equals(name, ignoreCase = true)
                }) {
                    editText.error = context.getString(R.string.tag_duplicate_name)
                    return@setOnClickListener
                }

                if (oldTag == null) {
                    val id = name.hashCode()
                    if (workingTags.any { it.second == id }) {
                        editText.error = context.getString(R.string.tag_duplicate_id)
                        return@setOnClickListener
                    }
                    workingTags.add(name to id)
                    tagAdapter.notifyItemInserted(workingTags.lastIndex)
                    recyclerView.scrollToPosition(workingTags.lastIndex)
                } else {
                    val index = workingTags.indexOfFirst { it.second == oldTag.second }
                    if (index >= 0) {
                        // Keep the ID stable: existing app assignments remain untouched.
                        workingTags[index] = name to oldTag.second
                        tagAdapter.notifyItemChanged(index)
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(position: Int) {
        if (position <= 0) return
        val tag = workingTags.getOrNull(position) ?: return
        if (tag.second == 0) return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_tag_remove)
            .setMessage(context.getString(R.string.tag_delete_confirm, tag.first))
            .setPositiveButton(R.string.action_tag_remove) { _, _ ->
                workingTags.removeAt(position)
                tagAdapter.notifyItemRemoved(position)
                tagAdapter.notifyItemRangeChanged(position, workingTags.size - position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save(selectedTagId: Int) {
        val originalIds = originalTags.map { it.second }.toSet()
        val currentIds = workingTags.map { it.second }.toSet()
        val removedIds = originalIds - currentIds

        if (removedIds.isNotEmpty()) {
            val iterator = HailData.checkedList.iterator()
            while (iterator.hasNext()) {
                val appInfo = iterator.next()
                appInfo.tagIdList.removeAll(removedIds)
                if (appInfo.tagIdList.isEmpty()) iterator.remove()
            }
            HailData.saveApps()
        }

        val defaultTag = workingTags.firstOrNull { it.second == 0 }
        if (defaultTag != null) {
            workingTags.remove(defaultTag)
            workingTags.add(0, defaultTag)
        }
        HailData.tags.clear()
        HailData.tags.addAll(workingTags)
        HailData.saveTags()

        val nextSelectedTagId = workingTags.firstOrNull { it.second == selectedTagId }?.second ?: 0
        home.refreshTags(nextSelectedTagId)
    }

    private inner class TagAdapter : RecyclerView.Adapter<TagViewHolder>() {
        override fun getItemCount(): Int = workingTags.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(56)
                setPadding(context.dp(4), context.dp(2), context.dp(2), context.dp(2))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val handle = actionView("⠿", R.string.tag_drag, 22f)
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(context).apply {
                textSize = 16f
                maxLines = 1
                setTypeface(typeface, Typeface.BOLD)
            }
            val subtitle = TextView(context).apply {
                textSize = 12f
                alpha = 0.72f
                maxLines = 1
            }
            labels.addView(title)
            labels.addView(subtitle)
            val up = actionView("↑", R.string.tag_move_up)
            val down = actionView("↓", R.string.tag_move_down)
            val edit = actionView("✎", R.string.tag_edit)
            val delete = actionView("×", R.string.tag_delete)

            row.addView(handle, LinearLayout.LayoutParams(context.dp(36), context.dp(48)))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(up, LinearLayout.LayoutParams(context.dp(36), context.dp(48)))
            row.addView(down, LinearLayout.LayoutParams(context.dp(36), context.dp(48)))
            row.addView(edit, LinearLayout.LayoutParams(context.dp(36), context.dp(48)))
            row.addView(delete, LinearLayout.LayoutParams(context.dp(36), context.dp(48)))
            return TagViewHolder(row, handle, title, subtitle, up, down, edit, delete)
        }

        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            val tag = workingTags[position]
            val fixed = position == 0 || tag.second == 0
            val count = HailData.checkedList.count { tag.second in it.tagIdList }
            holder.title.text = tag.first
            holder.subtitle.text = context.resources.getQuantityString(R.plurals.tag_app_count, count, count)
            holder.handle.text = if (fixed) "🔒" else "⠿"
            holder.handle.contentDescription = context.getString(
                if (fixed) R.string.tag_default_locked else R.string.tag_drag
            )
            holder.handle.isEnabled = !fixed
            holder.up.isEnabled = !fixed && position > 1
            holder.down.isEnabled = !fixed && position < itemCount - 1
            holder.edit.isEnabled = !fixed
            holder.delete.isEnabled = !fixed
            listOf(holder.handle, holder.up, holder.down, holder.edit, holder.delete).forEach {
                it.alpha = if (it.isEnabled) 1f else 0.35f
            }

            holder.handle.setOnTouchListener { _, event ->
                if (fixed) {
                    false
                } else {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper.startDrag(holder)
                    true
                }
            }
            holder.up.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) moveItem(index, index - 1)
            }
            holder.down.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) moveItem(index, index + 1)
            }
            holder.edit.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index > 0 && index != RecyclerView.NO_POSITION) promptTagName(index)
            }
            holder.delete.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index > 0 && index != RecyclerView.NO_POSITION) confirmDelete(index)
            }
        }
    }

    private fun actionView(symbol: String, description: Int, size: Float = 20f): TextView =
        TextView(context).apply {
            text = symbol
            textSize = size
            gravity = Gravity.CENTER
            minWidth = context.dp(36)
            minHeight = context.dp(44)
            contentDescription = context.getString(description)
            isClickable = true
            isFocusable = true
            val value = TypedValue()
            if (context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, value, true
                ) && value.resourceId != 0
            ) setBackgroundResource(value.resourceId)
        }

    private class TagViewHolder(
        row: View,
        val handle: TextView,
        val title: TextView,
        val subtitle: TextView,
        val up: TextView,
        val down: TextView,
        val edit: TextView,
        val delete: TextView
    ) : RecyclerView.ViewHolder(row)
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
