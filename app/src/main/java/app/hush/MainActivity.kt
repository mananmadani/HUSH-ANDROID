package app.hush

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.DisposableEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import app.hush.net.CollectionItem
import app.hush.ui.ArtistScreen
import app.hush.ui.CollectionScreen
import app.hush.ui.Dusk
import app.hush.ui.LocalOpenArtist
import app.hush.ui.LocalOpenCollection
import app.hush.ui.FloatingNav
import app.hush.ui.HomeScreen
import app.hush.ui.HushTheme
import app.hush.ui.LibraryScreen
import app.hush.ui.LikedScreen
import app.hush.ui.MiniPlayer
import app.hush.ui.PlayerScreen
import app.hush.ui.PlaylistScreen
import app.hush.ui.SearchScreen
import app.hush.ui.SettingsScreen
import app.hush.ui.StatsScreen

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

        setContent {
            // Keep the status and navigation bar icons readable in both themes.
            val light = Prefs.light
            DisposableEffect(light) {
                enableEdgeToEdge(
                    statusBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    else SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    else SystemBarStyle.dark(Color.TRANSPARENT)
                )
                onDispose { }
            }
            HushTheme {
                // Without this, plain Text falls back to black on our dark background.
                CompositionLocalProvider(LocalContentColor provides Dusk.Text) { HushRoot() }
            }
        }
    }
}

private fun enc(s: String): String =
    Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

private fun dec(s: String): String =
    String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))

private val TAB_ROUTES = listOf("home", "search", "library")

/** Wraps a normal screen so it sits below the status bar. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
}

@Composable
private fun HushRoot(vm: HushVm = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val inPlayer = route == "player"
    val onTab = route in TAB_ROUTES
    val player by vm.player.state.collectAsState()
    val accent = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .background(Dusk.Bg)
            .background(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.16f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(200f, 0f),
                    radius = 1100f
                )
            )
    ) {
        CompositionLocalProvider(
            LocalOpenArtist provides { name: String ->
                if (route == "player") nav.popBackStack()
                nav.navigate("artist/${enc(name)}")
            },
            LocalOpenCollection provides { c: CollectionItem ->
                if (route == "player") nav.popBackStack()
                nav.navigate("collection/${enc(listOf(c.url, c.title, c.thumb.orEmpty(), c.subtitle, c.count.toString()).joinToString("\n"))}")
            }
        ) {
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable("home") {
                Page {
                    HomeScreen(
                        vm,
                        onSettings = { nav.navigate("settings") },
                        onOpenLiked = { nav.navigate("liked") },
                        onOpenPlaylist = { nav.navigate("playlist/$it") }
                    )
                }
            }
            composable("search") { Page { SearchScreen(vm) } }
            composable("library") {
                Page {
                    LibraryScreen(
                        vm,
                        onOpenLiked = { nav.navigate("liked") },
                        onOpenPlaylist = { nav.navigate("playlist/$it") },
                        onOpenStats = { nav.navigate("stats") }
                    )
                }
            }
            composable("stats") { Page { StatsScreen(vm, onBack = { nav.popBackStack() }) } }
            composable("liked") { Page { LikedScreen(vm, onBack = { nav.popBackStack() }) } }
            composable(
                "playlist/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { Page { PlaylistScreen(vm, it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }) } }
            composable(
                "artist/{k}",
                arguments = listOf(navArgument("k") { type = NavType.StringType })
            ) {
                val name = it.arguments?.getString("k")?.let(::dec).orEmpty()
                Page { ArtistScreen(vm, name, onBack = { nav.popBackStack() }) }
            }
            composable(
                "collection/{k}",
                arguments = listOf(navArgument("k") { type = NavType.StringType })
            ) {
                val parts = it.arguments?.getString("k")?.let(::dec).orEmpty().split("\n")
                val item = CollectionItem(
                    url = parts.getOrElse(0) { "" },
                    title = parts.getOrElse(1) { "" },
                    thumb = parts.getOrElse(2) { "" }.ifBlank { null },
                    subtitle = parts.getOrElse(3) { "" },
                    count = parts.getOrElse(4) { "0" }.toLongOrNull() ?: 0L
                )
                Page { CollectionScreen(vm, item, onBack = { nav.popBackStack() }) }
            }
            composable("settings") { Page { SettingsScreen(vm, onBack = { nav.popBackStack() }) } }
            composable(
                "player",
                enterTransition = { slideInVertically(tween(280)) { it } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { slideOutVertically(tween(280)) { it } }
            ) { PlayerScreen(vm, onClose = { nav.popBackStack() }) }
        }
        }

        if (!inPlayer) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                if (player.mediaId != null) {
                    MiniPlayer(
                        state = player,
                        onToggle = vm.player::toggle,
                        onOpen = { nav.navigate("player") { launchSingleTop = true } }
                    )
                    Box(Modifier.padding(top = 8.dp))
                }
                if (onTab) {
                    FloatingNav(
                        current = route,
                        onSelect = { target ->
                            nav.navigate(target) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}
