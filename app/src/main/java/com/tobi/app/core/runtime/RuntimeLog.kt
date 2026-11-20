package com.tobi.app.core.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(
    val timestamp: Long,
    val level: String,
    val connectorId: String?,
    val message: String
)

object RuntimeLog {
    private const val MAX_ENTRIES = 500
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun log(level: String, message: String, connectorId: String? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level.lowercase(),
            connectorId = connectorId,
            message = message
        )
        _entries.update { current ->
            val next = current + entry
            if (next.size > MAX_ENTRIES) next.takeLast(MAX_ENTRIES) else next
        }
        android.util.Log.println(
            when (entry.level) {
                "error" -> android.util.Log.ERROR
                "warn" -> android.util.Log.WARN
                else -> android.util.Log.INFO
            },
            "TobiBridge",
            message
        )
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
