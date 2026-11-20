package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.KaraokeRecordScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MixStudioScreen
import com.example.ui.screens.TrackEditorScreen
import com.example.ui.theme.VocaStageTheme
import com.example.ui.viewmodel.KaraokeRecordViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.MixStudioViewModel
import com.example.ui.viewmodel.TrackEditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocaStageTheme {
                VocaStageNavHost()
            }
        }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val TRACK_EDITOR = "track_editor/{trackId}"
    const val KARAOKE_RECORD = "karaoke_record/{trackId}"
    const val MIX_STUDIO = "mix_studio/{mixId}"

    fun trackEditor(trackId: Long = 0L) = "track_editor/$trackId"
    fun karaokeRecord(trackId: Long) = "karaoke_record/$trackId"
    fun mixStudio(mixId: Long) = "mix_studio/$mixId"
}

@Composable
fun VocaStageNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        composable(Routes.LIBRARY) {
            val libraryViewModel: LibraryViewModel = viewModel()
            LibraryScreen(
                viewModel = libraryViewModel,
                onImportNewSong = {
                    navController.navigate(Routes.trackEditor(0L))
                },
                onEditTrack = { trackId ->
                    navController.navigate(Routes.trackEditor(trackId))
                },
                onRecordTrack = { trackId ->
                    navController.navigate(Routes.karaokeRecord(trackId))
                },
                onOpenMixStudio = { mixId ->
                    navController.navigate(Routes.mixStudio(mixId))
                }
            )
        }

        composable(
            route = Routes.TRACK_EDITOR,
            arguments = listOf(navArgument("trackId") { type = NavType.LongType })
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L
            val editorViewModel: TrackEditorViewModel = viewModel()
            TrackEditorScreen(
                trackId = trackId,
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.KARAOKE_RECORD,
            arguments = listOf(navArgument("trackId") { type = NavType.LongType })
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L
            val recordViewModel: KaraokeRecordViewModel = viewModel()
            KaraokeRecordScreen(
                trackId = trackId,
                viewModel = recordViewModel,
                onBack = { navController.popBackStack() },
                onMixCreated = { mixId ->
                    navController.navigate(Routes.mixStudio(mixId)) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.MIX_STUDIO,
            arguments = listOf(navArgument("mixId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mixId = backStackEntry.arguments?.getLong("mixId") ?: 0L
            val mixViewModel: MixStudioViewModel = viewModel()
            MixStudioScreen(
                mixId = mixId,
                viewModel = mixViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
