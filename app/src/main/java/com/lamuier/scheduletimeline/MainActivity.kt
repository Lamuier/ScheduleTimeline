package com.lamuier.scheduletimeline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
import com.lamuier.scheduletimeline.widget.ScheduleWidgetProviderLarge

class MainActivity : ComponentActivity() {
    // 桌面小组件深链：点击日程项直接打开对应事件编辑页（冷启动 / 热启动均生效）
    private val widgetEventId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)
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

                // 桌面小组件点击日程项深链：打开对应事件编辑页
                val deepEventId by widgetEventId
                LaunchedEffect(deepEventId) {
                    deepEventId?.let { id ->
                        navController.navigate("edit/$id")
                        widgetEventId.value = null
                    }
                }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val id = intent?.getLongExtra(
            ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID,
            -1L,
        ) ?: -1L
        if (id != -1L) widgetEventId.value = id
    }
}
