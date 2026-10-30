package com.aistra.hail.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.HailData.tags
import com.aistra.hail.databinding.FragmentHomeBinding
import com.aistra.hail.extensions.applyDefaultInsetter
import com.aistra.hail.extensions.isLandscape
import com.aistra.hail.extensions.isRtl
import com.aistra.hail.extensions.paddingRelative
import com.aistra.hail.ui.main.MainFragment
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : MainFragment() {
    var multiselect: Boolean = false
    val selectedList: MutableList<AppInfo> = mutableListOf()
    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        if (tags.size == 1) binding.tabs.isVisible = false
        binding.pager.adapter = HomeAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tags[position].first
        }.attach()
        binding.tabs.post { bindTagLongPress() }
        binding.tabs.applyDefaultInsetter { paddingRelative(isRtl, start = !activity.isLandscape, end = true) }
        return binding.root
    }

    fun selectedTagId(): Int = tags.getOrNull(binding.pager.currentItem)?.second ?: 0

    fun refreshTags(preferredTagId: Int?) {
        val pagerAdapter = binding.pager.adapter as? HomeAdapter ?: return
        pagerAdapter.notifyDataSetChanged()
        binding.tabs.isVisible = tags.size > 1
        binding.tabs.post {
            bindTagLongPress()
            val position = tags.indexOfFirst { it.second == preferredTagId }
            binding.pager.setCurrentItem(position.takeIf { it >= 0 } ?: 0, false)
        }
    }

    private fun bindTagLongPress() {
        for (position in 0 until binding.tabs.tabCount) {
            binding.tabs.getTabAt(position)?.view?.setOnLongClickListener {
                if (isResumed) TagEditorDialog(this).show()
                true
            }
        }
    }

    override fun onDestroyView() {
        multiselect = false
        selectedList.clear()
        super.onDestroyView()
        _binding = null
    }
}