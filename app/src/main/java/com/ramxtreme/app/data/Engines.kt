package com.ramxtreme.app.data

import android.content.Context
import android.os.SystemClock
import com.ramxtreme.app.core.AppMem
import com.ramxtreme.app.core.Fmt
import com.ramxtreme.app.core.Parsers
import com.ramxtreme.app.core.RamPlusUnits
import com.ramxtreme.app.core.SizeUnit
import com.ramxtreme.app.shizuku.ShizukuManager
import kotlin.math.abs

data class Outcome(val ok: Boolean, val message: String)

data class VendorState(val rawValue: Int?, val listGb: List<Int>?, val keyPresent: Boolean, val unit: SizeUnit) {
    val currentMb: Int? get() = rawValue?.let { RamPlusUnits.toMb(it, unit) }
}

private const val NO_SHIZUKU = "Shizuku n'est pas prêt : opération non effectuée."

/**
 * Extension de RAM via le réglage Samsung `ram_expand_size`.
 * Chaque écriture est sauvegardée, relue, et annulée si la relecture ne correspond pas.
 */
class ExtensionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("extension", Context.MODE_PRIVATE)

    suspend fun readState(): VendorState? {
        val value = ShizukuManager.exec("settings get global ram_expand_size") ?: return null
        if (!value.ok) return null
        val list = ShizukuManager.exec("settings get global ram_expand_size_list")
        val raw = Parsers.intOrNull(value.out)
        return VendorState(
            rawValue = raw,
            listGb = list?.takeIf { it.ok }?.let { Parsers.intList(it.out) },
            keyPresent = value.out.trim() != "null" && value.out.isNotBlank(),
            unit = RamPlusUnits.detect(raw),
        )
    }

    suspend fun apply(targetMb: Int, swapTotalKb: Long?): Outcome {
        val before = ShizukuManager.exec("settings get global ram_expand_size") ?: return Outcome(false, NO_SHIZUKU)
        if (!before.ok) return Outcome(false, "Lecture refusée : ${before.out.trim()}")
        val previous = before.out.trim()
        val unit = RamPlusUnits.detect(previous.toIntOrNull())
        val raw = RamPlusUnits.toRaw(targetMb, unit)

        if (!prefs.contains(KEY_ORIGINAL)) prefs.edit().putString(KEY_ORIGINAL, previous).apply()

        val write = ShizukuManager.exec("settings put global ram_expand_size $raw") ?: return Outcome(false, NO_SHIZUKU)
        if (!write.ok) return Outcome(false, "Écriture refusée par le système : ${write.out.trim()}")

        val check = ShizukuManager.exec("settings get global ram_expand_size")
        if (check == null || check.out.trim() != raw.toString()) {
            writeRaw(previous)
            return Outcome(false, "La valeur relue ne correspond pas : configuration précédente restaurée.")
        }
        prefs.edit()
            .putInt(KEY_PENDING_MB, targetMb)
            .putLong(KEY_PENDING_SWAP, swapTotalKb ?: -1L)
            .putLong(KEY_PENDING_BOOT, bootTime())
            .apply()
        return Outcome(true, "Réglage écrit et relu. Redémarrez : le résultat réel sera mesuré au prochain lancement.")
    }

    /**
     * Ajoute 6, 8, 12 et 16 Go à la liste que les réglages Samsung proposent.
     * Le choix se fait ensuite dans l'écran RAM Plus de Samsung, qui applique lui-même la valeur.
     */
    suspend fun unlockList(swapTotalKb: Long?): Outcome {
        val before = ShizukuManager.exec("settings get global ram_expand_size_list") ?: return Outcome(false, NO_SHIZUKU)
        if (!before.ok) return Outcome(false, "Lecture refusée : ${before.out.trim()}")
        val previous = before.out.trim()
        val current = Parsers.intList(previous) ?: listOf(0, 2, 4)
        val target = (current + listOf(6, 8, 12, 16)).filter { it in 0..16 }.distinct().sorted().joinToString(",")

        if (!prefs.contains(KEY_ORIGINAL_LIST)) prefs.edit().putString(KEY_ORIGINAL_LIST, previous).apply()

        val write = ShizukuManager.exec("settings put global ram_expand_size_list $target")
            ?: return Outcome(false, NO_SHIZUKU)
        if (!write.ok) return Outcome(false, "Écriture refusée par le système : ${write.out.trim()}")
        val check = ShizukuManager.exec("settings get global ram_expand_size_list")?.out?.trim()
        if (check != target) {
            writeList(previous)
            return Outcome(false, "La liste relue ne correspond pas : liste précédente restaurée.")
        }
        prefs.edit()
            .putInt(KEY_PENDING_MB, 0)
            .putLong(KEY_PENDING_SWAP, swapTotalKb ?: -1L)
            .putLong(KEY_PENDING_BOOT, bootTime())
            .apply()
        return Outcome(
            true,
            "Liste étendue : $target Go. Ouvrez maintenant Paramètres → Maintenance de l'appareil → Mémoire → RAM Plus, " +
                "choisissez la taille voulue, puis redémarrez. RAM Xtreme mesurera le résultat.",
        )
    }

    suspend fun restore(): Outcome {
        val original = prefs.getString(KEY_ORIGINAL, null)
        val list = prefs.getString(KEY_ORIGINAL_LIST, null)
        if (original == null && list == null) return Outcome(false, "Aucune sauvegarde : RAM Xtreme n'a rien modifié.")
        var ok = true
        if (original != null) ok = writeRaw(original) && ok
        if (list != null) ok = writeList(list) && ok
        if (!ok) return Outcome(false, "Restauration refusée (Shizuku indisponible ?).")
        prefs.edit().clear().apply()
        return Outcome(true, "Valeurs d'origine restaurées. Redémarrez pour les appliquer.")
    }

    private suspend fun writeList(value: String): Boolean {
        val cmd = if (Parsers.intList(value) == null) {
            "settings delete global ram_expand_size_list"
        } else {
            "settings put global ram_expand_size_list ${Parsers.intList(value)!!.joinToString(",")}"
        }
        return ShizukuManager.exec(cmd)?.ok == true
    }

    private suspend fun writeRaw(value: String): Boolean {
        val cmd = if (value == "null" || value.toIntOrNull() == null) {
            "settings delete global ram_expand_size"
        } else {
            "settings put global ram_expand_size $value"
        }
        return ShizukuManager.exec(cmd)?.ok == true
    }

    val hasBackup: Boolean get() = prefs.contains(KEY_ORIGINAL) || prefs.contains(KEY_ORIGINAL_LIST)

    /** Après un redémarrage, compare le swap réel avant/après. Renvoie null s'il n'y a rien à vérifier. */
    fun verifyAfterReboot(swapTotalKb: Long?): Outcome? {
        if (!prefs.contains(KEY_PENDING_MB)) return null
        if (abs(bootTime() - prefs.getLong(KEY_PENDING_BOOT, 0L)) < 60_000L) return null
        val targetMb = prefs.getInt(KEY_PENDING_MB, 0)
        val target = if (targetMb > 0) "$targetMb Mo" else "le réglage RAM Plus choisi"
        val beforeKb = prefs.getLong(KEY_PENDING_SWAP, -1L)
        prefs.edit().remove(KEY_PENDING_MB).remove(KEY_PENDING_SWAP).remove(KEY_PENDING_BOOT).apply()
        if (swapTotalKb == null || beforeKb < 0) {
            return Outcome(false, "Swap non mesurable : impossible de confirmer l'effet pour $target.")
        }
        return if (swapTotalKb > beforeKb + 64 * 1024) {
            Outcome(true, "Extension prise en compte : swap passé de ${Fmt.go(beforeKb)} à ${Fmt.go(swapTotalKb)}.")
        } else if (swapTotalKb < beforeKb - 64 * 1024) {
            Outcome(true, "Swap réduit : ${Fmt.go(beforeKb)} → ${Fmt.go(swapTotalKb)}.")
        } else {
            Outcome(
                false,
                "Le système n'a pas appliqué $target : swap inchangé (${Fmt.go(swapTotalKb)}). " +
                    "Vous pouvez restaurer la valeur d'origine.",
            )
        }
    }

    val pendingMb: Int? get() = if (prefs.contains(KEY_PENDING_MB)) prefs.getInt(KEY_PENDING_MB, 0) else null

    private fun bootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private companion object {
        const val KEY_ORIGINAL = "original"
        const val KEY_ORIGINAL_LIST = "original_list"
        const val KEY_PENDING_MB = "pending_mb"
        const val KEY_PENDING_SWAP = "pending_swap"
        const val KEY_PENDING_BOOT = "pending_boot"
    }
}

