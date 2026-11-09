package com.ramxtreme.app.core

/**
 * Liste blanche exhaustive des commandes que l'app peut lancer avec les droits ADB Shell.
 * Elle est vérifiée dans le service privilégié : aucune autre commande ne peut partir.
 */
object CommandPolicy {
    const val PACKAGE = "[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"

    private val allowed = listOf(
        Regex("id -u"),
        Regex("cat /proc/(meminfo|swaps|vmstat|pressure/memory)"),
        Regex("cat /sys/block/zram0/(disksize|mm_stat|comp_algorithm|backing_dev)"),
        Regex("test -w /sys/block/zram0/disksize"),
        Regex("getprop [a-zA-Z0-9._-]{1,92}"),
        Regex("settings get global ram_expand_size(_list)?"),
        Regex("settings put global ram_expand_size \\d{1,5}"),
        Regex("settings delete global ram_expand_size(_list)?"),
        Regex("settings put global ram_expand_size_list [0-9]{1,2}(,[0-9]{1,2}){0,11}"),
        Regex("device_config get activity_manager max_cached_processes"),
        Regex("device_config put activity_manager max_cached_processes \\d{1,3}"),
        Regex("device_config delete activity_manager max_cached_processes"),
        Regex("am kill-all"),
        Regex("pm list packages -3"),
        Regex("am force-stop $PACKAGE"),
        Regex("cmd appops get $PACKAGE RUN_ANY_IN_BACKGROUND"),
        Regex("cmd appops set $PACKAGE RUN_ANY_IN_BACKGROUND (ignore|allow)"),
        Regex("dumpsys meminfo"),
    )

    fun isAllowed(command: String): Boolean = allowed.any { it.matches(command) }
}
