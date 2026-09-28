package com.clipsync.android.service

/** Timestamp-ordered offers; the visible offer is never preempted by another PC. */
class PairingQueue<T>(private val timestamp: (T) -> Long) {
    private val waiting = mutableListOf<T>()
    private var active: T? = null
    val size: Int get() = waiting.size + if (active == null) 0 else 1
    fun add(item: T) { waiting.add(item); waiting.sortBy(timestamp) }
    fun firstOrNull(): T? {
        if (active == null && waiting.isNotEmpty()) active = waiting.removeAt(0)
        return active
    }
    fun first(): T = checkNotNull(firstOrNull())
    fun remove(item: T) { if (active == item) active = null else waiting.remove(item) }
}
