package com.notioff.telegramsuper

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

class AuthViewModel : ViewModel() {
    val authState: StateFlow<TdApi.AuthorizationState> = TelegramClient.authState
    val lastError: StateFlow<String?> = TelegramClient.lastError
    
    fun setApiCredentials(id: String, hash: String) {
        try {
            TelegramClient.setApiCredentials(id.toInt(), hash)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun sendPhoneNumber(phone: String) {
        TelegramClient.sendPhoneNumber(phone)
    }

    fun sendAuthCode(code: String) {
        TelegramClient.sendAuthCode(code)
    }

    fun sendPassword(password: String) {
        TelegramClient.sendPassword(password)
    }
}
