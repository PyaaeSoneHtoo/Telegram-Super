package com.notioff.telegramsuper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.material.icons.filled.Mic
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.compose.ui.text.style.TextOverflow
import com.airbnb.lottie.compose.*
import java.io.File

enum class VideoState {
    Preview,
    DownloadingForWatch,
    DownloadingForExport
}

@Composable
fun UnsupportedMessageView(reason: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "❗️",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column {
                Text(
                    text = "Unsupported Content",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun PinnedMessageBar(message: TdApi.Message, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "Pinned Message",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                val text = when (val content = message.content) {
                    is TdApi.MessageText -> content.text.text
                    is TdApi.MessagePhoto -> "📷 Photo"
                    is TdApi.MessageVideo -> "🎥 Video"
                    is TdApi.MessageSticker -> "Sticker"
                    is TdApi.MessageDocument -> "📄 ${content.document.fileName}"
                    else -> "Pinned Message"
                }
                
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VideoContentView(videoFile: TdApi.File, thumbFile: TdApi.File?, fileName: String, caption: String = "") {
    val effectiveFileName = remember(fileName, caption) { getEffectiveFileName(caption, fileName) }
    var state by remember { mutableStateOf<VideoState>(VideoState.Preview) }
    val context = LocalContext.current
    
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val localVideoFile = downloadedFiles[videoFile.id] ?: videoFile
    val localThumbFile = thumbFile?.id?.let { downloadedFiles[it] ?: thumbFile }

    LaunchedEffect(thumbFile?.id) {
        if (thumbFile != null && !thumbFile.local.isDownloadingCompleted && thumbFile.local.canBeDownloaded && !thumbFile.local.isDownloadingActive) {
            TelegramClient.downloadFile(thumbFile.id)
        }
    }

    LaunchedEffect(localVideoFile.local.isDownloadingCompleted, state) {
        if (state == VideoState.DownloadingForExport && localVideoFile.local.isDownloadingCompleted) {
            TelegramClient.exportDownloadedFile(localVideoFile, effectiveFileName) { success, msg ->
                val toastMsg = if (success) "Video saved to Downloads!" else "Export failed: $msg"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                state = VideoState.Preview
            }
        }
    }
    
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
                alpha = 0.5f
            )
        }
        
        if (state == VideoState.DownloadingForWatch || state == VideoState.DownloadingForExport) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        if (localVideoFile.local.path.isNotEmpty()) {
                            if (!localVideoFile.local.isDownloadingCompleted && !localVideoFile.local.isDownloadingActive) {
                                TelegramClient.downloadFile(localVideoFile.id, priority = 32, synchronous = false, isUserRequested = true)
                            }
                            context.startActivity(Intent(context, VideoPlayerActivity::class.java).apply {
                                putExtra("video_path", localVideoFile.local.path)
                            })
                        } else {
                            state = VideoState.DownloadingForWatch
                            TelegramClient.downloadFile(localVideoFile.id, priority = 32, synchronous = false, isUserRequested = true)
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Watch", modifier = Modifier.size(32.dp))
                }
                
                FilledTonalIconButton(
                    onClick = {
                        if (localVideoFile.local.isDownloadingCompleted) {
                            TelegramClient.exportDownloadedFile(localVideoFile, effectiveFileName) { success, msg ->
                                val toastMsg = if (success) "Video saved to Downloads!" else "Export failed: $msg"
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            state = VideoState.DownloadingForExport
                            TelegramClient.downloadFile(localVideoFile.id, isUserRequested = true)
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

    Box(
        modifier = Modifier
            .widthIn(max = 150.dp)
            .heightIn(max = 150.dp),
        contentAlignment = Alignment.Center
    ) {
        if (stickerFile.local.isDownloadingCompleted) {
            when (sticker.format) {
                is TdApi.StickerFormatWebp -> {
                    AsyncImage(
                        model = stickerFile.local.path,
                        contentDescription = "Sticker",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                is TdApi.StickerFormatTgs -> {
                    val jsonString = remember(stickerFile.local.path) {
                        TgsDecompressor.decompressTgs(File(stickerFile.local.path))
                    }
                    if (jsonString != null) {
                        val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(jsonString))
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Fallback to thumbnail or static image if decompression fails
                        AsyncImage(
                            model = sticker.thumbnail?.file?.local?.path,
                            contentDescription = "Sticker Placeholder",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                is TdApi.StickerFormatWebm -> {
                    // Video sticker (WebM)
                    VideoContentView(
                        videoFile = stickerFile,
                        thumbFile = sticker.thumbnail?.file,
                        fileName = "sticker.webm"
                    )
                }
                else -> {
                    AsyncImage(
                        model = stickerFile.local.path,
                        contentDescription = "Sticker",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.Gray.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun MediaContentView(file: TdApi.File, isVideo: Boolean = false) {
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val currentFile = downloadedFiles[file.id] ?: file
    var showFullscreen by remember { mutableStateOf(false) }
    
    LaunchedEffect(currentFile.id) {
        if (!currentFile.local.isDownloadingCompleted && currentFile.local.canBeDownloaded && !currentFile.local.isDownloadingActive) {
            TelegramClient.downloadFile(currentFile.id)
        }
    }

    if (currentFile.local.isDownloadingCompleted) {
        AsyncImage(
            model = currentFile.local.path,
            contentDescription = if (isVideo) "Video Preview" else "Photo",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (!isVideo) Modifier.clickable { showFullscreen = true } else Modifier),
            contentScale = ContentScale.Crop
        )
        if (showFullscreen && !isVideo) {
            FullscreenImageViewer(
                path = currentFile.local.path,
                onDismiss = { showFullscreen = false }
            )
        }
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

@Composable
fun FullscreenImageViewer(path: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offsetX += pan.x * scale
                        offsetY += pan.y * scale
                    }
                }
                .clickable { if (scale == 1f) onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = path,
                contentDescription = "Fullscreen Photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Long,
    threadId: Long = 0,
    onBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit = {},
    onShowTopics: (Long) -> Unit = {},
    viewModel: ChatViewModel = viewModel(
        key = "chat_${chatId}_$threadId", 
        factory = ChatViewModelFactory(chatId, threadId)
    )
) {
    LaunchedEffect(chatId, threadId) {
        viewModel.activate()
    }

    val chatInfo by viewModel.chatInfo.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val senderNames by viewModel.senderNames.collectAsState()
    val userStatuses by viewModel.userStatuses.collectAsState()
    
    // Get the peer user ID if this is a private chat
    val peerUserId = (chatInfo?.type as? TdApi.ChatTypePrivate)?.userId
    val peerStatus = peerUserId?.let { userStatuses[it] }
    val isOnline = peerStatus is TdApi.UserStatusOnline
    val statusText = peerStatus?.let { formatUserStatus(it) }
    
    val isGroupChat = chatInfo?.type is TdApi.ChatTypeBasicGroup || chatInfo?.type is TdApi.ChatTypeSupergroup
    
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chatInfo?.title ?: "Chat", 
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isOnline) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                            }
                        }
                        val subtitle = when {
                            threadId != 0L -> viewModel.topicNames.collectAsState().value[threadId] ?: "Topic"
                            statusText != null -> statusText
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline && statusText == subtitle) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val chatInfo by viewModel.chatInfo.collectAsState()
                    if (chatInfo?.viewAsTopics == true) {
                        TextButton(onClick = { onShowTopics(chatId) }) {
                            Text("Topics", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
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
            val chatInfo by viewModel.chatInfo.collectAsState()
            ChatInputArea(
                onSendMessage = { text -> viewModel.sendMessage(text) },
                onSendMedia = { filePath, mimeType -> viewModel.sendMedia(filePath, mimeType) },
                canSendMessages = chatInfo?.permissions?.canSendBasicMessages ?: true
            )
        }
    ) { paddingValues ->
        val pinnedMessage by viewModel.pinnedMessage.collectAsState()
        
        var showPinnedList by remember { mutableStateOf(false) }
        var messageToDelete by remember { mutableStateOf<TdApi.Message?>(null) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (pinnedMessage != null) {
                PinnedMessageBar(message = pinnedMessage!!, onClick = {
                    viewModel.loadPinnedMessages()
                    showPinnedList = true
                })
            }
            
            Box(modifier = Modifier.weight(1f)) {
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
                            
                            val topicName = if (threadId == 0L && message.topicId is TdApi.MessageTopicForum) {
                                val forumId = (message.topicId as TdApi.MessageTopicForum).forumTopicId.toLong()
                                viewModel.topicNames.value[forumId]
                            } else null

                            MessageBubble(
                                message = message, 
                                senderName = senderName, 
                                topicName = topicName,
                                onNavigateToChat = onNavigateToChat,
                                onLongClick = {
                                    messageToDelete = message
                                    showDeleteDialog = true
                                }
                            )
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

        if (showPinnedList) {
            PinnedMessagesBottomSheet(
                messages = viewModel.pinnedMessagesList.collectAsState().value,
                onDismiss = { showPinnedList = false },
                onMessageClick = { messageId ->
                    showPinnedList = false
                    val currentList = messages
                    val existingIdx = currentList.indexOfFirst { it.id == messageId }
                    if (existingIdx >= 0) {
                        // Already loaded — just scroll to it
                        coroutineScope.launch {
                            listState.animateScrollToItem(existingIdx)
                        }
                    } else {
                        // Not in memory — load history from that message, then scroll
                        viewModel.loadMessagesFromId(messageId) { targetIndex ->
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    }
                }
            )
        }

        if (showDeleteDialog && messageToDelete != null) {
            var revoke by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Message") },
                text = {
                    Column {
                        Text("Are you sure you want to delete this message?")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = revoke, onCheckedChange = { revoke = it })
                            Text("Delete for everyone")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteMessages(longArrayOf(messageToDelete!!.id), revoke)
                        showDeleteDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: TdApi.Message, 
    senderName: String? = null, 
    topicName: String? = null,
    onNavigateToChat: (Long) -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isOutgoing = message.isOutgoing
    val backgroundColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
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
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Handle click */ },
                onLongClick = onLongClick
            ),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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
            
            when (val content = message.content) {
                is TdApi.MessageText -> {
                    TelegramText(
                        text = content.text,
                        color = textColor,
                        onNavigateToChat = onNavigateToChat
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
                        TelegramText(
                            text = content.caption,
                            color = textColor,
                            onNavigateToChat = onNavigateToChat
                        )
                    }
                }
                is TdApi.MessageVideo -> {
                    VideoContentView(
                        videoFile = content.video.video,
                        thumbFile = content.video.thumbnail?.file,
                        fileName = content.video.fileName,
                        caption = content.caption.text
                    )
                    if (content.caption.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TelegramText(
                            text = content.caption,
                            color = textColor,
                            onNavigateToChat = onNavigateToChat
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
                    DocumentContentView(
                        document = content.document,
                        caption = content.caption.text,
                        textColor = textColor
                    )
                }
                is TdApi.MessageVoiceNote -> {
                    VoiceNotePlayer(
                        voiceNote = content.voiceNote,
                        textColor = textColor
                    )
                }
                is TdApi.MessagePinMessage -> {
                    Text("📌 Pinned a message", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                is TdApi.MessageChatChangeTitle -> {
                    Text("✏️ Title changed to: ${content.title}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatChangePhoto -> {
                    Text("🖼 Chat photo changed", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatDeletePhoto -> {
                    Text("🖼 Chat photo removed", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatAddMembers -> {
                    Text("👋 Added ${content.memberUserIds.size} members", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatJoinByLink -> {
                    Text("🔗 Joined by invite link", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatDeleteMember -> {
                    Text("🚪 Member left", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageScreenshotTaken -> {
                    Text("📸 Screenshot taken", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatSetBackground -> {
                    Text("🖼 Chat background changed", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatSetTheme -> {
                    val themeInfo = when (val t = content.theme) {
                        is TdApi.ChatThemeEmoji -> ": ${t.name}"
                        null -> ": default"
                        else -> ""
                    }
                    Text("🎨 Chat theme changed$themeInfo", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageAudio -> {
                    AudioMessagePlayer(
                        audio = content.audio,
                        textColor = textColor
                    )
                }
                is TdApi.MessageVideoNote -> {
                    Text("📹 Video Note", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageAnimatedEmoji -> {
                    Text(content.emoji, fontSize = 40.sp)
                }
                is TdApi.MessageGame -> {
                    Text("🎮 Game: ${content.game.title}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageStory -> {
                    Text("🤳 Story", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageInvoice -> {
                    Text("💳 Invoice: ${content.productInfo.title}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessagePaidMedia -> {
                    Text("💰 Paid Media", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageLocation -> {
                    Text("📍 Location", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageVenue -> {
                    Text("📍 Venue: ${content.venue.title}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessagePoll -> {
                    Text("📊 Poll: ${content.poll.question}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageDice -> {
                    Text("${content.emoji} Dice: ${content.value}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageContact -> {
                    Text("👤 Contact: ${content.contact.firstName} ${content.contact.lastName}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageForumTopicCreated -> {
                    Text("🆕 Forum topic created: ${content.name}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageForumTopicEdited -> {
                    Text("✏️ Forum topic renamed to: ${content.name}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageForumTopicIsClosedToggled -> {
                    val status = if (content.isClosed) "closed" else "reopened"
                    Text("🔒 Forum topic $status", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageForumTopicIsHiddenToggled -> {
                    val status = if (content.isHidden) "hidden" else "shown"
                    Text("👁 Forum topic $status", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageCall -> {
                    val durationStr = if (content.duration > 0) " (${content.duration}s)" else ""
                    Text("📞 Call$durationStr", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageBasicGroupChatCreate -> {
                    Text("🏢 Group created", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageSupergroupChatCreate -> {
                    Text("🚀 Supergroup created", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageExpiredPhoto, is TdApi.MessageExpiredVideo, is TdApi.MessageExpiredVideoNote, is TdApi.MessageExpiredVoiceNote -> {
                    Text("⌛️ Media expired", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatUpgradeTo -> {
                    Text("🚀 Group upgraded to Supergroup", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatUpgradeFrom -> {
                    Text("🚀 Group created from Basic Group", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatSetMessageAutoDeleteTime -> {
                    val timeStr = if (content.messageAutoDeleteTime > 0) "${content.messageAutoDeleteTime}s" else "disabled"
                    Text("⏳ Auto-delete set to: $timeStr", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageChatBoost -> {
                    val count = if (content.boostCount > 1) " (${content.boostCount} boosts)" else ""
                    Text("⚡️ Chat Boosted$count", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageGiveaway -> {
                    Text("🎁 Giveaway started", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageGiveawayCompleted -> {
                    Text("🎁 Giveaway completed", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageVideoChatStarted -> {
                    Text("📹 Video Chat started", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageVideoChatEnded -> {
                    Text("📹 Video Chat ended", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageVideoChatScheduled -> {
                    Text("📹 Video Chat scheduled", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageInviteVideoChatParticipants -> {
                    Text("📹 Invited to Video Chat", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is TdApi.MessageUnsupported -> {
                    UnsupportedMessageView("Message type not supported by TDLib")
                }
                else -> {
                    val typeName = content?.javaClass?.simpleName?.removePrefix("Message") ?: "Unknown"
                    UnsupportedMessageView("Type '$typeName' not implemented yet")
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
fun ChatInputArea(
    onSendMessage: (String) -> Unit,
    onSendMedia: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    canSendMessages: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // File pickers
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/*"
            val path = getRealPathFromUri(context, it)
            if (path != null) onSendMedia(path, mime)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "video/*"
            val path = getRealPathFromUri(context, it)
            if (path != null) onSendMedia(path, mime)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "audio/*"
            val path = getRealPathFromUri(context, it)
            if (path != null) onSendMedia(path, mime)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        if (!canSendMessages) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "You cannot send messages in this chat",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment button
                    Box {
                        IconButton(onClick = { showAttachMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📷 Photo") },
                                onClick = { showAttachMenu = false; imagePicker.launch("image/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("🎥 Video") },
                                onClick = { showAttachMenu = false; videoPicker.launch("video/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("🎵 Audio") },
                                onClick = { showAttachMenu = false; audioPicker.launch("audio/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 File") },
                                onClick = { showAttachMenu = false; imagePicker.launch("*/*") }
                            )
                        }
                    }

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

                    if (text.isBlank()) {
                        // Voice note button — hold to record, release to send
                        VoiceNoteButton(
                            onSendVoice = { path -> onSendMedia(path, "audio/voice") }
                        )
                    } else {
                        IconButton(
                            onClick = {
                                onSendMessage(text)
                                text = ""
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
        }
    }
}

@Composable
fun VoiceNoteButton(
    onSendVoice: (filePath: String) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    val recorderRef = remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    val outputFileRef = remember { mutableStateOf<java.io.File?>(null) }

    val micColor by animateColorAsState(
        targetValue = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
        label = "mic_color"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(micColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Start recording on press
                        val file = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                        outputFileRef.value = file
                        val started = try {
                            val mr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                android.media.MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION") android.media.MediaRecorder()
                            }
                            mr.apply {
                                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                                setAudioSamplingRate(16000)
                                setAudioEncodingBitRate(32000)
                                setOutputFile(file.absolutePath)
                                prepare()
                                start()
                            }
                            recorderRef.value = mr
                            true
                        } catch (e: Exception) {
                            Log.e("VoiceNote", "Recording failed: ${e.message}")
                            false
                        }
                        isRecording = started

                        // Wait for release
                        val released = tryAwaitRelease()

                        // Stop and send
                        isRecording = false
                        try {
                            recorderRef.value?.stop()
                            recorderRef.value?.release()
                        } catch (e: Exception) {
                            Log.e("VoiceNote", "Stop failed: ${e.message}")
                        }
                        recorderRef.value = null
                        if (started) {
                            outputFileRef.value?.let { f ->
                                if (f.exists() && f.length() > 0) {
                                    onSendVoice(f.absolutePath)
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Mic,
            contentDescription = if (isRecording) "Recording... release to send" else "Hold to record voice message",
            tint = Color.White
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recorderRef.value?.let { try { it.stop(); it.release() } catch (e: Exception) { } }
        }
    }
}

@Composable
fun VoiceNotePlayer(voiceNote: TdApi.VoiceNote, textColor: androidx.compose.ui.graphics.Color) {
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val file = downloadedFiles[voiceNote.voice.id] ?: voiceNote.voice
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val playerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val durationSec = voiceNote.duration

    LaunchedEffect(file.id) {
        if (!file.local.isDownloadingCompleted && file.local.canBeDownloaded && !file.local.isDownloadingActive) {
            TelegramClient.downloadFile(file.id)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    playerRef.value?.stop()
                    playerRef.value?.release()
                    playerRef.value = null
                    isPlaying = false
                } else if (file.local.isDownloadingCompleted) {
                    try {
                        val mp = android.media.MediaPlayer().apply {
                            setDataSource(file.local.path)
                            prepare()
                            start()
                            setOnCompletionListener {
                                isPlaying = false
                                it.release()
                                playerRef.value = null
                            }
                        }
                        playerRef.value = mp
                        isPlaying = true
                    } catch (e: Exception) {
                        Log.e("VoicePlayer", "Playback failed: ${e.message}")
                    }
                }
            },
            modifier = Modifier
                .size(40.dp)
                .background(textColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play voice message",
                tint = textColor
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "🎤 Voice note",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (durationSec > 0) {
                Text(
                    text = "%d:%02d".format(durationSec / 60, durationSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            if (!file.local.isDownloadingCompleted) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(2.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerRef.value?.let { try { it.stop(); it.release() } catch (e: Exception) { } }
        }
    }
}

@Composable
fun DocumentContentView(document: TdApi.Document, caption: String, textColor: Color) {
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val file = downloadedFiles[document.document.id] ?: document.document
    var isExporting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val effectiveFileName = remember { getEffectiveFileName(caption, document.fileName) }

    LaunchedEffect(isExporting, file.local.isDownloadingCompleted) {
        if (isExporting && file.local.isDownloadingCompleted) {
            TelegramClient.exportDownloadedFile(file, effectiveFileName) { success, msg ->
                val toastMsg = if (success) "File saved to Downloads!" else "Export failed: $msg"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                isExporting = false
            }
        }
    }

    if (document.fileName.endsWith(".mkv", ignoreCase = true) || document.mimeType.startsWith("video/")) {
        VideoContentView(
            videoFile = document.document,
            thumbFile = document.thumbnail?.file,
            fileName = document.fileName,
            caption = caption
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    Surface(
        color = textColor.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add, // Using Add as a generic doc icon fallback
                contentDescription = null,
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = android.text.format.Formatter.formatShortFileSize(context, document.document.expectedSize.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
            
            if (isExporting || file.local.isDownloadingActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = {
                    if (file.local.isDownloadingCompleted) {
                        isExporting = true
                    } else {
                        isExporting = true
                        TelegramClient.downloadFile(file.id, isUserRequested = true)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    if (caption.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = caption,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun AudioMessagePlayer(audio: TdApi.Audio, textColor: androidx.compose.ui.graphics.Color, caption: String = "") {
    val downloadedFiles by TelegramClient.downloadedFiles.collectAsState()
    val file = downloadedFiles[audio.audio.id] ?: audio.audio
    var isPlaying by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val playerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val context = LocalContext.current
    val effectiveFileName = remember { getEffectiveFileName(caption, audio.fileName) }

    LaunchedEffect(isExporting, file.local.isDownloadingCompleted) {
        if (isExporting && file.local.isDownloadingCompleted) {
            TelegramClient.exportDownloadedFile(file, effectiveFileName) { success, msg ->
                val toastMsg = if (success) "Audio saved to Downloads!" else "Export failed: $msg"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                isExporting = false
            }
        }
    }

    LaunchedEffect(file.id) {
        if (!file.local.isDownloadingCompleted && file.local.canBeDownloaded && !file.local.isDownloadingActive) {
            TelegramClient.downloadFile(file.id)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    playerRef.value?.stop()
                    playerRef.value?.release()
                    playerRef.value = null
                    isPlaying = false
                } else if (file.local.isDownloadingCompleted) {
                    try {
                        val mp = android.media.MediaPlayer().apply {
                            setDataSource(file.local.path)
                            prepare()
                            start()
                            setOnCompletionListener {
                                isPlaying = false
                                it.release()
                                playerRef.value = null
                            }
                        }
                        playerRef.value = mp
                        isPlaying = true
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Playback failed: ${e.message}")
                    }
                }
            },
            modifier = Modifier
                .size(40.dp)
                .background(textColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = textColor
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val title = audio.title.ifEmpty { audio.fileName.ifEmpty { "Audio" } }
            val performer = audio.performer.ifEmpty { "" }
            Text(
                text = "🎵 $title",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (performer.isNotEmpty()) {
                Text(
                    text = performer,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val dur = audio.duration
            if (dur > 0) {
                Text(
                    text = "%d:%02d".format(dur / 60, dur % 60),
                )
            }
        }
        
        // Download button for audio
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).padding(start = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            IconButton(onClick = {
                if (file.local.isDownloadingCompleted) {
                    isExporting = true
                } else {
                    isExporting = true
                    TelegramClient.downloadFile(file.id, isUserRequested = true)
                }
            }) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerRef.value?.let { try { it.stop(); it.release() } catch (e: Exception) { } }
        }
    }
}


/** Get real file path from content URI, copying to cache if needed */
fun getRealPathFromUri(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val ext = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType) ?: "bin"
        val file = java.io.File(context.cacheDir, "upload_${System.currentTimeMillis()}.$ext")
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        Log.e("MediaPicker", "Failed to get path", e)
        null
    }
}

fun getEffectiveFileName(caption: String, originalFileName: String): String {
    if (caption.isBlank()) return originalFileName
    val extension = originalFileName.substringAfterLast('.', "")
    // Remove invalid characters and truncate to 75
    val baseName = caption.take(75).replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").trim()
    return if (extension.isNotEmpty()) "$baseName.$extension" else baseName
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedMessagesBottomSheet(
    messages: List<TdApi.Message>,
    onDismiss: () -> Unit,
    onMessageClick: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Pinned Messages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(messages) { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMessageClick(message.id) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                val text = when (val content = message.content) {
                                    is TdApi.MessageText -> content.text.text
                                    is TdApi.MessagePhoto -> "Photo"
                                    is TdApi.MessageVideo -> "Video"
                                    is TdApi.MessageSticker -> "Sticker"
                                    else -> "Pinned Message"
                                }
                                Text(
                                    text = text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                val date = Date(message.date * 1000L)
                                val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                Text(
                                    text = sdf.format(date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
@Composable
fun TelegramText(
    text: TdApi.FormattedText,
    color: Color,
    onNavigateToChat: (Long) -> Unit
) {
    val annotatedString = remember(text) {
        formatTelegramText(text)
    }
    
    val context = LocalContext.current
    
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(color = color),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val url = annotation.item
                    // Extract username from t.me links and navigate inside the app
                    val username: String? = when {
                        url.startsWith("https://t.me/") -> {
                            // https://t.me/username or https://t.me/username/123
                            url.removePrefix("https://t.me/").split("/").firstOrNull()
                                ?.takeIf { it.isNotBlank() && !it.startsWith("+") }
                        }
                        url.startsWith("tg://resolve") -> {
                            // tg://resolve?domain=username
                            android.net.Uri.parse(url).getQueryParameter("domain")
                        }
                        url.startsWith("@") -> url.removePrefix("@")
                        else -> null
                    }

                    if (username != null) {
                        TelegramClient.resolveBotAndOpenChat(username) { chatId ->
                            if (chatId != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onNavigateToChat(chatId)
                                }
                            } else {
                                // Fallback: open in browser
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("TelegramText", "Fallback open failed: $url")
                                    }
                                }
                            }
                        }
                    } else {
                        // Open external link directly (Facebook, etc.)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("TelegramText", "Failed to open URL: $url")
                        }
                    }
                }
        }
    )
}

fun formatTelegramText(formattedText: TdApi.FormattedText): AnnotatedString {
    val text = formattedText.text
    return buildAnnotatedString {
        append(text)
        formattedText.entities.forEach { entity ->
            val start = entity.offset
            val end = start + entity.length
            if (start < 0 || end > text.length) return@forEach
            
            when (val type = entity.type) {
                is TdApi.TextEntityTypeBold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                is TdApi.TextEntityTypeItalic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                is TdApi.TextEntityTypeUrl -> {
                    addStyle(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline), start, end)
                    addStringAnnotation(tag = "URL", annotation = text.substring(start, end), start, end)
                }
                is TdApi.TextEntityTypeTextUrl -> {
                    addStyle(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline), start, end)
                    addStringAnnotation(tag = "URL", annotation = type.url, start, end)
                }
                is TdApi.TextEntityTypeMention -> {
                    addStyle(SpanStyle(color = Color.Blue), start, end)
                    addStringAnnotation(tag = "URL", annotation = "https://t.me/${text.substring(start + 1, end)}", start, end)
                }
            }
        }
    }
}

fun formatUserStatus(status: TdApi.UserStatus): String {
    return when (status) {
        is TdApi.UserStatusOnline -> "online"
        is TdApi.UserStatusOffline -> {
            val wasOnline = status.wasOnline
            val now = System.currentTimeMillis() / 1000
            val diff = now - wasOnline
            when {
                diff < 60 -> "last seen just now"
                diff < 3600 -> "last seen ${diff / 60}m ago"
                diff < 86400 -> "last seen ${diff / 3600}h ago"
                diff < 172800 -> "last seen yesterday"
                else -> {
                    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                    "last seen ${sdf.format(java.util.Date(wasOnline * 1000L))}"
                }
            }
        }
        is TdApi.UserStatusRecently -> "last seen recently"
        is TdApi.UserStatusLastWeek -> "last seen within a week"
        is TdApi.UserStatusLastMonth -> "last seen within a month"
        else -> ""
    }
}
