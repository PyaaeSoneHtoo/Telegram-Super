package com.notioff.telegramsuper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.drinkless.tdlib.TdApi
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TDLib
        TelegramClient.initialize(applicationContext)
        
        setContent {
            val redBlackColorScheme = darkColorScheme(
                primary = Color(0xFFE53935), // Red
                onPrimary = Color.White,
                primaryContainer = Color(0xFFB71C1C), // Dark Red
                onPrimaryContainer = Color.White,
                secondary = Color(0xFFEF5350), // Lighter Red
                onSecondary = Color.Black,
                background = Color(0xFF121212), // Very dark gray/Black
                onBackground = Color.White,
                surface = Color(0xFF1E1E1E), // Slightly lighter black for cards/rows
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFB0B0B0) // Light gray for secondary text
            )

            MaterialTheme(colorScheme = redBlackColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by TelegramClient.authState.collectAsState()
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
                    
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        // Record or ignore if rejected
                    }
                    
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    when (authState) {
                        is TdApi.AuthorizationStateReady -> {
                            BackHandler(enabled = currentScreen != Screen.Main) {
                                currentScreen = Screen.Main
                            }
                            when (currentScreen) {
                                Screen.Main -> {
                                    ChatListScreen(
                                        onChatClick = { chatId ->
                                            currentScreen = Screen.Chat(chatId)
                                        },
                                        onSettingsClick = {
                                            currentScreen = Screen.Settings
                                        }
                                    )
                                }
                                Screen.Settings -> {
                                    SettingsScreen(
                                        onBack = { currentScreen = Screen.Main },
                                        onLogout = { TelegramClient.logOut() },
                                        onPrivacyClick = { currentScreen = Screen.Privacy },
                                        onStorageClick = { currentScreen = Screen.StorageSettings }
                                    )
                                }
                                Screen.StorageSettings -> {
                                    StorageSettingsScreen(
                                        onBack = { currentScreen = Screen.Settings }
                                    )
                                }
                                Screen.Privacy -> {
                                    PrivacyScreen(
                                        onBack = { currentScreen = Screen.Settings },
                                        onOpenChat = { chatId -> currentScreen = Screen.Chat(chatId) }
                                    )
                                }
                                is Screen.Chat -> {
                                    ChatScreen(
                                        chatId = (currentScreen as Screen.Chat).chatId,
                                        onBack = { currentScreen = Screen.Main }
                                    )
                                }
                            }
                        }
                        is TdApi.AuthorizationStateLoggingOut -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            AuthScreen()
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
    object Privacy : Screen()
    object StorageSettings : Screen()
    data class Chat(val chatId: Long) : Screen()
}
