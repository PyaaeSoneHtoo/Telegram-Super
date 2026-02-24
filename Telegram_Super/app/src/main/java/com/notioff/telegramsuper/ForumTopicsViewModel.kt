package com.notioff.telegramsuper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class ForumTopicsViewModel(private val chatId: Long) : ViewModel() {
    private val _topics = MutableStateFlow<List<TdApi.ForumTopic>>(emptyList())
    val topics: StateFlow<List<TdApi.ForumTopic>> = _topics

    private val _chatTitle = MutableStateFlow("")
    val chatTitle: StateFlow<String> = _chatTitle

    init {
        loadChatTitle()
        loadTopics()
    }

    private fun loadChatTitle() {
        TelegramClient.getChatInfo(chatId) { chat ->
            if (chat != null) {
                _chatTitle.value = chat.title
            }
        }
    }

    private fun loadTopics() {
        viewModelScope.launch {
            TelegramClient.getForumTopics(chatId) { result ->
                if (result != null) {
                    _topics.value = result.topics.toList()
                }
            }
        }
    }
}
