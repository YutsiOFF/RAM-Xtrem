package com.ramxtreme.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramxtreme.app.BuildConfig
import com.ramxtreme.app.MainViewModel
import com.ramxtreme.app.core.Compatibility
import com.ramxtreme.app.core.Fmt
import com.ramxtreme.app.shizuku.ShizukuManager

@Composable
fun CompatScreen(vm: MainViewModel) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val vendorState by vm.vendorState.collectAsStateWithLifecycle()
    val d = vm.device

    RxCard("Cet appareil") {
        StatRow("Fabricant", d.manufacturer)
        StatRow("Modèle", d.model)
        StatRow("Android", "${d.android} (API ${d.sdk})")
        StatRow("Noyau Linux", d.kernel)
        StatRow("Processeur", d.soc)
        StatRow("Architecture", d.abis)
        StatRow("RAM physique", Fmt.both(snap?.mem?.totalKb))
        StatRow("Stockage total", Fmt.both(snap?.storageTotalKb))
        StatRow("ZRAM", Fmt.both(snap?.zramDiskKb))
        StatRow("Algorithme ZRAM", snap?.zramAlgorithm)
        StatRow("Périphériques de swap", snap?.swaps?.let { list -> if (list.isEmpty()) "Aucun" else list.joinToString { it.path.substringAfterLast('/') } })
    }
    RxCard("Mécanisme constructeur") {
        StatRow("Famille détectée", d.vendor.label)
        StatRow("Mécanisme", d.vendor.mechanism)
        StatRow("Réglable via Shizuku", if (d.vendor.adbTunable) "Oui (à vérifier après redémarrage)" else "Non")
        StatRow("Clé RAM Plus présente", vendorState?.let { if (it.keyPresent) "Oui" else "Non" })
    }
    Text("Tableau de compatibilité", fontWeight = FontWeight.SemiBold)
    Compatibility.rows.forEach { row ->
        RxCard(row.tech) {
            StatRow("Sans root", row.noRoot)
            StatRow("Shizuku / ADB", row.shizuku)
            StatRow("Root", row.root)
            Note(row.limits)
        }
    }
}

@Composable
fun ShizukuScreen(vm: MainViewModel) {
    val state by vm.shizuku.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uid = ShizukuManager.privilegeUid()

    RxCard("État") {
        StatRow(
            "Shizuku",
            when (state) {
                ShizukuManager.State.NOT_INSTALLED -> "Non installé"
                ShizukuManager.State.NOT_RUNNING -> "Installé, service arrêté"
                ShizukuManager.State.NEEDS_PERMISSION -> "Actif, autorisation requise"
                ShizukuManager.State.READY -> "Connecté"
            },
        )
        StatRow(
            "Privilèges",
            when (uid) {
                null -> null
                0 -> "root (uid 0)"
                2000 -> "ADB Shell (uid 2000)"
                else -> "uid $uid"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ShizukuManager.State.NOT_INSTALLED -> Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Télécharger Shizuku") }
                ShizukuManager.State.NEEDS_PERMISSION -> Button(onClick = { vm.requestShizuku() }) { Text("Autoriser RAM Xtreme") }
                else -> {}
            }
            OutlinedButton(onClick = { vm.refreshShizuku() }) { Text("Actualiser") }
        }
    }
    RxCard("Démarrer Shizuku sans ordinateur (Android 11+)") {
        listOf(
            "1. Installez Shizuku et ouvrez-le.",
            "2. Activez les Options pour les développeurs (Paramètres → À propos → touchez 7 fois « Numéro de build »).",
            "3. Dans les Options pour les développeurs, activez « Débogage sans fil » (Wi-Fi requis).",
            "4. Dans Shizuku, touchez « Association », puis dans Débogage sans fil « Associer avec un code ».",
            "5. Saisissez le code dans la notification Shizuku.",
            "6. Revenez dans Shizuku et touchez « Démarrer ».",
            "7. Revenez ici et touchez « Autoriser RAM Xtreme ».",
        ).forEach { Text(it) }
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) { Text("Options pour les développeurs") }
        Note("Le service Shizuku s'arrête à chaque redémarrage du téléphone : il faut le relancer (étape 6).")
    }
    RxCard("Ce que Shizuku permet — et ne permet pas") {
        Note(
            "Shizuku donne les droits ADB Shell, pas le root. RAM Xtreme s'en sert pour lire les statistiques masquées, " +
                "écrire le réglage Samsung RAM Plus et la limite d'apps en cache, et lire la mémoire par application.",
        )
        Note(
            "Impossible même avec Shizuku : modifier la ZRAM, activer un fichier de swap, changer les paramètres du noyau " +
                "ou toucher aux partitions système.",
        )
        Note("RAM Xtreme n'envoie que des commandes d'une liste blanche fixe, visible dans le code source (CommandPolicy).")
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val interval by vm.intervalMs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    RxCard("Actualisation du tableau de bord") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1000L to "1 s", 2000L to "2 s", 5000L to "5 s").forEach { (ms, label) ->
                FilterChip(selected = interval == ms, onClick = { vm.setInterval(ms) }, label = { Text(label) })
            }
        }
    }
    RxCard("Sécurité") {
        Note("Annule toutes les modifications faites par RAM Xtreme (extension de RAM et limite d'apps en cache).")
        Button(onClick = { vm.restoreEverything() }, enabled = !busy) { Text("Tout restaurer") }
    }
    RxCard("À propos") {
        StatRow("Version", BuildConfig.VERSION_NAME)
        StatRow("Type de build", BuildConfig.BUILD_TYPE)
        Note(
            "RAM Xtreme n'affiche que des mesures réelles et n'applique que des réglages autorisés par Android. " +
                "Sans root, ni la ZRAM ni le swap du noyau ne peuvent être agrandis directement.",
        )
    }
}
