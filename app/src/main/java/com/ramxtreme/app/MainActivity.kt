package com.ramxtreme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramxtreme.app.ui.AppsScreen
import com.ramxtreme.app.ui.Bad
import com.ramxtreme.app.ui.CompatScreen
import com.ramxtreme.app.ui.ExtensionScreen
import com.ramxtreme.app.ui.Good
import com.ramxtreme.app.ui.HomeScreen
import com.ramxtreme.app.ui.Night
import com.ramxtreme.app.ui.Note
import com.ramxtreme.app.ui.OptimizeScreen
import com.ramxtreme.app.ui.RamXtremeTheme
import com.ramxtreme.app.ui.RxCard
import com.ramxtreme.app.ui.SettingsScreen
import com.ramxtreme.app.ui.ShizukuScreen

enum class Screen(val title: String, val tab: String?, val icon: ImageVector?) {
    HOME("RAM Xtreme", "Accueil", Icons.Filled.Home),
    EXTENSION("RAM Extension", "Extension", Icons.Filled.Add),
    OPTIMIZE("Optimisation", "Profils", Icons.Filled.Build),
    APPS("Applications", "Applis", Icons.Filled.Info),
    MORE("Plus", "Plus", Icons.Filled.Menu),
    COMPAT("Compatibilité", null, null),
    SHIZUKU("Shizuku", null, null),
    SETTINGS("Paramètres", null, null),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RamXtremeTheme { RamXtremeApp() } }
    }
}

@Composable
fun RamXtremeApp(vm: MainViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val message by vm.message.collectAsStateWithLifecycle()
    val isSub = screen.tab == null

    BackHandler(enabled = screen != Screen.HOME) { screen = if (isSub) Screen.MORE else Screen.HOME }

    Scaffold(
        containerColor = Night,
        bottomBar = {
            NavigationBar(containerColor = Night) {
                Screen.entries.filter { it.tab != null }.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item || (item == Screen.MORE && isSub),
                        onClick = { screen = item },
                        icon = { item.icon?.let { Icon(it, contentDescription = item.tab) } },
                        label = { Text(item.tab ?: "") },
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = screen, label = "écran", modifier = Modifier.padding(padding)) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (current.tab == null) TextButton(onClick = { screen = Screen.MORE }) { Text("‹ Retour") }
                Text(current.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                message?.let { outcome ->
                    RxCard(if (outcome.ok) "Opération vérifiée" else "Opération non appliquée") {
                        Note(outcome.message, if (outcome.ok) Good else Bad)
                        TextButton(onClick = { vm.dismissMessage() }) { Text("Fermer") }
                    }
                }
                when (current) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.EXTENSION -> ExtensionScreen(vm)
                    Screen.OPTIMIZE -> OptimizeScreen(vm)
                    Screen.APPS -> AppsScreen(vm)
                    Screen.MORE -> listOf(Screen.COMPAT, Screen.SHIZUKU, Screen.SETTINGS).forEach { target ->
                        OutlinedButton(onClick = { screen = target }, modifier = Modifier.fillMaxWidth()) {
                            Text(target.title)
                        }
                    }
                    Screen.COMPAT -> CompatScreen(vm)
                    Screen.SHIZUKU -> ShizukuScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}
