package com.example.data.model

import java.util.Locale

data class LyricLine(
    val id: Int,
    val timeMs: Long?,
    val text: String
)

object LrcParser {
    private val lrcRegex = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)$""")

    fun parse(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val lines = raw.lines()
        val parsed = mutableListOf<LyricLine>()
        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            val match = lrcRegex.matchEntire(trimmed)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val millis = when (fracStr.length) {
                    0 -> 0L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = min * 60_000L + sec * 1_000L + millis
                val lyricText = match.groupValues[4].trim()
                if (lyricText.isNotEmpty()) {
                    parsed.add(LyricLine(id = index, timeMs = totalMs, text = lyricText))
                }
            } else {
                // Skip metadata tags like [ar:...], [ti:...]
                if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                    parsed.add(LyricLine(id = index, timeMs = null, text = trimmed))
                }
            }
        }
        return parsed
    }

    fun hasTimestamps(lines: List<LyricLine>): Boolean = lines.any { it.timeMs != null }

    fun findActiveIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var active = -1
        for (i in lines.indices) {
            val t = lines[i].timeMs ?: continue
            if (t <= positionMs + 150L) {
                active = i
            } else {
                break
            }
        }
        return active
    }

    fun formatTimestamp(timeMs: Long): String {
        val clamped = timeMs.coerceAtLeast(0L)
        val minutes = clamped / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val centis = (clamped % 1_000L) / 10L
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis)
    }

    fun serialize(lines: List<LyricLine>): String {
        return lines.joinToString("\n") { line ->
            val t = line.timeMs
            if (t != null) {
                "${formatTimestamp(t)} ${line.text}"
            } else {
                line.text
            }
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }
}
