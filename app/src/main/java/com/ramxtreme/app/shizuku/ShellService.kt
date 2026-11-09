package com.ramxtreme.app.shizuku

import android.content.Context
import com.ramxtreme.app.IShellService
import com.ramxtreme.app.core.CommandPolicy
import kotlin.system.exitProcess

/**
 * Service lancé par Shizuku dans un processus séparé, avec l'identité ADB Shell (uid 2000).
 * Il n'exécute que les commandes de [CommandPolicy].
 */
class ShellService() : IShellService.Stub() {

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        if (!CommandPolicy.isAllowed(command)) return "126\nCommande refusée par la liste blanche."
        return try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_CHARS)
                var total = 0
                while (total < MAX_CHARS) {
                    val n = reader.read(buffer, total, MAX_CHARS - total)
                    if (n < 0) break
                    total += n
                }
                // Sortie tronquée : on arrête la commande plutôt que de la laisser bloquée.
                if (total >= MAX_CHARS) process.destroy()
                String(buffer, 0, total)
            }
            val code = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                1
            }
            "$code\n$output"
        } catch (t: Throwable) {
            "127\n${t.message ?: t.javaClass.simpleName}"
        }
    }

    private companion object {
        // Reste sous la limite d'une transaction Binder (1 Mo).
        const val MAX_CHARS = 200_000
    }
}