enum class Profile(val label: String, val description: String, val maxCached: Int?, val killCached: Boolean) {
    EQUILIBRE("Équilibré", "Réglages d'origine du système, adaptés à l'usage quotidien.", null, false),
    MULTITACHE(
        "Multitâche",
        "Autorise davantage d'apps en cache (limite portée à 96). Android reste libre de les fermer si la RAM manque.",
        96, false,
    ),
    GAMING(
        "Gaming",
        "Ferme une fois les processus en cache sans danger avant de jouer. Les services actifs ne sont pas touchés.",
        null, true,
    ),
    ECONOMIE("Économie", "Réduit la limite d'apps en cache à 16 pour limiter l'activité en arrière-plan.", 16, false),
    PERSONNALISE("Personnalisé", "Choisissez vous-même la limite d'apps en cache.", null, false),
}

/** Profils : agit sur `device_config activity_manager max_cached_processes`, avec sauvegarde et relecture. */
class ProfileEngine(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val key = "activity_manager max_cached_processes"

    val active: Profile
        get() = runCatching { Profile.valueOf(prefs.getString("active", "") ?: "") }.getOrDefault(Profile.EQUILIBRE)

    suspend fun currentLimit(): String? {
        val r = ShizukuManager.exec("device_config get $key") ?: return null
        return if (r.ok) r.out.trim().let { if (it == "null" || it.isEmpty()) "Défaut système" else it } else null
    }

    suspend fun apply(profile: Profile, custom: Int): Outcome {
        val before = ShizukuManager.exec("device_config get $key") ?: return Outcome(false, NO_SHIZUKU)
        if (!before.ok) return Outcome(false, "Lecture refusée : ${before.out.trim()}")
        if (!prefs.contains("original")) prefs.edit().putString("original", before.out.trim()).apply()

        val target = if (profile == Profile.PERSONNALISE) custom.coerceIn(4, 256) else profile.maxCached
        val command = if (target == null) "device_config delete $key" else "device_config put $key $target"
        val write = ShizukuManager.exec(command) ?: return Outcome(false, NO_SHIZUKU)
        if (!write.ok) return Outcome(false, "Écriture refusée par le système : ${write.out.trim()}")

        val check = ShizukuManager.exec("device_config get $key")?.out?.trim()
        val expected = target?.toString() ?: "null"
        if (check != expected) {
            restoreOriginal()
            return Outcome(false, "La valeur relue ($check) ne correspond pas : réglage précédent restauré.")
        }
        var extra = ""
        if (profile.killCached) {
            val kill = ShizukuManager.exec("am kill-all")
            extra = if (kill?.ok == true) " Processus en cache fermés." else " Fermeture des processus en cache refusée."
        }
        prefs.edit().putString("active", profile.name).apply()
        return Outcome(true, "Profil ${profile.label} appliqué et vérifié.$extra")
    }

    suspend fun restoreOriginal(): Outcome {
        val original = prefs.getString("original", null)
            ?: return Outcome(false, "Aucune sauvegarde : rien à restaurer.")
        val cmd = if (original.toIntOrNull() == null) "device_config delete $key" else "device_config put $key $original"
        if (ShizukuManager.exec(cmd)?.ok != true) return Outcome(false, "Restauration refusée (Shizuku indisponible ?).")
        prefs.edit().clear().apply()
        return Outcome(true, "Limite d'apps en cache d'origine restaurée.")
    }
}

