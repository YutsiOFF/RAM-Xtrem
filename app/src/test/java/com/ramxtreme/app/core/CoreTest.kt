package com.ramxtreme.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val meminfo = """
        MemTotal:        7654321 kB
        MemFree:          123456 kB
        MemAvailable:    2654321 kB
        Cached:          1500000 kB
        SwapTotal:       4194300 kB
        SwapFree:        3194300 kB
    """.trimIndent()

    @Test
    fun meminfoIsParsedExactly() {
        val m = Parsers.memInfo(meminfo)!!
        assertEquals(7654321L, m.totalKb)
        assertEquals(5000000L, m.usedKb)
        assertEquals(4194300L, m.swapTotalKb)
        assertEquals(1000000L, m.swapUsedKb)
    }

    @Test
    fun meminfoWithoutSwapReportsInaccessible() {
        val m = Parsers.memInfo("MemTotal: 1000 kB\nMemFree: 100 kB\nCached: 200 kB")!!
        assertEquals(300L, m.availableKb)
        assertNull(m.swapTotalKb)
        assertNull(m.swapUsedKb)
        assertNull(Parsers.memInfo("n'importe quoi"))
    }

    @Test
    fun swapsAndZram() {
        val swaps = Parsers.swaps(
            "Filename\t\t\t\tType\t\tSize\t\tUsed\t\tPriority\n" +
                "/dev/block/zram0                        partition\t4194300\t\t1048576\t\t-2\n",
        )
        assertEquals(1, swaps.size)
        assertTrue(swaps[0].isZram)
        assertEquals(4194300L, swaps[0].sizeKb)
        assertEquals(-2, swaps[0].priority)
        val z = Parsers.zramMmStat("1073741824 268435456 300000000 0 310000000 12 0 0 0")!!
        assertEquals(1073741824L, z.origBytes)
        assertEquals(300000000L, z.memUsedBytes)
        assertNull(Parsers.zramMmStat(""))
    }

    @Test
    fun pressureAndLists() {
        val p = Parsers.pressure(
            "some avg10=1.50 avg60=0.40 avg300=0.10 total=123\nfull avg10=0.25 avg60=0.00 avg300=0.00 total=45",
        )!!
        assertEquals(1.5, p.someAvg10, 0.0001)
        assertEquals(0.25, p.fullAvg10, 0.0001)
        assertEquals(listOf(0, 2, 4, 6, 8), Parsers.intList("0,2,4,6,8\n"))
        assertNull(Parsers.intList("null\n"))
    }

    @Test
    fun dumpsysMeminfoSection() {
        val text = """
            Total RSS by process:
                400,000K: system (pid 1000)

            Total PSS by process:
                312,456K: com.android.systemui (pid 1234)
                512,000K: com.example.game (pid 4321 / activities)
                 90,100K: com.ramxtreme.app (pid 999)

            Total PSS by OOM adjustment:
                500,000K: Native
        """.trimIndent()
        val apps = Parsers.dumpsysMeminfo(text)
        assertEquals(3, apps.size)
        assertEquals("com.example.game", apps[0].name)
        assertEquals(512000L, apps[0].pssKb)
    }

    @Test
    fun commandPolicyIsAStrictWhitelist() {
        assertTrue(CommandPolicy.isAllowed("cat /proc/meminfo"))
        assertTrue(CommandPolicy.isAllowed("settings put global ram_expand_size 8192"))
        assertTrue(CommandPolicy.isAllowed("device_config put activity_manager max_cached_processes 96"))
        assertFalse(CommandPolicy.isAllowed("cat /proc/meminfo; rm -rf /sdcard"))
        assertFalse(CommandPolicy.isAllowed("settings put global adb_enabled 0"))
        assertFalse(CommandPolicy.isAllowed("settings put global ram_expand_size 8192 && reboot"))
        assertFalse(CommandPolicy.isAllowed("pm uninstall com.example"))
        assertFalse(CommandPolicy.isAllowed("cat /data/system/packages.xml"))
    }

    @Test
    fun units() {
        assertEquals(SizeUnit.MB, RamPlusUnits.detect(4096))
        assertEquals(SizeUnit.GB, RamPlusUnits.detect(8))
        assertEquals(SizeUnit.MB, RamPlusUnits.detect(null))
        assertEquals(8, RamPlusUnits.toRaw(8192, SizeUnit.GB))
        assertEquals(8192, RamPlusUnits.toMb(8, SizeUnit.GB))
    }

    private fun plan(vendor: Vendor, shizuku: Boolean, storageMb: Long = 100_000) = ExtensionPlanner.plan(
        vendor = vendor,
        shizukuReady = shizuku,
        vendorListGb = listOf(0, 2, 4, 6, 8),
        keyPresent = true,
        physicalRamMb = 8192,
        freeStorageMb = storageMb,
        sizesMb = ExtensionPlanner.presetsMb + 64 * 1024,
    ).associateBy { it.sizeMb }

    @Test
    fun plannerNeverPromisesWhatTheDeviceForbids() {
        // Appareil non Samsung, ou sans Shizuku : tout est théorique.
        assertTrue(plan(Vendor.PIXEL, true).values.all { it.status == OptionStatus.THEORIQUE })
        assertTrue(plan(Vendor.SAMSUNG, false).values.all { it.status == OptionStatus.THEORIQUE })

        val samsung = plan(Vendor.SAMSUNG, true)
        assertEquals(OptionStatus.CONFIGURABLE, samsung.getValue(8 * 1024).status)
        assertEquals(OptionStatus.A_VERIFIER, samsung.getValue(12 * 1024).status)
        assertEquals(OptionStatus.THEORIQUE, samsung.getValue(64 * 1024).status)

        // Stockage presque plein : rien au-delà de la moitié de l'espace libre.
        assertEquals(OptionStatus.THEORIQUE, plan(Vendor.SAMSUNG, true, storageMb = 6000).getValue(4 * 1024).status)
    }

    @Test
    fun vendorDetection() {
        assertEquals(Vendor.SAMSUNG, Vendor.detect("samsung", "samsung"))
        assertEquals(Vendor.XIAOMI, Vendor.detect("Xiaomi", "POCO"))
        assertEquals(Vendor.PIXEL, Vendor.detect("Google", "google"))
        assertEquals(Vendor.REALME, Vendor.detect("realme", "realme"))
        assertEquals(Vendor.OTHER, Vendor.detect("Nothing", "Nothing"))
    }
}
