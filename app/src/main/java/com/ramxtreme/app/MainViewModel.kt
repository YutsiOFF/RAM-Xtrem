package com.ramxtreme.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ramxtreme.app.core.AppMem
import com.ramxtreme.app.core.ExtensionOption
import com.ramxtreme.app.core.ExtensionPlanner
import com.ramxtreme.app.data.AppsReader
import com.ramxtreme.app.data.DeviceInfo
import com.ramxtreme.app.data.ExtensionEngine
import com.ramxtreme.app.data.Outcome
import com.ramxtreme.app.data.Profile
import com.ramxtreme.app.data.ProfileEngine
import com.ramxtreme.app.data.RootEngine
import com.ramxtreme.app.data.RootShell
import com.ramxtreme.app.data.Snapshot
import com.ramxtreme.app.data.SystemReader
import com.ramxtreme.app.data.VendorState
import com.ramxtreme.app.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val reader = SystemReader(app)
    private val extension = ExtensionEngine(app)
    private val profiles = ProfileEngine(app)
    private val root = RootEngine()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val device: DeviceInfo = reader.device()
    val shizuku: StateFlow<ShizukuManager.State> = ShizukuManager.state

    val snapshot = MutableStateFlow<Snapshot?>(null)
    val ramHistory = MutableStateFlow<List<Float>>(emptyList())
    val swapHistory = MutableStateFlow<List<Float>>(emptyList())
    val vendorState = MutableStateFlow<VendorState?>(null)
    val message = MutableStateFlow<Outcome?>(null)
    val apps = MutableStateFlow<List<AppMem>?>(null)
    val appsLoading = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val activeProfile = MutableStateFlow(profiles.active)
    val cachedLimit = MutableStateFlow<String?>(null)
    val intervalMs = MutableStateFlow(prefs.getLong("interval", 2000L))
    val hasExtensionBackup = MutableStateFlow(extension.hasBackup)
    val pendingMb = MutableStateFlow(extension.pendingMb)

    /** null = pas encore vérifié. */
    val rootAvailable = MutableStateFlow<Boolean?>(null)

    init {
        ShizukuManager.init(app)
        viewModelScope.launch {
            var verified = false
            var tick = 0
            while (true) {
                val snap = reader.snapshot()
                snapshot.value = snap
                snap.mem?.let { mem ->
                    ramHistory.value = (ramHistory.value + mem.usedFraction).takeLast(HISTORY)
                    val swapTotal = mem.swapTotalKb
                    val swapUsed = mem.swapUsedKb
                    if (swapTotal != null && swapUsed != null && swapTotal > 0) {
                        swapHistory.value = (swapHistory.value + swapUsed.toFloat() / swapTotal).takeLast(HISTORY)
                    }
                    if (!verified) {
                        verified = true
                        extension.verifyAfterReboot(swapTotal)?.let {
                            message.value = it
                            pendingMb.value = extension.pendingMb
                        }
                    }
                }
                if (tick % 5 == 0 && shizuku.value == ShizukuManager.State.READY) refreshPrivileged()
                tick++
                delay(intervalMs.value)
            }
        }
    }

    private suspend fun refreshPrivileged() {
        if (device.vendor.adbTunable) vendorState.value = extension.readState()
        cachedLimit.value = profiles.currentLimit()
    }

    fun options(customMb: Int?): List<ExtensionOption> {
        val snap = snapshot.value
        val state = vendorState.value
        return ExtensionPlanner.plan(
            vendor = device.vendor,
            shizukuReady = shizuku.value == ShizukuManager.State.READY,
            vendorListGb = state?.listGb,
            keyPresent = state?.keyPresent == true,
            physicalRamMb = (snap?.mem?.totalKb ?: 0L) / 1024,
            freeStorageMb = (snap?.storageFreeKb ?: 0L) / 1024,
            sizesMb = ExtensionPlanner.presetsMb + listOfNotNull(customMb),
        )
    }

    private fun launchOp(block: suspend () -> Outcome) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            message.value = try {
                block()
            } catch (t: Throwable) {
                Outcome(false, "Erreur inattendue : ${t.message ?: t.javaClass.simpleName}")
            }
            hasExtensionBackup.value = extension.hasBackup
            pendingMb.value = extension.pendingMb
            activeProfile.value = profiles.active
            refreshPrivileged()
            busy.value = false
        }
    }

    fun applyExtension(mb: Int) = launchOp { extension.apply(mb, snapshot.value?.mem?.swapTotalKb) }
    fun unlockList() = launchOp { extension.unlockList(snapshot.value?.mem?.swapTotalKb) }
    fun checkRoot() = launchOp {
        val ok = RootShell.available()
        rootAvailable.value = ok
        if (ok) Outcome(true, "Accès root accordé.")
        else Outcome(false, "Aucun accès root : cet appareil n'est pas rooté, ou l'autorisation a été refusée.")
    }

    fun rootResize(mb: Int) = launchOp { root.resizeZram(mb) }
    fun restoreExtension() = launchOp { extension.restore() }
    fun applyProfile(profile: Profile, custom: Int) = launchOp { profiles.apply(profile, custom) }
    fun restoreProfile() = launchOp { profiles.restoreOriginal() }

    fun restoreEverything() = launchOp {
        val a = extension.restore()
        val b = profiles.restoreOriginal()
        Outcome(a.ok || b.ok, "${a.message}\n${b.message}")
    }

    fun loadApps() {
        if (appsLoading.value) return
        appsLoading.value = true
        viewModelScope.launch {
            apps.value = AppsReader.load()
            appsLoading.value = false
        }
    }

    fun requestShizuku() = ShizukuManager.requestPermission()
    fun refreshShizuku() = ShizukuManager.refresh()
    fun dismissMessage() {
        message.value = null
    }

    fun setInterval(ms: Long) {
        intervalMs.value = ms
        prefs.edit().putLong("interval", ms).apply()
    }

    private companion object {
        const val HISTORY = 60
    }
}
