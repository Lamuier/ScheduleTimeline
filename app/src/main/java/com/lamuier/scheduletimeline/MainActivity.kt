package com.lamuier.scheduletimeline

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lamuier.scheduletimeline.notification.areAppNotificationsEnabled
import com.lamuier.scheduletimeline.notification.hasNotificationPermission
import com.lamuier.scheduletimeline.notification.hasSystemNotificationAccess
import com.lamuier.scheduletimeline.ui.edit.EditEventScreen
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import com.lamuier.scheduletimeline.ui.timeline.TimelineScreen
import com.lamuier.scheduletimeline.widget.ScheduleWidgetProviderLarge

class MainActivity : ComponentActivity() {
    // 桌面小组件 / 提醒通知深链：点击直接打开对应事件编辑页（冷启动 / 热启动均生效）
    private val widgetEventId = mutableStateOf<Long?>(null)

    // 桌面 Shortcut（长按图标）：待处理动作，Compose 侧消费后清空
    private val pendingShortcutAction = mutableStateOf<String?>(null)

    // 批量导入 Shortcut：直达主屏管理面板的导入步骤
    private val requestOpenImport = mutableStateOf(false)

    // 前往系统通知设置后返回时恢复开启偏好
    private var pendingEnableFromSettings = false
    private var pendingAlwaysOnFromSettings = false

    private val app: ScheduleApplication
        get() = application as ScheduleApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 仅冷启动消费深链，避免旋转重建把已打开的编辑页再导航一次、冲掉未保存改动。
        if (savedInstanceState == null) {
            handleDeepLinkIntent(intent)
            handleShortcutIntent(intent)
        }
        enableEdgeToEdge()
        setContent {
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModel.factory(application),
            )
            var pendingAlwaysOn by remember { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                val prefs = app.notificationPreferences
                if (granted) {
                    prefs.setPermissionRequested(false)
                    if (areAppNotificationsEnabled(this@MainActivity)) {
                        viewModel.setNotificationsEnabled(true)
                        if (pendingAlwaysOn) {
                            viewModel.setLiveUpdatesAlwaysOn(true)
                        }
                        pendingAlwaysOn = false
                    } else {
                        pendingEnableFromSettings = true
                        pendingAlwaysOnFromSettings = pendingAlwaysOn
                        pendingAlwaysOn = false
                        openNotificationSettings()
                    }
                } else {
                    prefs.setPermissionRequested(true)
                    viewModel.setNotificationsEnabled(false)
                    pendingAlwaysOn = false
                }
            }
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

            ScheduleTimelineTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val navController = rememberNavController()

                // 桌面小组件点击日程项深链：打开对应事件编辑页
                val deepEventId by widgetEventId
                LaunchedEffect(deepEventId) {
                    deepEventId?.let { id ->
                        navController.navigate("edit/$id") {
                            popUpTo("timeline") { inclusive = false }
                            launchSingleTop = true
                        }
                        widgetEventId.value = null
                    }
                }

                // 桌面 Shortcut：新增日程 / 今日日程 / 最近日程
                val shortcutAction by pendingShortcutAction
                LaunchedEffect(shortcutAction) {
                    if (shortcutAction in listOf(AppShortcuts.ACTION_TODAY, AppShortcuts.ACTION_NEXT_SCHEDULED, AppShortcuts.ACTION_IMPORT)) {
                        navController.popBackStack("timeline", inclusive = false)
                    }
                    when (shortcutAction) {
                        AppShortcuts.ACTION_ADD_EVENT -> {
                            // 固定为今天新增，避免沿用上次停留的非今日日期
                            viewModel.changeDate(LocalDate.now())
                            navController.navigate("edit") {
                                popUpTo("timeline") { inclusive = false }
                                launchSingleTop = true
                            }
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
                                val hasPermission = hasNotificationPermission(this@MainActivity)
                                if (!hasPermission) {
                                    val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                    val prefs = app.notificationPreferences
                                    if (!shouldShowRationale && prefs.isPermissionRequested()) {
                                        pendingEnableFromSettings = true
                                        pendingAlwaysOnFromSettings = alwaysOn
                                        pendingAlwaysOn = false
                                        openNotificationSettings()
                                    } else {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    }
                                } else if (!areAppNotificationsEnabled(this@MainActivity)) {
                                    pendingEnableFromSettings = true
                                    pendingAlwaysOnFromSettings = alwaysOn
                                    pendingAlwaysOn = false
                                    openNotificationSettings()
                                } else {
                                    viewModel.setNotificationsEnabled(true)
                                    if (alwaysOn) viewModel.setLiveUpdatesAlwaysOn(true)
                                    pendingAlwaysOn = false
                                }
                            },
                        )
                    }
                    composable("edit") { entry ->
                        EditEventScreen(
                            sessionId = entry.id,
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
                            sessionId = entry.id,
                            eventId = id,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = app.notificationPreferences
        if (hasSystemNotificationAccess(this)) {
            if (pendingEnableFromSettings) {
                prefs.setEnabled(true)
                if (pendingAlwaysOnFromSettings) {
                    prefs.setAlwaysOn(true)
                }
            }
        } else if (prefs.enabled.value) {
            prefs.setEnabled(false)
        }
        pendingEnableFromSettings = false
        pendingAlwaysOnFromSettings = false
        app.refreshNotifications()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent == null) return
        val extraId = intent.getLongExtra(
            ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID,
            -1L,
        )
        val reminderId = parseReminderEventId(intent)
        val targetId = when {
            extraId != -1L -> extraId
            reminderId != null && reminderId > 0L -> reminderId
            else -> null
        }
        if (targetId != null) {
            widgetEventId.value = targetId
        }
        if (extraId != -1L) {
            intent.removeExtra(ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID)
        }
        if (intent.action == "${packageName}.OPEN_REMINDER" ||
            intent.action == "${packageName}.OPEN_WIDGET_EVENT" ||
            intent.action == ScheduleWidgetProviderLarge.ACTION_WIDGET_ITEM
        ) {
            intent.action = null
        }
        val data = intent.data
        if (data?.scheme == "schedule" &&
            (data.host == "reminder" || data.host == "widget")
        ) {
            intent.data = null
        }
    }

    private fun parseReminderEventId(intent: Intent): Long? {
        val data = intent.data
        val matchesAction = intent.action == "${packageName}.OPEN_REMINDER"
        val matchesUri = data?.scheme == "schedule" && data.host == "reminder"
        if (!matchesAction && !matchesUri) return null
        return data?.lastPathSegment?.toLongOrNull()
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            AppShortcuts.ACTION_ADD_EVENT,
            AppShortcuts.ACTION_TODAY,
            AppShortcuts.ACTION_NEXT_SCHEDULED,
            AppShortcuts.ACTION_IMPORT,
                -> {
                AppShortcuts.reportUsed(this, intent.action)
                pendingShortcutAction.value = intent.action
                intent.action = null
            }
        }
    }
}
