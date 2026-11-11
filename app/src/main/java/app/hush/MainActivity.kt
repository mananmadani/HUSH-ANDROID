package app.hush

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.hush.ui.HomeScreen
import app.hush.ui.HushTheme
import app.hush.ui.LibraryScreen
import app.hush.ui.LikedScreen
import app.hush.ui.MiniPlayer
import app.hush.ui.PlayerScreen
import app.hush.ui.PlaylistScreen
import app.hush.ui.SearchScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Needed on Android 13+ so the playback notification (and its controls) can show.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { HushTheme { HushRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("search", "Search", Icons.Filled.Search),
    Tab("library", "Library", Icons.Filled.LibraryMusic)
)

@Composable
private fun HushRoot(vm: HushVm = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val inPlayer = route == "player"
    val onTab = TABS.any { it.route == route }
    val player by vm.player.state.collectAsState()

    Scaffold(
        bottomBar = {
            if (!inPlayer) {
                Column(modifier = if (onTab) Modifier else Modifier.navigationBarsPadding()) {
                    if (player.mediaId != null) {
                        MiniPlayer(
                            state = player,
                            onToggle = vm.player::toggle,
                            onOpen = { nav.navigate("player") { launchSingleTop = true } }
                        )
                    }
                    if (onTab) {
                        NavigationBar {
                            TABS.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.route,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm) }
            composable("search") { SearchScreen(vm) }
            composable("library") {
                LibraryScreen(
                    vm,
                    onOpenLiked = { nav.navigate("liked") },
                    onOpenPlaylist = { nav.navigate("playlist/$it") }
                )
            }
            composable("liked") { LikedScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                "playlist/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { PlaylistScreen(vm, it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }) }
            composable("player") { PlayerScreen(vm, onClose = { nav.popBackStack() }) }
        }
    }
}
