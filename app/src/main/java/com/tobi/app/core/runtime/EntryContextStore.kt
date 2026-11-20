package com.tobi.app.core.runtime

import com.tobi.app.core.protocol.EntryCard
import java.util.concurrent.ConcurrentHashMap

object EntryContextStore {
    private const val MAX_ENTRIES = 200
    private val entries = ConcurrentHashMap<String, EntryCard>()
    private val order = ArrayDeque<String>()

    @Synchronized
    fun put(connectorId: String, entry: EntryCard) {
        val key = key(connectorId, entry.uid)
        entries[key] = entry
        order.remove(key)
        order.addLast(key)
        while (order.size > MAX_ENTRIES) {
            entries.remove(order.removeFirst())
        }
    }

    fun get(connectorId: String, uid: String): EntryCard? = entries[key(connectorId, uid)]

    private fun key(connectorId: String, uid: String): String = "$connectorId\u0000$uid"
}