object AppsReader {
    suspend fun load(): List<AppMem>? {
        val r = ShizukuManager.exec("dumpsys meminfo") ?: return null
        if (!r.ok) return null
        return Parsers.dumpsysMeminfo(r.out).takeIf { it.isNotEmpty() }
    }
}

/**
 * Libération de RAM réelle : interdit l'exécution en arrière-plan d'apps installées par l'utilisateur.
 * Jamais d'app système. Réversible app par app.
 */
class AppControl(context: Context) {
    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)
    private val pattern = Regex(com.ramxtreme.app.core.CommandPolicy.PACKAGE)

    val restricted: Set<String> get() = (prefs.getStringSet("restricted", emptySet()) ?: emptySet()).toSet()

    /** Apps installées par l'utilisateur : les seules sur lesquelles RAM Xtreme accepte d'agir. */
    suspend fun thirdParty(): Set<String>? {
        val r = ShizukuManager.exec("pm list packages -3") ?: return null
        if (!r.ok) return null
        return r.out.lineSequence().map { it.trim().removePrefix("package:") }.filter { pattern.matches(it) }.toSet()
    }

    private suspend fun guard(pkg: String, self: String): Outcome? {
        if (!pattern.matches(pkg)) return Outcome(false, "Nom d'application invalide.")
        if (pkg == self || pkg == "moe.shizuku.privileged.api") return Outcome(false, "Cette app est nécessaire à RAM Xtreme.")
        val allowed = thirdParty() ?: return Outcome(false, NO_SHIZUKU)
        return if (pkg in allowed) null else Outcome(false, "$pkg est une app système : RAM Xtreme n'y touche pas.")
    }

    suspend fun restrict(pkg: String, self: String): Outcome {
        guard(pkg, self)?.let { return it }
        val set = ShizukuManager.exec("cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore") ?: return Outcome(false, NO_SHIZUKU)
        if (!set.ok) return Outcome(false, "Refusé par le système : ${set.out.trim()}")
        val check = ShizukuManager.exec("cmd appops get $pkg RUN_ANY_IN_BACKGROUND")?.out ?: ""
        if (!check.contains("ignore")) {
            ShizukuManager.exec("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
            return Outcome(false, "La restriction n'a pas été prise en compte : rien n'a été modifié.")
        }
        ShizukuManager.exec("am force-stop $pkg")
        prefs.edit().putStringSet("restricted", restricted + pkg).apply()
        return Outcome(true, "$pkg : arrière-plan restreint et app arrêtée.")
    }

    suspend fun allow(pkg: String): Outcome {
        if (!pattern.matches(pkg)) return Outcome(false, "Nom d'application invalide.")
        val set = ShizukuManager.exec("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow") ?: return Outcome(false, NO_SHIZUKU)
        if (!set.ok) return Outcome(false, "Refusé par le système : ${set.out.trim()}")
        prefs.edit().putStringSet("restricted", restricted - pkg).apply()
        return Outcome(true, "$pkg : arrière-plan de nouveau autorisé.")
    }

    suspend fun allowAll(): Outcome {
        val all = restricted
        if (all.isEmpty()) return Outcome(false, "Aucune app restreinte par RAM Xtreme.")
        val failed = all.filter { !allow(it).ok }
        return if (failed.isEmpty()) Outcome(true, "${all.size} app(s) de nouveau autorisée(s) en arrière-plan.")
        else Outcome(false, "Non rétablies : ${failed.joinToString()}.")
    }
}
