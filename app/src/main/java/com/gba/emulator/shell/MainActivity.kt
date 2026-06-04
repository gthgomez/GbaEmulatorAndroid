package com.gba.emulator.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.gba.emulator.shell.ui.GbaEmulatorScreen
import com.gba.emulator.shell.ui.GbaEmulatorTheme
import com.gba.emulator.shell.ui.GameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            GbaEmulatorTheme {
                val session = remember { EmulatorSession() }
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }

                DisposableEffect(Unit) {
                    onDispose { session.destroy() }
                }

                when (val current = route) {
                    AppRoute.Home -> {
                        GbaEmulatorScreen(
                            session = session,
                            onPlayRom = { loaded ->
                                route = AppRoute.Play(loaded.title)
                            },
                        )
                    }

                    is AppRoute.Play -> {
                        GameScreen(
                            session = session,
                            romTitle = current.title,
                            onExit = { route = AppRoute.Home },
                        )
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
