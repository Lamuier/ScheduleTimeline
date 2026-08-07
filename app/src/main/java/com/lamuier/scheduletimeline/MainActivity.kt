package com.lamuier.scheduletimeline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lamuier.scheduletimeline.ui.edit.EditEventScreen
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import com.lamuier.scheduletimeline.ui.timeline.TimelineScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModel.factory(application),
            )
            var pendingAlwaysOn by remember { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                viewModel.setNotificationsEnabled(granted)
                if (granted && pendingAlwaysOn) {
                    viewModel.setLiveUpdatesAlwaysOn(true)
                }
                pendingAlwaysOn = false
            }
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            ScheduleTimelineTheme(themeMode = themeMode) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "timeline",
                ) {
                    composable("timeline") {
                        TimelineScreen(
                            viewModel = viewModel,
                            onAdd = { navController.navigate("edit") },
                            onEditEvent = { id -> navController.navigate("edit/$id") },
                            onRequestNotificationPermission = { alwaysOn ->
                                pendingAlwaysOn = alwaysOn
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.setNotificationsEnabled(true)
                                    if (alwaysOn) viewModel.setLiveUpdatesAlwaysOn(true)
                                    pendingAlwaysOn = false
                                } else {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                            },
                        )
                    }
                    composable("edit") {
                        EditEventScreen(
                            eventId = null,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "edit/{eventId}",
                        arguments = listOf(
                            navArgument("eventId") { type = NavType.LongType },
                        ),
                    ) { entry ->
                        val id = entry.arguments?.getLong("eventId")
                        EditEventScreen(
                            eventId = id,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
