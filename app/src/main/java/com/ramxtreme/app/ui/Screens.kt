package com.ramxtreme.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramxtreme.app.MainViewModel
import com.ramxtreme.app.core.ExtensionOption
import com.ramxtreme.app.core.Fmt
import com.ramxtreme.app.core.OptionStatus
import com.ramxtreme.app.data.Profile
import com.ramxtreme.app.shizuku.ShizukuManager
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: MainViewModel) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val ram by vm.ramHistory.collectAsStateWithLifecycle()
    val swap by vm.swapHistory.collectAsStateWithLifecycle()
    val vendorState by vm.vendorState.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val mem = snap?.mem

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RamGauge(
            fraction = mem?.usedFraction,
            center = mem?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
            sub = "RAM utilisée",
        )
    }
    RxCard("RAM physique") {
        StatRow("Totale", Fmt.both(mem?.totalKb))
        StatRow("Utilisée", Fmt.both(mem?.usedKb))
        StatRow("Disponible", Fmt.both(mem?.availableKb))
    }
    RxCard("Mémoire virtuelle") {
        StatRow("ZRAM configurée", Fmt.both(snap?.zramDiskKb))
        StatRow("ZRAM utilisée (données)", Fmt.both(snap?.zram?.let { it.origBytes / 1024 }))
        StatRow("ZRAM après compression", Fmt.both(snap?.zram?.let { it.memUsedBytes / 1024 }))
        StatRow("Swap total", Fmt.both(mem?.swapTotalKb))
        StatRow("Swap utilisé", Fmt.both(mem?.swapUsedKb))
        StatRow(
            "${vm.device.vendor.mechanism} (constructeur)",
            vendorState?.currentMb?.let { if (it == 0) "Désactivé" else Fmt.both(it * 1024L) },
        )
    }
    RxCard("Temps réel") {
        Note("RAM utilisée")
        Spark(ram, Blue)
        Note("Swap utilisé")
        if (swap.isEmpty()) Note("Non accessible") else Spark(swap, Violet)
    }
    RxCard("Système") {
        StatRow(
            "Pression mémoire (10 s)",
            snap?.pressure?.let { String.format(Locale.FRANCE, "%.2f %% partielle · %.2f %% totale", it.someAvg10, it.fullAvg10) },
        )
        StatRow("Stockage interne libre", Fmt.both(snap?.storageFreeKb))
        StatRow("Mémoire des applications", apps?.let { list -> Fmt.both(list.sumOf { it.pssKb }) })
        StatRow("Moteur d'optimisation", if (shizuku == ShizukuManager.State.READY) "Actif · profil ${profile.label}" else "Inactif (Shizuku requis)")
    }
    Note("Toutes les valeurs proviennent du noyau ou d'Android. Une donnée refusée par le système est affichée « Non accessible ».")
}

