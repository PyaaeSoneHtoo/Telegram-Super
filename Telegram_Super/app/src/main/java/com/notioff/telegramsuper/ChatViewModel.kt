package com.notioff.telegramsuper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class ChatViewModel(private val chatId: Long, private val threadId: Long = 0) : ViewModel() {

    private val _chatInfo = MutableStateFlow<TdApi.Chat?>(null)
    val chatInfo: StateFlow<TdApi.Chat?> = _chatInfo.asStateFlow()

    val messages: StateFlow<List<TdApi.Message>> = TelegramClient.messagesFlow
    
    private val _senderNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val senderNames: StateFlow<Map<String, String>> = _senderNames.asStateFlow()
    
    private val _topicNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val topicNames: StateFlow<Map<Long, String>> = _topicNames.asStateFlow()
    
    private val _pinnedMessage = MutableStateFlow<TdApi.Message?>(null)
    val pinnedMessage: StateFlow<TdApi.Message?> = _pinnedMessage.asStateFlow()
    
    private val _pinnedMessagesList = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val pinnedMessagesList: StateFlow<List<TdApi.Message>> = _pinnedMessagesList.asStateFlow()

    val userStatuses: StateFlow<Map<Long, TdApi.UserStatus>> = TelegramClient.userStatuses
    
    var isEndReached = false
    private var isLoading = false

    fun deleteMessages(messageIds: LongArray, revoke: Boolean) {
        TelegramClient.deleteMessages(chatId, messageIds, revoke)
    }

    fun loadPinnedMessages() {
        TelegramClient.getPinnedMessages(chatId) { msgs ->
            _pinnedMessagesList.value = msgs
        }
    }

    fun activate() {
        TelegramClient.openChat(chatId, threadId)
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
                    // Only fetch topic info if we are in the main chat (threadId == 0)
                    if (threadId == 0L && msg.topicId is TdApi.MessageTopicForum) {
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
        TelegramClient.getChatPinnedMessage(chatId) { message ->
            _pinnedMessage.value = message
        }
    }

    private fun loadInitialMessages() {
        TelegramClient.loadMessages(chatId, threadId, limit = 50)
    }

    /** Load messages anchored at [messageId] and return the index of that message in the result. */
    fun loadMessagesFromId(messageId: Long, onLoaded: (targetIndex: Int) -> Unit) {
        isEndReached = false
        // fromMessageId = messageId+1, offset=0 → returns messages <= messageId (inclusive trick)
        // Actually GetChatHistory returns messages with id < fromMessageId, so use messageId+1
        TelegramClient.loadMessagesFromId(chatId, threadId, messageId) { idx ->
            onLoaded(idx)
        }
    }

    fun loadMoreMessages() {
        if (isLoading || isEndReached) return
        
        val currentMessages = messages.value
        if (currentMessages.isNotEmpty()) {
            isLoading = true
            val lastMessageId = currentMessages.last().id
            TelegramClient.loadMessages(chatId, threadId, fromMessageId = lastMessageId, limit = 50) { loadedCount ->
                isLoading = false
                if (loadedCount == 0) {
                    isEndReached = true
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isNotBlank()) {
            TelegramClient.sendMessage(chatId, text, threadId)
        }
    }

    fun sendMedia(filePath: String, mimeType: String) {
        TelegramClient.sendMediaMessage(chatId, filePath, mimeType, threadId)
    }

    override fun onCleared() {
        super.onCleared()
        TelegramClient.closeChat(chatId)
    }
}

class ChatViewModelFactory(private val chatId: Long, private val threadId: Long = 0) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatId, threadId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
