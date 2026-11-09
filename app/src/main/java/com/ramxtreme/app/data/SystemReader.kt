package com.ramxtreme.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.ramxtreme.app.core.MemInfo
import com.ramxtreme.app.core.Parsers
import com.ramxtreme.app.core.Pressure
import com.ramxtreme.app.core.SwapDevice
import com.ramxtreme.app.core.Vendor
import com.ramxtreme.app.core.ZramStat
import com.ramxtreme.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Snapshot(
    val mem: MemInfo?,
    val swaps: List<SwapDevice>?,
    val zramDiskKb: Long?,
    val zram: ZramStat?,
    val zramAlgorithm: String?,
    val pressure: Pressure?,
    val storageFreeKb: Long,
    val storageTotalKb: Long,
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val android: String,
    val sdk: Int,
    val kernel: String?,
    val soc: String,
    val abis: String,
    val vendor: Vendor,
)

class SystemReader(private val context: Context) {

    /** Lecture directe, puis repli sur Shizuku si SELinux refuse l'accès à l'app. */
    private suspend fun read(path: String): String? {
        val direct = try {
            File(path).readText().takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
        if (direct != null) return direct
        val r = ShizukuManager.exec("cat $path") ?: return null
        return if (r.ok && r.out.isNotBlank()) r.out else null
    }

    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) {
        val mem = read("/proc/meminfo")?.let(Parsers::memInfo) ?: fallbackMem()
        val swaps = read("/proc/swaps")?.let(Parsers::swaps)
        val zramDisk = read("/sys/block/zram0/disksize")?.trim()?.toLongOrNull()?.let { it / 1024 }
            ?: swaps?.filter { it.isZram }?.takeIf { it.isNotEmpty() }?.sumOf { it.sizeKb }
        val zram = read("/sys/block/zram0/mm_stat")?.let(Parsers::zramMmStat)
        val algorithm = read("/sys/block/zram0/comp_algorithm")
            ?.let { Regex("\\[([^\\]]+)]").find(it)?.groupValues?.get(1) }
        val pressure = read("/proc/pressure/memory")?.let(Parsers::pressure)
        val stat = StatFs(Environment.getDataDirectory().path)
        Snapshot(mem, swaps, zramDisk, zram, algorithm, pressure, stat.availableBytes / 1024, stat.totalBytes / 1024)
    }

    private fun fallbackMem(): MemInfo? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem <= 0) return null
        return MemInfo(info.totalMem / 1024, info.availMem / 1024, info.availMem / 1024, null, null)
    }

    fun device(): DeviceInfo {
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        } else {
            Build.HARDWARE
        }
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            android = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            kernel = System.getProperty("os.version"),
            soc = soc.ifBlank { Build.HARDWARE },
            abis = Build.SUPPORTED_ABIS.joinToString(", "),
            vendor = Vendor.detect(Build.MANUFACTURER, Build.BRAND),
        )
    }
}
