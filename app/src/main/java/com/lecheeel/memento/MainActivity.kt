package com.lecheeel.memento

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lecheeel.memento.data.NotificationRepository
import com.lecheeel.memento.data.NotificationSyncManager
import com.lecheeel.memento.ui.AppListScreen
import com.lecheeel.memento.ui.AppTopBar
import com.lecheeel.memento.ui.HomeScreen
import com.lecheeel.memento.ui.SettingsScreen
import com.lecheeel.memento.ui.theme.MementoTheme
import kotlinx.coroutines.delay

private enum class AppScreen {
    HOME,
    SETTINGS,
    APP_LIST,
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
            var settings by remember { mutableStateOf(NotificationRepository.snapshot()) }
            var listenerGranted by remember { mutableStateOf(isListenerEnabled(this)) }
            var queueSize by remember { mutableStateOf(NotificationSyncManager.get(this).queueSize()) }

            fun refreshState() {
                settings = NotificationRepository.snapshot()
                listenerGranted = isListenerEnabled(this)
                queueSize = NotificationSyncManager.get(this).queueSize()
            }

            LaunchedEffect(Unit) {
                while (true) {
                    refreshState()
                    delay(1_000)
                }
            }

            MementoTheme(dynamicColor = settings.useDynamicColor) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        when (screen) {
                            AppScreen.SETTINGS -> AppTopBar(title = "设置") { screen = AppScreen.HOME }
                            AppScreen.APP_LIST -> AppTopBar(title = "应用列表") { screen = AppScreen.SETTINGS }
                            AppScreen.HOME -> Unit
                        }
                    },
                ) { innerPadding ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            settings = settings,
                            listenerGranted = listenerGranted,
                            queueSize = queueSize,
                            modifier = Modifier.padding(innerPadding),
                            onOpenSettings = { screen = AppScreen.SETTINGS },
                            onOpenNotificationSettings = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onToggleCapture = { enabled ->
                                NotificationRepository.update { it.copy(captureEnabled = enabled) }
                                if (enabled) NotificationSyncManager.get(this).flushAsync()
                                refreshState()
                            },
                        )

                        AppScreen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            modifier = Modifier.padding(innerPadding),
                            onOpenApps = { screen = AppScreen.APP_LIST },
                        )

                        AppScreen.APP_LIST -> AppListScreen(
                            settings = settings,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

private fun isListenerEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    return flat.contains(context.packageName)
}
