package com.notioff.telegramsuper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.viewmodel.compose.viewModel
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Long,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(key = chatId.toString(), factory = ChatViewModelFactory(chatId))
) {
    LaunchedEffect(chatId) {
        viewModel.activate()
    }

    val chatInfo by viewModel.chatInfo.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val senderNames by viewModel.senderNames.collectAsState()
    
    val isGroupChat = chatInfo?.type is TdApi.ChatTypeBasicGroup || chatInfo?.type is TdApi.ChatTypeSupergroup
    
    val listState = rememberLazyListState()
    
    // Pagination trigger
    /*
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && visibleItems.last().index == messages.size - 1) {
                    viewModel.loadMoreMessages()
                }
            }
    }
    */

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = chatInfo?.title ?: "Chat", 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            ChatInputArea(onSendMessage = { text -> viewModel.sendMessage(text) })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val senderName = if (!message.isOutgoing && isGroupChat) {
                            val idStr = viewModel.getSenderIdString(message.senderId)
                            senderNames[idStr]
                        } else null
                        
                        val topicName = if (message.topicId is TdApi.MessageTopicForum) {
                            val forumId = (message.topicId as TdApi.MessageTopicForum).forumTopicId.toLong()
                            viewModel.topicNames.value[forumId]
                        } else null

                        MessageBubble(message, senderName, topicName)
                    }
                    
                    item {
                        LaunchedEffect(true) {
                            viewModel.loadMoreMessages()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: TdApi.Message, senderName: String? = null, topicName: String? = null) {
    // Determine if the message was sent by the current user
    val isOutgoing = message.isOutgoing

    val backgroundColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // Reddish for outgoing based on theme
    } else {
        MaterialTheme.colorScheme.surfaceVariant // Gray/dark surface for incoming
    }
    
    val textColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isOutgoing) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Topic name (if applicable)
            if (topicName != null) {
                Text(
                    text = "Topic: $topicName",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            
            // Sender name (if applicable, incoming group messages)
            if (senderName != null) {
                Text(
                    text = senderName,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            
            // Render content based on type
            when (val content = message.content) {
                is TdApi.MessageText -> {
                    Text(
                        text = content.text.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is TdApi.MessagePhoto -> {
                    val highestRes = content.photo.sizes.maxByOrNull { it.width * it.height }
                    val photoFile = highestRes?.photo
                    
                    if (photoFile != null) {
                        MediaContentView(file = photoFile, isVideo = false)
                    } else {
                        Text("📷 Photo", color = textColor)
                    }
                    if (content.caption.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = content.caption.text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is TdApi.MessageVideo -> {
                    VideoContentView(
                        videoFile = content.video.video,
                        thumbFile = content.video.thumbnail?.file,
                        fileName = content.video.fileName
                    )
                    if (content.caption.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = content.caption.text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is TdApi.MessageAnimation -> {
                    Text("GIF", color = textColor)
                }
                is TdApi.MessageSticker -> {
                    StickerContentView(sticker = content.sticker)
                }
                is TdApi.MessageDocument -> {
                    if (content.document.fileName.endsWith(".mkv", ignoreCase = true) || content.document.mimeType.startsWith("video/")) {
                        VideoContentView(
                            videoFile = content.document.document,
                            thumbFile = content.document.thumbnail?.file,
                            fileName = content.document.fileName
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = content.document.fileName,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (content.caption.text.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = content.caption.text,
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text("📄 ${content.document.fileName}", color = textColor)
                    }
                }
                is TdApi.MessageVoiceNote -> {
                    Text("🎤 Voice", color = textColor)
                }
                is TdApi.MessageUnsupported -> {
                    Text("❗️ Unsupported Message", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Text("Unsupported message type", color = textColor)
                }
            }
            
            val reactions = message.interactionInfo?.reactions?.reactions
            if (!reactions.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactions.forEach { reaction ->
                        val emoji = when (val type = reaction.type) {
                            is TdApi.ReactionTypeEmoji -> type.emoji
                            is TdApi.ReactionTypeCustomEmoji -> "🔹"
                            else -> "🔹"
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (reaction.isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = reaction.totalCount.toString(),
                                    fontSize = 12.sp,
                                    color = if (reaction.isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Time
            val date = Date(message.date * 1000L)
            val today = Calendar.getInstance()
            val msgDate = Calendar.getInstance().apply { time = date }
            
            val format = if (today.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)) {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            } else {
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            }
            val timeStr = format.format(date)
            
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputArea(onSendMessage: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Message") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun MediaContentView(file: TdApi.File, isVideo: Boolean = false) {
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val currentFile = downloadedFiles[file.id] ?: file
    
    LaunchedEffect(currentFile.id) {
        if (!currentFile.local.isDownloadingCompleted && currentFile.local.canBeDownloaded && !currentFile.local.isDownloadingActive) {
            TelegramClient.downloadFile(currentFile.id)
        }
    }

    if (currentFile.local.isDownloadingCompleted) {
        AsyncImage(
            model = currentFile.local.path,
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

enum class VideoState {
    Preview,
    DownloadingForWatch,
    DownloadingForExport
}

@Composable
fun VideoContentView(videoFile: TdApi.File, thumbFile: TdApi.File?, fileName: String) {
    var state by remember { mutableStateOf<VideoState>(VideoState.Preview) }
    val context = LocalContext.current
    
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val localVideoFile = downloadedFiles[videoFile.id] ?: videoFile
    val localThumbFile = thumbFile?.id?.let { downloadedFiles[it] ?: thumbFile }

    // Download thumbnail if not local
    LaunchedEffect(thumbFile?.id) {
        if (thumbFile != null && !thumbFile.local.isDownloadingCompleted && thumbFile.local.canBeDownloaded && !thumbFile.local.isDownloadingActive) {
            TelegramClient.downloadFile(thumbFile.id)
        }
    }

    // Auto-export flow when downloading for export finishes
    LaunchedEffect(localVideoFile.local.isDownloadingCompleted, state) {
        if (state == VideoState.DownloadingForExport && localVideoFile.local.isDownloadingCompleted) {
            TelegramClient.exportDownloadedFile(localVideoFile, fileName) { success, msg ->
                val toastMsg = if (success) "Video saved to Downloads!" else "Export failed: $msg"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                state = VideoState.Preview
            }
        }
    }
    
    // Auto-watch flow when download for watch starts and path is set
    LaunchedEffect(localVideoFile.local.path, state) {
        if (state == VideoState.DownloadingForWatch && localVideoFile.local.path.isNotEmpty()) {
            state = VideoState.Preview
            context.startActivity(Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra("video_path", localVideoFile.local.path)
            })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 300.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (localThumbFile?.local?.isDownloadingCompleted == true) {
            AsyncImage(
                model = localThumbFile.local.path,
                contentDescription = "Video Thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f // Darken thumbnail slightly to make buttons visible
            )
        }
        
        if (state == VideoState.DownloadingForWatch || state == VideoState.DownloadingForExport) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Watch Button
                FilledIconButton(
                    onClick = {
                        if (localVideoFile.local.path.isNotEmpty()) {
                            if (!localVideoFile.local.isDownloadingCompleted && !localVideoFile.local.isDownloadingActive) {
                                TelegramClient.downloadFile(localVideoFile.id, priority = 32, synchronous = false)
                            }
                            context.startActivity(Intent(context, VideoPlayerActivity::class.java).apply {
                                putExtra("video_path", localVideoFile.local.path)
                            })
                        } else {
                            state = VideoState.DownloadingForWatch
                            TelegramClient.downloadFile(localVideoFile.id, priority = 32, synchronous = false)
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Watch", modifier = Modifier.size(32.dp))
                }
                
                // Download Button
                FilledTonalIconButton(
                    onClick = {
                        if (localVideoFile.local.isDownloadingCompleted) {
                            TelegramClient.exportDownloadedFile(localVideoFile, fileName) { success, msg ->
                                val toastMsg = if (success) "Video saved to Downloads!" else "Export failed: $msg"
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            state = VideoState.DownloadingForExport
                            TelegramClient.downloadFile(localVideoFile.id)
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Download", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun StickerContentView(sticker: TdApi.Sticker) {
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val stickerFile = downloadedFiles[sticker.sticker.id] ?: sticker.sticker
    
    LaunchedEffect(stickerFile.id) {
        if (!stickerFile.local.isDownloadingCompleted && stickerFile.local.canBeDownloaded && !stickerFile.local.isDownloadingActive) {
            TelegramClient.downloadFile(stickerFile.id)
        }
    }

    if (stickerFile.local.isDownloadingCompleted) {
        AsyncImage(
            model = stickerFile.local.path,
            contentDescription = "Sticker",
            modifier = Modifier
                .widthIn(max = 150.dp)
                .heightIn(max = 150.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(Color.Gray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
