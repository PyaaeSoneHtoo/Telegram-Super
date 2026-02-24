package com.notioff.telegramsuper

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = viewModel(),
    onChatClick: (Long, Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val chats by viewModel.chatList.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentVirtualId by viewModel.currentVirtualFolderId.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var globalResults by remember { mutableStateOf<List<TdApi.Chat>>(emptyList()) }
    var isGlobalSearching by remember { mutableStateOf(false) }

    // Local filter
    val localMatches = if (isSearchActive && searchQuery.isNotBlank()) {
        chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
    } else chats

    // Debounced global search
    LaunchedEffect(searchQuery) {
        if (isSearchActive && searchQuery.length >= 2) {
            isGlobalSearching = true
            kotlinx.coroutines.delay(400)
            TelegramClient.searchChatsGlobal(searchQuery) { results ->
                // Filter out chats already in local list
                val localIds = localMatches.map { it.id }.toSet()
                globalResults = results.filter { it.id !in localIds }
                isGlobalSearching = false
            }
        } else {
            globalResults = emptyList()
        }
    }

    val displayedChats = localMatches

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Search chats...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        } else {
                            Text("Telegram Super v1.5", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                )
                
                // Tabs for folders
                val virtualTabs = listOf(
                    TelegramClient.VIRTUAL_ID_ALL to "All",
                    TelegramClient.VIRTUAL_ID_PERSONAL to "Personal",
                    TelegramClient.VIRTUAL_ID_GROUPS to "Groups",
                    TelegramClient.VIRTUAL_ID_CHANNELS to "Channels"
                )
                
                val allFolderItems = virtualTabs.map { it.first to it.second } + 
                                  folders.map { it.id to it.name.text.text }
                
                val selectedIndex = allFolderItems.indexOfFirst { it.first == currentVirtualId }.coerceAtLeast(0)
                
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    allFolderItems.forEach { (id, name) ->
                        Tab(
                            selected = currentVirtualId == id,
                            onClick = { viewModel.selectFolder(id) },
                            text = { Text(name, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (displayedChats.isEmpty() && !isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(displayedChats, key = { it.id }) { chat ->
                    var showContextMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        val chatType = chat.type
                        val isPossibleForum = chatType is TdApi.ChatTypeSupergroup && !chatType.isChannel
                        ChatListItem(
                            chat = chat, 
                            onClick = { onChatClick(chat.id, chat.viewAsTopics || isPossibleForum) },
                            onLongClick = { showContextMenu = true }
                        )
                        
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false }
                        ) {
                            val isPinned = chat.positions.any { it.isPinned }
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Unpin" else "Pin") },
                                onClick = {
                                    viewModel.pinChat(chat.id, !isPinned)
                                    showContextMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                            )
                            val isUnread = chat.unreadCount > 0 || chat.isMarkedAsUnread
                            DropdownMenuItem(
                                text = { Text(if (isUnread) "Mark as Read" else "Mark as Unread") },
                                onClick = {
                                    viewModel.markAsUnread(chat.id, !isUnread)
                                    showContextMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear History") },
                                onClick = {
                                    viewModel.clearHistory(chat.id, false)
                                    showContextMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Chat") },
                                onClick = {
                                    viewModel.deleteChat(chat.id)
                                    showContextMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block / Leave") },
                                onClick = {
                                    viewModel.blockChat(chat)
                                    showContextMenu = false
                                }
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                // Global search section below local results
                if (isSearchActive && searchQuery.length >= 2) {
                    item {
                        Text(
                            text = "Global Results",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (isGlobalSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (globalResults.isEmpty()) {
                        item {
                            Text(
                                text = "No global results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        items(globalResults, key = { "global_${it.id}" }) { chat ->
                            val chatType = chat.type
                            val isPossibleForum = chatType is TdApi.ChatTypeSupergroup && !chatType.isChannel
                            ChatListItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id, chat.viewAsTopics || isPossibleForum) },
                                onLongClick = {}
                            )
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(chat: TdApi.Chat, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        val initial = if (chat.title.isNotEmpty()) chat.title.take(1).uppercase() else "?"
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Time
                val timeStr = chat.lastMessage?.date?.let { dateInt ->
                    val date = Date(dateInt * 1000L)
                    val today = Calendar.getInstance()
                    val msgDate = Calendar.getInstance().apply { time = date }
                    
                    val format = if (today.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)) {
                        SimpleDateFormat("h:mm a", Locale.getDefault())
                    } else {
                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    }
                    format.format(date)
                } ?: ""
                
                val isPinned = chat.positions.any { it.isPinned }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            val lastMessageText = when (val content = chat.lastMessage?.content) {
                is TdApi.MessageText -> content.text.text
                is TdApi.MessagePhoto -> "📷 Photo" + if (content.caption.text.isNotEmpty()) ": ${content.caption.text}" else ""
                is TdApi.MessageVideo -> "🎥 Video" + if (content.caption.text.isNotEmpty()) ": ${content.caption.text}" else ""
                is TdApi.MessageAnimation -> "🎬 GIF"
                is TdApi.MessageSticker -> "${content.sticker.emoji} Sticker"
                is TdApi.MessageAnimatedEmoji -> "${content.emoji} Animated emoji"
                is TdApi.MessageDocument -> "📄 ${content.document.fileName.ifEmpty { "Document" }}"
                is TdApi.MessageVoiceNote -> "🎤 Voice message"
                is TdApi.MessageAudio -> "🎵 ${content.audio.title.ifEmpty { "Audio" }}"
                is TdApi.MessageVideoNote -> "📹 Video message"
                is TdApi.MessageContact -> "👤 Contact"
                is TdApi.MessageLocation -> "📍 Location"
                is TdApi.MessagePoll -> "📊 ${content.poll.question.text}"
                is TdApi.MessageCall -> "📞 Call"
                is TdApi.MessagePinMessage -> "📌 Pinned a message"
                is TdApi.MessageGameScore -> "🎮 Game score"
                null -> "No messages yet"
                else -> ""
            }
            
            Text(
                text = lastMessageText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
