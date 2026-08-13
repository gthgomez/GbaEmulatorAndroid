package com.gba.emulator.shell

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.gba.emulator.shell.ui.GbaEmulatorScreen
import com.gba.emulator.shell.ui.GbaEmulatorTheme
import com.gba.emulator.shell.ui.GameScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            val prefs = remember { getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var darkModeOverrideStr by remember {
                mutableStateOf(prefs.getString("dark_mode_override", "system") ?: "system")
            }
            val darkModeOverride = when (darkModeOverrideStr) {
                "light" -> false
                "dark" -> true
                else -> null
            }

            GbaEmulatorTheme(darkModeOverride = darkModeOverride) {
                val session = remember { EmulatorSession() }
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }

                DisposableEffect(Unit) {
                    onDispose { session.destroy() }
                }

                LaunchedEffect(Unit) {
                    val context = this@MainActivity
                    if (BuildConfig.DEBUG && PushedRomLoader.hasPushedRom(context)) {
                        val result = withContext(Dispatchers.IO) {
                            PushedRomLoader.load(context)
                        }
                        result.onSuccess { loaded ->
                            withContext(Dispatchers.Default) {
                                session.loadRom(loaded)
                            }
                            route = AppRoute.Play(loaded.title)
                        }
                    }
                }

                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        if (targetState is AppRoute.Play) {
                            (slideInVertically(animationSpec = tween(300), initialOffsetY = { it }) + fadeIn(animationSpec = tween(300)))
                                .togetherWith(fadeOut(animationSpec = tween(150)))
                        } else {
                            fadeIn(animationSpec = tween(200))
                                .togetherWith(slideOutVertically(animationSpec = tween(200), targetOffsetY = { it }) + fadeOut(animationSpec = tween(150)))
                        }
                    },
                    label = "screenTransition",
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) { currentRoute ->
                    when (currentRoute) {
                        AppRoute.Home -> {
                            GbaEmulatorScreen(
                                session = session,
                                darkModeOverride = darkModeOverrideStr,
                                onDarkModeOverrideChanged = { newMode ->
                                    darkModeOverrideStr = newMode
                                    prefs.edit().putString("dark_mode_override", newMode).apply()
                                },
                                onPlayRom = { loaded ->
                                    route = AppRoute.Play(loaded.title)
                                },
                                onResumePlay = {
                                    val title = session.getCartridgeMetadata()?.title ?: "Game"
                                    route = AppRoute.Play(title)
                                },
                            )
                        }

                        is AppRoute.Play -> {
                            GameScreen(
                                session = session,
                                romTitle = currentRoute.title,
                                onExit = { route = AppRoute.Home },
                            )
                        }
                    }
                }
            }
        }
    }

    private sealed interface AppRoute {
        data object Home : AppRoute
        data class Play(val title: String) : AppRoute
    }
}
