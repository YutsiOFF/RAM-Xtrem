package com.ramxtreme.app.core

enum class Vendor(val label: String, val mechanism: String, val adbTunable: Boolean) {
    SAMSUNG("Samsung", "RAM Plus", true),
    XIAOMI("Xiaomi / Redmi / POCO", "Extension de mémoire", false),
    MOTOROLA("Motorola", "RAM Boost", false),
    ONEPLUS("OnePlus", "Extension de RAM", false),
    OPPO("OPPO", "Extension de RAM", false),
    REALME("Realme", "Extension dynamique de RAM", false),
    PIXEL("Google Pixel", "Aucune extension réglable (ZRAM fixe)", false),
    OTHER("Autre fabricant", "Mécanisme inconnu", false);

    companion object {
        fun detect(manufacturer: String, brand: String): Vendor {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            return when {
                m.contains("samsung") -> SAMSUNG
                m.contains("xiaomi") || b in setOf("redmi", "poco", "xiaomi") -> XIAOMI
                m.contains("motorola") || m.contains("lenovo") && b.contains("moto") -> MOTOROLA
                m.contains("oneplus") -> ONEPLUS
                m.contains("realme") || b.contains("realme") -> REALME
                m.contains("oppo") -> OPPO
                m.contains("google") -> PIXEL
                else -> OTHER
            }
        }
    }
}

/** Unité de la clé Samsung `ram_expand_size` : historiquement en Mo, parfois en Go selon le firmware. */
enum class SizeUnit { MB, GB }

object RamPlusUnits {
    fun detect(currentRaw: Int?): SizeUnit = if (currentRaw != null && currentRaw in 1..64) SizeUnit.GB else SizeUnit.MB
    fun toRaw(targetMb: Int, unit: SizeUnit): Int = if (unit == SizeUnit.GB) targetMb / 1024 else targetMb
    fun toMb(raw: Int, unit: SizeUnit): Int = if (unit == SizeUnit.GB) raw * 1024 else raw
}

enum class OptionStatus { CONFIGURABLE, A_VERIFIER, THEORIQUE }

data class ExtensionOption(val sizeMb: Int, val status: OptionStatus, val reason: String)

object ExtensionPlanner {
    val presetsMb: List<Int> = listOf(2, 4, 6, 8, 12, 16).map { it * 1024 }
    const val MAX_MB: Int = 32 * 1024

    fun plan(
        vendor: Vendor,
        shizukuReady: Boolean,
        vendorListGb: List<Int>?,
        keyPresent: Boolean,
        physicalRamMb: Long,
        freeStorageMb: Long,
        sizesMb: List<Int>,
    ): List<ExtensionOption> = sizesMb.distinct().sorted().map { mb ->
        when {
            mb <= 0 || mb > MAX_MB ->
                ExtensionOption(mb, OptionStatus.THEORIQUE, "Valeur hors limites de sécurité (max. 32 Go).")
            !vendor.adbTunable ->
                ExtensionOption(
                    mb, OptionStatus.THEORIQUE,
                    "${vendor.label} : aucun réglage n'est accessible sans root. Utilisez les paramètres système.",
                )
            !shizukuReady ->
                ExtensionOption(mb, OptionStatus.THEORIQUE, "Nécessite Shizuku (droits ADB Shell) pour écrire le réglage.")
            mb > freeStorageMb / 2 ->
                ExtensionOption(mb, OptionStatus.THEORIQUE, "Stockage libre insuffisant pour rester en sécurité.")
            vendorListGb != null && mb % 1024 == 0 && (mb / 1024) in vendorListGb ->
                ExtensionOption(mb, OptionStatus.CONFIGURABLE, "Valeur proposée par le constructeur.")
            !keyPresent ->
                ExtensionOption(
                    mb, OptionStatus.A_VERIFIER,
                    "Clé RAM Plus absente sur ce firmware : écriture possible, prise en compte incertaine.",
                )
            mb > physicalRamMb * 2 ->
                ExtensionOption(
                    mb, OptionStatus.A_VERIFIER,
                    "Plus de 2× la RAM physique : gain réel improbable, vérifié après redémarrage.",
                )
            else ->
                ExtensionOption(
                    mb, OptionStatus.A_VERIFIER,
                    "Au-delà de la liste constructeur : écriture possible, prise en compte vérifiée après redémarrage.",
                )
        }
    }
}

/** Tableau de compatibilité issu de la recherche technique. */
data class CompatRow(
    val tech: String,
    val noRoot: String,
    val shizuku: String,
    val root: String,
    val limits: String,
)

object Compatibility {
    val rows = listOf(
        CompatRow(
            "Statistiques RAM / swap", "Oui (/proc/meminfo)", "Oui, plus détaillées", "Oui",
            "Le détail ZRAM et /proc/swaps sont masqués aux apps par SELinux.",
        ),
        CompatRow(
            "ZRAM (taille, algorithme)", "Non", "Lecture seule", "Oui",
            "L'écriture dans /sys/block/zram0 est réservée à init / root.",
        ),
        CompatRow(
            "Fichier de swap (swapon)", "Non", "Non", "Oui",
            "swapon exige CAP_SYS_ADMIN, que ni une app ni ADB Shell ne possèdent.",
        ),
        CompatRow(
            "Samsung RAM Plus", "Via Paramètres système", "Oui (settings global)", "Oui",
            "Redémarrage requis. Valeurs hors liste : résultat non garanti.",
        ),
        CompatRow(
            "Extension RAM autres marques", "Via Paramètres système", "Lecture seule", "Oui",
            "Aucune clé publique modifiable connue via ADB.",
        ),
        CompatRow(
            "Paramètres noyau (swappiness…)", "Non", "Non", "Oui",
            "/proc/sys/vm n'est pas modifiable par ADB Shell.",
        ),
        CompatRow(
            "Limite d'apps en cache", "Non", "Oui (device_config)", "Oui",
            "Le système peut réécrire la valeur ; Android reste seul décideur.",
        ),
        CompatRow(
            "Mémoire par application", "Non (soi-même uniquement)", "Oui (dumpsys meminfo)", "Oui",
            "Android masque les autres processus aux apps ordinaires.",
        ),
    )
}
