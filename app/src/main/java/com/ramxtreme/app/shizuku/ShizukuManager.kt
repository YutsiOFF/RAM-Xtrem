package com.ramxtreme.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.ramxtreme.app.BuildConfig
import com.ramxtreme.app.IShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShellResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

object ShizukuManager {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4711

    private val _state = MutableStateFlow(State.NOT_INSTALLED)
    val state: StateFlow<State> = _state

    private var appContext: Context? = null
    private var initialized = false
    private var binding = false

    @Volatile
    private var service: IShellService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) IShellService.Stub.asInterface(binder) else null
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
        }
    }

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!initialized) {
            initialized = true
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener {
                service = null
                refresh()
            }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }
        refresh()
    }

    fun refresh() {
        val ctx = appContext ?: return
        val alive = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
        val granted = alive && try {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
        val installed = try {
            ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (t: Throwable) {
            false
        }
        _state.value = when {
            granted -> State.READY
            alive -> State.NEEDS_PERMISSION
            installed -> State.NOT_RUNNING
            else -> State.NOT_INSTALLED
        }
        if (granted) bind() else service = null
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            refresh()
        }
    }

    /** Identité réelle du service Shizuku : 2000 = ADB Shell, 0 = root. */
    fun privilegeUid(): Int? = try {
        if (Shizuku.pingBinder()) Shizuku.getUid() else null
    } catch (t: Throwable) {
        null
    }

    private fun bind() {
        if (service != null || binding) return
        binding = true
        try {
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            binding = false
        }
    }

    /** Renvoie null si Shizuku n'est pas prêt : l'appelant doit alors afficher « Non accessible ». */
    suspend fun exec(command: String): ShellResult? = withContext(Dispatchers.IO) {
        if (_state.value != State.READY) return@withContext null
        var s = service
        var waited = 0
        while (s == null && waited < 4000) {
            bind()
            delay(100)
            waited += 100
            s = service
        }
        if (s == null) return@withContext null
        try {
            val raw = s.exec(command)
            val nl = raw.indexOf('\n')
            if (nl < 0) ShellResult(raw.trim().toIntOrNull() ?: 1, "")
            else ShellResult(raw.substring(0, nl).toIntOrNull() ?: 1, raw.substring(nl + 1))
        } catch (t: Throwable) {
            service = null
            null
        }
    }
}
