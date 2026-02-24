package com.notioff.telegramsuper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.drinkless.tdlib.TdApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit
) {
    var stats by remember { mutableStateOf<TdApi.StorageStatistics?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isClearing by remember { mutableStateOf(false) }
    var chatNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        TelegramClient.getStorageStatistics(50) { result ->
            stats = result
            isLoading = false
            
            // Fetch names for all chats
            result?.byChat?.forEach { chatStat ->
                if (chatStat.chatId != 0L) {
                    TelegramClient.getChatInfo(chatStat.chatId) { chat ->
                        if (chat != null) {
                            chatNames = chatNames + (chatStat.chatId to chat.title)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Usage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (stats != null) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = "Total Storage Used: ${formatSize(stats!!.size)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Files: ${stats!!.count}", style = MaterialTheme.typography.bodyLarge)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            isClearing = true
                            TelegramClient.optimizeStorage(
                                sizeLimit = 0,
                                ttl = 0,
                                countLimit = 0,
                                immunityDelay = 0,
                                chatLimit = 50
                            ) { newStats ->
                                stats = newStats
                                isClearing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isClearing && stats!!.size > 0
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Cache")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Cache")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Usage by Chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(stats!!.byChat.sortedByDescending { it.size }) { chatStat ->
                            val title = if (chatStat.chatId == 0L) "Other Files" else chatNames[chatStat.chatId] ?: "Loading..."
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = title, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${chatStat.count} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(text = formatSize(chatStat.size), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Failed to load storage statistics.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

fun formatSize(sizeInBytes: Long): String {
    val kb = sizeInBytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$sizeInBytes B"
    }
}
