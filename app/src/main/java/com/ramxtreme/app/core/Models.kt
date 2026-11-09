package com.ramxtreme.app.core

import java.util.Locale

/** Valeurs en kilo-octets, telles que publiées par le noyau. `null` = non accessible. */
data class MemInfo(
    val totalKb: Long,
    val freeKb: Long,
    val availableKb: Long,
    val swapTotalKb: Long?,
    val swapFreeKb: Long?,
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalKb > 0) usedKb.toFloat() / totalKb else 0f
    val swapUsedKb: Long?
        get() = if (swapTotalKb != null && swapFreeKb != null) (swapTotalKb - swapFreeKb).coerceAtLeast(0) else null
}

data class SwapDevice(val path: String, val type: String, val sizeKb: Long, val usedKb: Long, val priority: Int) {
    val isZram: Boolean get() = path.contains("zram")
}

/** Extrait de /sys/block/zram0/mm_stat (octets). */
data class ZramStat(val origBytes: Long, val comprBytes: Long, val memUsedBytes: Long)

/** Pression mémoire (PSI), pourcentage du temps bloqué sur 10 s. */
data class Pressure(val someAvg10: Double, val fullAvg10: Double)

data class AppMem(val name: String, val pssKb: Long)

object Fmt {
    fun mo(kb: Long): String = "${kb / 1024} Mo"
    fun go(kb: Long): String = String.format(Locale.FRANCE, "%.2f Go", kb / 1048576.0)
    fun both(kb: Long?): String? = kb?.let { "${mo(it)} · ${go(it)}" }
}
