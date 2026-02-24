package com.notioff.telegramsuper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class ChatViewModel(private val chatId: Long) : ViewModel() {

    private val _chatInfo = MutableStateFlow<TdApi.Chat?>(null)
    val chatInfo: StateFlow<TdApi.Chat?> = _chatInfo.asStateFlow()

    val messages: StateFlow<List<TdApi.Message>> = TelegramClient.messagesFlow
    
    private val _senderNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val senderNames: StateFlow<Map<String, String>> = _senderNames.asStateFlow()
    
    private val _topicNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val topicNames: StateFlow<Map<Long, String>> = _topicNames.asStateFlow()
    
    var isEndReached = false
        private set
        
    private var isLoading = false

    fun activate() {
        TelegramClient.openChat(chatId)
        loadChatInfo()
        loadInitialMessages()
    }

    init {
        viewModelScope.launch {
            messages.collect { msgs ->
                // Identify unknown senders and fetch them
                val currentNames = _senderNames.value
                val newNames = currentNames.toMutableMap()
                var hasUpdates = false
                
                val currentTopics = _topicNames.value
                val newTopics = currentTopics.toMutableMap()
                var hasTopicUpdates = false
                
                msgs.forEach { msg ->
                    if (msg.topicId is TdApi.MessageTopicForum) {
                        val forumTopicId = (msg.topicId as TdApi.MessageTopicForum).forumTopicId.toLong()
                        if (!newTopics.containsKey(forumTopicId)) {
                            newTopics[forumTopicId] = "..."
                            hasTopicUpdates = true
                            TelegramClient.getForumTopic(chatId, forumTopicId) { forumTopic ->
                                if (forumTopic != null) {
                                    updateTopicName(forumTopicId, forumTopic.info.name)
                                } else {
                                    updateTopicName(forumTopicId, "Unknown Topic")
                                }
                            }
                        }
                    }

                    if (msg.isOutgoing) return@forEach
                    val senderIdStr = getSenderIdString(msg.senderId) ?: return@forEach
                    if (!newNames.containsKey(senderIdStr)) {
                        newNames[senderIdStr] = "..." // Placeholder while loading
                        hasUpdates = true
                        
                        when (val sender = msg.senderId) {
                            is TdApi.MessageSenderUser -> {
                                TelegramClient.getUser(sender.userId) { user ->
                                    if (user != null) {
                                        val name = listOf(user.firstName, user.lastName)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" ")
                                        updateSenderName(senderIdStr, if (name.isNotBlank()) name else "User")
                                    } else {
                                        updateSenderName(senderIdStr, "Unknown User")
                                    }
                                }
                            }
                            is TdApi.MessageSenderChat -> {
                                TelegramClient.getChatInfo(sender.chatId) { chat ->
                                    if (chat != null) {
                                        updateSenderName(senderIdStr, chat.title)
                                    } else {
                                        updateSenderName(senderIdStr, "Unknown Chat")
                                    }
                                }
                            }
                        }
                    }
                }
                if (hasUpdates) {
                    _senderNames.value = newNames
                }
                if (hasTopicUpdates) {
                    _topicNames.value = newTopics
                }
            }
        }
    }

    private fun updateSenderName(idStr: String, name: String) {
        val currentNames = _senderNames.value.toMutableMap()
        currentNames[idStr] = name
        _senderNames.value = currentNames
    }
    
    private fun updateTopicName(topicId: Long, name: String) {
        val currentTopics = _topicNames.value.toMutableMap()
        currentTopics[topicId] = name
        _topicNames.value = currentTopics
    }
    
    fun getSenderIdString(senderId: TdApi.MessageSender?): String? {
        return when (senderId) {
            is TdApi.MessageSenderUser -> "user_${senderId.userId}"
            is TdApi.MessageSenderChat -> "chat_${senderId.chatId}"
            else -> null
        }
    }

    private fun loadChatInfo() {
        TelegramClient.getChatInfo(chatId) { chat ->
            _chatInfo.value = chat
        }
    }

    private fun loadInitialMessages() {
        TelegramClient.loadMessages(chatId, limit = 50)
    }

    fun loadMoreMessages() {
        if (isLoading || isEndReached) return
        
        val currentMessages = messages.value
        if (currentMessages.isNotEmpty()) {
            isLoading = true
            val lastMessageId = currentMessages.last().id
            TelegramClient.loadMessages(chatId, fromMessageId = lastMessageId, limit = 50) { loadedCount ->
                isLoading = false
                if (loadedCount == 0) {
                    isEndReached = true
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isNotBlank()) {
            TelegramClient.sendMessage(chatId, text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        TelegramClient.closeChat(chatId)
    }
}

class ChatViewModelFactory(private val chatId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
