package com.ramxtreme.app.data

import com.ramxtreme.app.core.ExtensionPlanner
import com.ramxtreme.app.core.Fmt
import com.ramxtreme.app.core.Parsers
import com.ramxtreme.app.shizuku.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Exécution via `su`, uniquement sur un appareil déjà rooté. Renvoie null si `su` est absent. */
object RootShell {
    suspend fun exec(script: String, timeoutSec: Long = 90): ShellResult? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            process.outputStream.close()
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroy()
                return@withContext ShellResult(124, "Délai dépassé.")
            }
            ShellResult(process.exitValue(), process.inputStream.bufferedReader().readText().take(20_000))
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun available(): Boolean = exec("id -u", 60)?.let { it.ok && it.out.trim() == "0" } == true
}

/**
 * Mode root facultatif : redimensionne réellement la ZRAM du noyau.
 * Non persistant : un redémarrage rétablit toujours la configuration du constructeur.
 */
class RootEngine {
    private fun script(path: String, name: String, bytes: Long, strict: Boolean): String {
        val sep = if (strict) "&&" else ";"
        return "swapoff $path $sep echo 1 > /sys/block/$name/reset && echo $bytes > /sys/block/$name/disksize " +
            "&& mkswap $path && swapon $path"
    }

    private suspend fun swapTotalKb(): Long? =
        RootShell.exec("cat /proc/meminfo")?.takeIf { it.ok }?.let { Parsers.memInfo(it.out) }?.swapTotalKb

    suspend fun resizeZram(targetMb: Int): Outcome {
        if (targetMb !in 512..ExtensionPlanner.MAX_MB) return Outcome(false, "Taille hors limites (0,5 à 32 Go).")
        if (!RootShell.available()) {
            return Outcome(false, "Accès root absent ou refusé : cet appareil n'est pas rooté, ou l'autorisation a été refusée.")
        }
        val swaps = RootShell.exec("cat /proc/swaps")?.takeIf { it.ok }?.let { Parsers.swaps(it.out) }
            ?: return Outcome(false, "Lecture de /proc/swaps impossible.")
        val dev = swaps.firstOrNull { it.isZram } ?: return Outcome(false, "Aucun périphérique ZRAM actif sur cet appareil.")
        val name = dev.path.substringAfterLast('/')
        if (!Regex("/dev/(block/)?zram\\d{1,2}").matches(dev.path)) {
            return Outcome(false, "Périphérique de swap inattendu (${dev.path}) : opération annulée.")
        }
        val mem = RootShell.exec("cat /proc/meminfo")?.takeIf { it.ok }?.let { Parsers.memInfo(it.out) }
            ?: return Outcome(false, "Lecture de /proc/meminfo impossible.")
        if (dev.usedKb > mem.availableKb * 7 / 10) {
            return Outcome(
                false,
                "Le swap contient ${Fmt.mo(dev.usedKb)} pour ${Fmt.mo(mem.availableKb)} de RAM disponible : le vider " +
                    "maintenant pourrait figer le téléphone. Redémarrez, puis réessayez juste après le démarrage.",
            )
        }
        val before = mem.swapTotalKb ?: dev.sizeKb
        val expected = before - dev.sizeKb + targetMb * 1024L
        val result = RootShell.exec(script(dev.path, name, targetMb * 1024L * 1024L, strict = true), 180)
        val after = swapTotalKb()
        if (result?.ok == true && after != null && abs(after - expected) < 64 * 1024) {
            return Outcome(
                true,
                "ZRAM redimensionnée : swap réel passé de ${Fmt.go(before)} à ${Fmt.go(after)}. " +
                    "Valable jusqu'au prochain redémarrage.",
            )
        }
        // Échec : on remet la taille précédente, puis on dit ce que le noyau affiche vraiment.
        RootShell.exec(script(dev.path, name, (dev.sizeKb + 4) * 1024L, strict = false), 180)
        val now = swapTotalKb()
        return Outcome(
            false,
            "Le noyau a refusé : ${result?.out?.trim()?.takeLast(200) ?: "aucune réponse"}. " +
                "Swap actuel : ${now?.let { Fmt.go(it) } ?: "non mesurable"}. Un redémarrage rétablit la configuration d'origine.",
        )
    }
}
