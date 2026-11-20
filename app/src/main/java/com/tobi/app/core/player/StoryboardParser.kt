package com.tobi.app.core.player

data class StoryboardCue(
    val startMs: Long,
    val endMs: Long,
    val imageUrl: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

object StoryboardParser {
    fun parse(vttText: String): List<StoryboardCue> {
        val cues = mutableListOf<StoryboardCue>()
        val blocks = vttText.split(Regex("""\r?\n\r?\n"""))
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timingLine = lines.firstOrNull { it.contains("-->") } ?: continue
            val parts = timingLine.split("-->").map { it.trim() }
            if (parts.size < 2) continue
            val start = parseTimestamp(parts[0]) ?: continue
            val end = parseTimestamp(parts[1].split(" ").first()) ?: continue
            val payload = lines.lastOrNull { it != timingLine } ?: continue
            val match = Regex("""(.+?)#xywh=(\d+),(\d+),(\d+),(\d+)""").find(payload) ?: continue
            cues.add(
                StoryboardCue(
                    startMs = start,
                    endMs = end,
                    imageUrl = match.groupValues[1],
                    x = match.groupValues[2].toIntOrNull() ?: 0,
                    y = match.groupValues[3].toIntOrNull() ?: 0,
                    w = match.groupValues[4].toIntOrNull() ?: 0,
                    h = match.groupValues[5].toIntOrNull() ?: 0
                )
            )
        }
        return cues
    }

    private fun parseTimestamp(value: String): Long? {
        val parts = value.split(":")
        return try {
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toDouble()
                    minutes * 60000 + (seconds * 1000).toLong()
                }
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toDouble()
                    hours * 3600000 + minutes * 60000 + (seconds * 1000).toLong()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun cueAt(cues: List<StoryboardCue>, positionMs: Long): StoryboardCue? {
        return cues.firstOrNull { positionMs in it.startMs..it.endMs }
    }
}
