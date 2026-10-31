package app.mtmux.core.model

/**
 * Last-sampled host memory / root-disk remaining (from the most recent SSH).
 * Values are KiB as reported by /proc/meminfo and `df -Pk /`.
 */
data class HostResources(
    val memAvailKb: Long? = null,
    val memTotalKb: Long? = null,
    val diskAvailKb: Long? = null,
    val diskTotalKb: Long? = null,
) {
    val isEmpty: Boolean get() = memAvailKb == null && diskAvailKb == null
}

object HostResourceText {
    fun parseKv(raw: String): HostResources {
        val map = raw.lineSequence().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
        return HostResources(
            memAvailKb = map["MEM_AVAIL_KB"]?.toLongOrNull(),
            memTotalKb = map["MEM_TOTAL_KB"]?.toLongOrNull(),
            diskAvailKb = map["DISK_AVAIL_KB"]?.toLongOrNull(),
            diskTotalKb = map["DISK_TOTAL_KB"]?.toLongOrNull(),
        )
    }

    /** Compact remaining size: 48G / 1.2G / 512M / 80K */
    fun formatKb(kb: Long): String {
        if (kb < 0L) return "—"
        val g = kb / 1_048_576.0
        val m = kb / 1024.0
        return when {
            g >= 10 -> "${g.toInt()}G"
            g >= 1.0 -> {
                val t = String.format(java.util.Locale.US, "%.1f", g)
                t.removeSuffix(".0") + "G"
            }
            m >= 1 -> "${m.toInt()}M"
            else -> "${kb}K"
        }
    }

    fun memLow(avail: Long?, total: Long?): Boolean {
        if (avail == null) return false
        if (avail < 256L * 1024L) return true
        if (total != null && total > 0 && avail * 10 < total) return true
        return false
    }

    fun diskLow(avail: Long?, total: Long?): Boolean {
        if (avail == null) return false
        if (avail < 2L * 1_048_576L) return true
        if (total != null && total > 0 && avail * 10 < total) return true
        return false
    }
}
