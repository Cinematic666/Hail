package com.aistra.hail.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aistra.hail.app.HailData.tags

class HomeAdapter(fragment: HomeFragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = tags.size

    override fun getItemId(position: Int): Long = TagOrder.stableItemId(tags[position].second)

    override fun containsItem(itemId: Long): Boolean =
        tags.any { TagOrder.stableItemId(it.second) == itemId }

    override fun createFragment(position: Int): Fragment = PagerFragment()
}
