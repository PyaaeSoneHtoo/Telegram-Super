package com.notioff.telegramsuper

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

class ChatListViewModel : ViewModel() {
    val chatList: StateFlow<List<TdApi.Chat>> = TelegramClient.chatList
    val folders: StateFlow<List<TdApi.ChatFolderInfo>> = TelegramClient.chatFolders
    val currentChatList: StateFlow<TdApi.ChatList> = TelegramClient.currentChatList
    val currentVirtualFolderId: StateFlow<Int> = TelegramClient.currentVirtualFolderId

    fun selectFolder(folderId: Int) {
        TelegramClient.setChatList(folderId)
    }

    fun pinChat(chatId: Long, isPinned: Boolean) {
        TelegramClient.toggleChatIsPinned(chatId, isPinned)
    }

    fun markAsUnread(chatId: Long, isUnread: Boolean) {
        TelegramClient.toggleChatIsMarkedAsUnread(chatId, isUnread)
    }

    fun clearHistory(chatId: Long, revoke: Boolean) {
        TelegramClient.deleteChatHistory(chatId, true, revoke)
    }

    fun deleteChat(chatId: Long) {
        TelegramClient.deleteChat(chatId)
    }

    fun blockChat(chat: TdApi.Chat) {
        when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> TelegramClient.blockUser(type.userId)
            is TdApi.ChatTypeBasicGroup -> TelegramClient.leaveChat(chat.id)
            is TdApi.ChatTypeSupergroup -> TelegramClient.leaveChat(chat.id)
            is TdApi.ChatTypeSecret -> TelegramClient.blockUser(type.userId)
        }
    }

    fun logOut() {
        TelegramClient.logOut()
    }
}
