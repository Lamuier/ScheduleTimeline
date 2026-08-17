package com.lamuier.scheduletimeline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import java.time.LocalDate
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

    // 桌面 Shortcut（长按图标）：待处理动作，Compose 侧消费后清空
    private val pendingShortcutAction = mutableStateOf<String?>(null)

    // 批量导入 Shortcut：直达主屏管理面板的导入步骤
    private val requestOpenImport = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)
        // 仅真实冷启动处理，防止旋转等重建时重放已消费的 shortcut intent
        if (savedInstanceState == null) handleShortcutIntent(intent)
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

                // 桌面 Shortcut：新增日程 / 今日日程 / 最近日程
                val shortcutAction by pendingShortcutAction
                LaunchedEffect(shortcutAction) {
                    when (shortcutAction) {
                        AppShortcuts.ACTION_ADD_EVENT -> {
                            // 固定为今天新增，避免沿用上次停留的非今日日期
                            viewModel.changeDate(LocalDate.now())
                            navController.navigate("edit")
                        }
                        AppShortcuts.ACTION_TODAY -> viewModel.changeDate(LocalDate.now())
                        AppShortcuts.ACTION_NEXT_SCHEDULED ->
                            viewModel.jumpToNextScheduledDate()
                        // 导入面板在主屏内部，走独立信号由 TimelineScreen 消费
                        AppShortcuts.ACTION_IMPORT -> requestOpenImport.value = true
                    }
                    if (shortcutAction != null) pendingShortcutAction.value = null
                }

                NavHost(
                    navController = navController,
                    startDestination = "timeline",
                ) {
                    composable("timeline") {
                        val importRequest by requestOpenImport
                        TimelineScreen(
                            viewModel = viewModel,
                            onAdd = { navController.navigate("edit") },
                            onEditEvent = { id -> navController.navigate("edit/$id") },
                            requestOpenImport = importRequest,
                            onOpenImportConsumed = { requestOpenImport.value = false },
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
        handleShortcutIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val id = intent?.getLongExtra(
            ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID,
            -1L,
        ) ?: -1L
        if (id != -1L) widgetEventId.value = id
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            AppShortcuts.ACTION_ADD_EVENT,
            AppShortcuts.ACTION_TODAY,
            AppShortcuts.ACTION_NEXT_SCHEDULED,
            AppShortcuts.ACTION_IMPORT,
                -> pendingShortcutAction.value = intent.action
        }
    }
}
