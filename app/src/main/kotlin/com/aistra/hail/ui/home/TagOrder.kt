package com.aistra.hail.ui.home

internal object TagOrder {
    fun move(tags: MutableList<Pair<String, Int>>, from: Int, to: Int): Boolean {
        if (from !in tags.indices || to !in tags.indices || from == to) return false
        if (from == 0 || to == 0 || tags[from].second == 0 || tags[to].second == 0) return false
        val item = tags.removeAt(from)
        tags.add(to, item)
        return true
    }
}
