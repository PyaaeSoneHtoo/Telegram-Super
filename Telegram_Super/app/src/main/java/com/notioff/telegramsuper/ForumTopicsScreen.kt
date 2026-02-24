package com.notioff.telegramsuper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.drinkless.tdlib.TdApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumTopicsScreen(
    chatId: Long,
    onTopicClick: (Long) -> Unit,
    onBack: () -> Unit,
    onShowRawChat: (Long) -> Unit
) {
    val viewModel: ForumTopicsViewModel = viewModel(
        key = "forum_$chatId",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ForumTopicsViewModel(chatId) as T
            }
        }
    )

    val topics by viewModel.topics.collectAsState()
    val chatTitle by viewModel.chatTitle.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatTitle.ifEmpty { "Topics" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onShowRawChat(chatId) }) {
                        Text("Raw Chat", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (topics.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(topics) { topic ->
                    TopicItem(topic = topic, onClick = { onTopicClick(topic.info.forumTopicId.toLong()) })
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
fun TopicItem(topic: TdApi.ForumTopic, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = topic.info.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            topic.lastMessage?.let { msg ->
                val text = when (val content = msg.content) {
                    is TdApi.MessageText -> content.text.text
                    else -> "Media"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}
