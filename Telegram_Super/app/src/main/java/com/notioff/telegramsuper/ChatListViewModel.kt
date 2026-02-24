package com.notioff.telegramsuper

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

class ChatListViewModel : ViewModel() {
    val chatList: StateFlow<List<TdApi.Chat>> = TelegramClient.chatList

    fun logOut() {
        TelegramClient.logOut()
    }
}