@Composable
fun ExtensionScreen(vm: MainViewModel) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val vendorState by vm.vendorState.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hasBackup by vm.hasExtensionBackup.collectAsStateWithLifecycle()
    val pending by vm.pendingMb.collectAsStateWithLifecycle()
    val rootOk by vm.rootAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var customText by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<ExtensionOption?>(null) }

    val customMb = customText.replace(',', '.').toFloatOrNull()?.let { (it * 1024).roundToInt() }?.takeIf { it > 0 }
    // Les états observés ci-dessus déclenchent le recalcul des options.
    val options = remember(snap, vendorState, shizuku, customMb) { vm.options(customMb) }
    val real = options.filter { it.status != OptionStatus.THEORIQUE }
    val theoretical = options.filter { it.status == OptionStatus.THEORIQUE }

    RxCard("État actuel") {
        StatRow("RAM physique", Fmt.both(snap?.mem?.totalKb))
        StatRow("Swap réellement actif", Fmt.both(snap?.mem?.swapTotalKb))
        StatRow("Mécanisme constructeur", vm.device.vendor.mechanism)
        StatRow("Réglage enregistré", vendorState?.currentMb?.let { if (it == 0) "Désactivé" else Fmt.both(it * 1024L) })
        StatRow("Valeur brute de la clé", vendorState?.let { state -> state.rawValue?.let { "$it (${state.unit.name})" } ?: "Absente" })
        StatRow("Valeurs constructeur", vendorState?.listGb?.joinToString(", ") { "$it Go" })
        StatRow("Stockage libre", Fmt.both(snap?.storageFreeKb))
    }
    pending?.let {
        RxCard("Vérification en attente") {
            Note((if (it > 0) "Réglage ${it / 1024.0} Go écrit." else "Liste Samsung étendue : choisissez la taille dans RAM Plus.") + " Redémarrez le téléphone : RAM Xtreme mesurera le swap réel au prochain lancement.", Warn)
        }
    }
    RxCard("A · Réellement configurables") {
        if (real.isEmpty()) Note("Aucune valeur n'est modifiable sur cet appareil dans l'état actuel. Voir la section B pour la raison.")
        real.forEach { option ->
            OptionRow(option, enabled = !busy) { confirm = option }
        }
    }
    RxCard("B · Théoriques (non applicables ici)") {
        if (theoretical.isEmpty()) Note("Toutes les valeurs listées sont applicables.")
        theoretical.forEach { option -> OptionRow(option, enabled = false) {} }
    }
    if (vm.device.vendor.adbTunable) {
        RxCard("Méthode 2 · Débloquer dans les réglages Samsung") {
            Note(
                "Si l'écriture directe est ignorée au redémarrage, cette méthode ajoute 6, 8, 12 et 16 Go à la liste de " +
                    "l'écran RAM Plus de Samsung. Vous choisissez ensuite la taille dans cet écran : c'est Samsung qui " +
                    "l'applique. Résultat non garanti, mesuré après redémarrage.",
            )
            Button(onClick = { vm.unlockList() }, enabled = shizuku == ShizukuManager.State.READY && !busy) {
                Text("Étendre la liste Samsung")
            }
        }
    }
    RxCard("Mode root (facultatif)") {
        Note(
            "Réservé aux téléphones déjà rootés. Redimensionne réellement la ZRAM du noyau, sans plafond constructeur. " +
                "Le changement disparaît au redémarrage, ce qui le rend toujours réversible. RAM Xtreme ne roote pas le téléphone.",
        )
        StatRow(
            "Accès root",
            when (rootOk) {
                null -> "Non vérifié"
                true -> "Accordé"
                false -> "Absent ou refusé"
            },
        )
        OutlinedButton(onClick = { vm.checkRoot() }, enabled = !busy) { Text("Vérifier l'accès root") }
        if (rootOk == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 8, 12).forEach { gb ->
                    Button(onClick = { vm.rootResize(gb * 1024) }, enabled = !busy) { Text("$gb Go") }
                }
            }
            customMb?.let { mb ->
                Button(onClick = { vm.rootResize(mb) }, enabled = !busy) { Text("Appliquer $mb Mo (personnalisé)") }
            }
            Note("Une ZRAM très supérieure à la RAM physique ralentit le téléphone quand elle se remplit.", Warn)
        }
    }
    RxCard("Personnalisé") {
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it.take(5) },
            label = { Text("Taille en Go (ex. 10)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Note("La valeur saisie apparaît dans la section A ou B selon ce que le téléphone autorise.")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.restoreExtension() }, enabled = hasBackup && !busy) { Text("Restaurer l'origine") }
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Réglages système") }
    }
    Note(
        "RAM Xtreme ne crée aucun faux fichier de swap : seul le swap mesuré par le noyau compte. " +
            "Une extension de RAM utilise de la compression et parfois le stockage : possible ralentissement et usure du stockage.",
    )

    confirm?.let { option ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Appliquer ${option.sizeMb / 1024.0} Go ?") },
            text = {
                Text(
                    "${option.reason}\n\nLa valeur actuelle est sauvegardée, l'écriture est relue, puis l'effet réel " +
                        "est mesuré après redémarrage. Vous pourrez restaurer à tout moment.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyExtension(option.sizeMb)
                    confirm = null
                }) { Text("Appliquer") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun OptionRow(option: ExtensionOption, enabled: Boolean, onApply: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (option.sizeMb % 1024 == 0) "${option.sizeMb / 1024} Go" else "${option.sizeMb} Mo",
                fontWeight = FontWeight.SemiBold,
            )
            Note(
                option.reason,
                when (option.status) {
                    OptionStatus.CONFIGURABLE -> Good
                    OptionStatus.A_VERIFIER -> Warn
                    OptionStatus.THEORIQUE -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (option.status != OptionStatus.THEORIQUE) {
            Button(onClick = onApply, enabled = enabled) { Text("Appliquer") }
        }
    }
}

@Composable
fun OptimizeScreen(vm: MainViewModel) {
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val active by vm.activeProfile.collectAsStateWithLifecycle()
    val limit by vm.cachedLimit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    var custom by remember { mutableFloatStateOf(48f) }
    val ready = shizuku == ShizukuManager.State.READY

    RxCard("Analyse") {
        StatRow("Limite d'apps en cache", limit)
        StatRow("Profil actif", if (ready) active.label else "Aucun (Shizuku requis)")
        StatRow(
            "Pression mémoire",
            snap?.pressure?.let {
                when {
                    it.someAvg10 < 1 -> "Faible"
                    it.someAvg10 < 10 -> "Modérée"
                    else -> "Élevée"
                }
            },
        )
        StatRow("Mémoire compressée", Fmt.both(snap?.zram?.let { it.origBytes / 1024 }))
    }
    if (!ready) {
        Note("Sans Shizuku, une app ordinaire ne peut modifier aucun réglage mémoire du système : les profils sont désactivés.", Warn)
    }
    Profile.entries.forEach { profile ->
        RxCard(profile.label + if (ready && profile == active) "  ·  actif" else "") {
            Note(profile.description)
            if (profile == Profile.PERSONNALISE) {
                Text("Limite : ${custom.roundToInt()} apps en cache")
                Slider(value = custom, onValueChange = { custom = it }, valueRange = 4f..256f, enabled = ready)
            }
            Button(onClick = { vm.applyProfile(profile, custom.roundToInt()) }, enabled = ready && !busy) {
                Text("Appliquer")
            }
        }
    }
    OutlinedButton(onClick = { vm.restoreProfile() }, enabled = ready && !busy) { Text("Restaurer le réglage d'origine") }
    Note(
        "Aucun profil ne peut forcer Android à garder toutes les apps en mémoire : le système ferme toujours des " +
            "processus quand la RAM manque. Il peut aussi réinitialiser cette limite lors d'une mise à jour de configuration.",
    )
}

@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuManager.State.READY

    RxCard("Consommation mémoire") {
        Note(
            if (ready) "Mesure PSS réelle fournie par « dumpsys meminfo » (peut prendre quelques secondes)."
            else "Non accessible : Android masque la mémoire des autres apps. Activez Shizuku pour l'analyse.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.loadApps() }, enabled = ready && !loading) { Text("Analyser") }
            if (loading) CircularProgressIndicator()
        }
    }
    val list = apps
    if (list != null) {
        val max = list.firstOrNull()?.pssKb?.coerceAtLeast(1) ?: 1
        RxCard("${list.size} processus · ${Fmt.go(list.sumOf { it.pssKb })}") {
            list.take(40).forEach { app ->
                Column {
                    StatRow(app.name, Fmt.mo(app.pssKb))
                    LinearProgressIndicator(
                        progress = { app.pssKb.toFloat() / max },
                        modifier = Modifier.fillMaxWidth(),
                        color = Blue,
                        trackColor = Track,
                    )
                }
            }
        }
    } else if (ready && !loading) {
        Note("Aucune analyse pour l'instant.")
    }
}
