package com.ramxtreme.app.core

/** Analyse des sources réelles du noyau et du système. Aucune valeur n'est inventée : échec = null. */
object Parsers {
    private val spaces = Regex("\\s+")

    fun memInfo(text: String): MemInfo? {
        val map = HashMap<String, Long>()
        for (line in text.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val value = line.substring(idx + 1).trim().split(spaces).firstOrNull()?.toLongOrNull() ?: continue
            map[line.substring(0, idx).trim()] = value
        }
        val total = map["MemTotal"] ?: return null
        val free = map["MemFree"] ?: 0L
        val available = map["MemAvailable"] ?: (free + (map["Cached"] ?: 0L))
        return MemInfo(total, free, available, map["SwapTotal"], map["SwapFree"])
    }

    fun swaps(text: String): List<SwapDevice> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("Filename") }
        .mapNotNull { line ->
            val p = line.split(spaces)
            if (p.size < 5) return@mapNotNull null
            val size = p[2].toLongOrNull() ?: return@mapNotNull null
            val used = p[3].toLongOrNull() ?: return@mapNotNull null
            SwapDevice(p[0], p[1], size, used, p[4].toIntOrNull() ?: 0)
        }.toList()

    fun zramMmStat(text: String): ZramStat? {
        val p = text.trim().split(spaces).mapNotNull { it.toLongOrNull() }
        return if (p.size >= 3) ZramStat(p[0], p[1], p[2]) else null
    }

    fun pressure(text: String): Pressure? {
        fun avg10(prefix: String): Double? = text.lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.let { Regex("avg10=([0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        val some = avg10("some") ?: return null
        return Pressure(some, avg10("full") ?: 0.0)
    }

    /** Section « Total PSS by process » de `dumpsys meminfo`. */
    fun dumpsysMeminfo(text: String): List<AppMem> {
        val row = Regex("^\\s*([\\d,.\\u00a0\\u202f ]+)K: (\\S+) \\(pid")
        val out = ArrayList<AppMem>()
        var inSection = false
        for (line in text.lineSequence()) {
            if (!inSection) {
                if (line.contains("Total PSS by process")) inSection = true
                continue
            }
            val m = row.find(line)
            if (m == null) {
                if (out.isNotEmpty()) break else continue
            }
            val kb = m.groupValues[1].filter { it.isDigit() }.toLongOrNull() ?: continue
            out.add(AppMem(m.groupValues[2], kb))
        }
        return out.sortedByDescending { it.pssKb }
    }

    /** Ex. « 0,2,4,6,8 » → [0, 2, 4, 6, 8]. Renvoie null si la clé est absente. */
    fun intList(text: String): List<Int>? {
        val t = text.trim()
        if (t.isEmpty() || t == "null") return null
        val values = t.split(',').mapNotNull { it.trim().toIntOrNull() }
        return values.ifEmpty { null }
    }

    fun intOrNull(text: String): Int? = text.trim().toIntOrNull()
}
