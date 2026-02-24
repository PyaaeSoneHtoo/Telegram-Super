package com.notioff.telegramsuper

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import android.content.Context
import kotlinx.coroutines.flow.asStateFlow

object TelegramClient {
    private var client: Client? = null
    private var appContext: Context? = null
    
    private var apiId: Int = 0
    private var apiHash: String = ""
    
    private val _authState = MutableStateFlow<TdApi.AuthorizationState>(TdApi.AuthorizationStateWaitTdlibParameters())
    val authState: StateFlow<TdApi.AuthorizationState> = _authState.asStateFlow()

    private val chatMap = java.util.concurrent.ConcurrentHashMap<Long, TdApi.Chat>()
    private val _chatList = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chatList: StateFlow<List<TdApi.Chat>> = _chatList.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _messagesFlow = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val messagesFlow: StateFlow<List<TdApi.Message>> = _messagesFlow.asStateFlow()
    
    private val _ageVerificationBotUsername = MutableStateFlow<String?>(null)
    val ageVerificationBotUsername: StateFlow<String?> = _ageVerificationBotUsername.asStateFlow()
    
    private val _downloadedFiles = MutableStateFlow<Map<Int, TdApi.File>>(emptyMap())
    val downloadedFiles: StateFlow<Map<Int, TdApi.File>> = _downloadedFiles.asStateFlow()
    
    private var currentChatId: Long? = null
    
    private var notificationManager: DownloadNotificationManager? = null

    private fun updateChatList() {
        val sorted = chatMap.values.filter { chat ->
            chat.positions.any { it.list is TdApi.ChatListMain }
        }.sortedByDescending { chat ->
            chat.positions.first { it.list is TdApi.ChatListMain }.order
        }
        _chatList.value = sorted
    }

    fun initialize(context: Context) {
        if (client != null) return
        
        appContext = context.applicationContext
        notificationManager = DownloadNotificationManager(context)
        val databasePath = context.filesDir.absolutePath + "/tdlib/"
        
        val prefs = context.getSharedPreferences("tdlib_prefs", Context.MODE_PRIVATE)
        apiId = prefs.getInt("api_id", 0)
        apiHash = prefs.getString("api_hash", "") ?: ""
        
        client = Client.create(
            { tdApiObject ->
                when (tdApiObject) {
                    is TdApi.UpdateAuthorizationState -> {
                        _authState.value = tdApiObject.authorizationState
                        handleAuthState(tdApiObject.authorizationState, databasePath)
                        if (tdApiObject.authorizationState is TdApi.AuthorizationStateReady) {
                            client?.send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) { }
                        }
                    }
                    is TdApi.UpdateNewChat -> {
                        chatMap[tdApiObject.chat.id] = tdApiObject.chat
                        updateChatList()
                    }
                    is TdApi.UpdateNewMessage -> {
                        if (tdApiObject.message.chatId == currentChatId) {
                            val currentList = _messagesFlow.value.toMutableList()
                            currentList.add(0, tdApiObject.message) // Add to top as we display reversed
                            _messagesFlow.value = currentList.distinctBy { it.id }.sortedByDescending { it.date }
                        }
                    }
                    is TdApi.UpdateAgeVerificationParameters -> {
                        val p = tdApiObject.parameters
                        if (p != null && p.verificationBotUsername.isNotBlank()) {
                            Log.w("TDLibPrivacy", "Age verification required. Bot: ${p.verificationBotUsername}, Min Age: ${p.minAge}, Country: ${p.country}")
                            _ageVerificationBotUsername.value = p.verificationBotUsername
                        } else {
                            Log.i("TDLibPrivacy", "Age verification not required or cleared.")
                            _ageVerificationBotUsername.value = null
                        }
                    }
                    is TdApi.UpdateChatPosition -> {
                        val chat = chatMap[tdApiObject.chatId]
                        if (chat != null) {
                            val newPositions = chat.positions.filter { it.list.constructor != tdApiObject.position.list.constructor }.toMutableList()
                            if (tdApiObject.position.order != 0L) {
                                newPositions.add(tdApiObject.position)
                            }
                            chat.positions = newPositions.toTypedArray()
                            updateChatList()
                        }
                    }
                    is TdApi.UpdateChatLastMessage -> {
                        val chat = chatMap[tdApiObject.chatId]
                        if (chat != null) {
                            chat.lastMessage = tdApiObject.lastMessage
                            chat.positions = tdApiObject.positions
                            updateChatList()
                        }
                    }
                    is TdApi.UpdateChatTitle -> {
                        val chat = chatMap[tdApiObject.chatId]
                        if (chat != null) {
                            chat.title = tdApiObject.title
                            updateChatList()
                        }
                    }
                    is TdApi.UpdateChatPhoto -> { // This is a new update handler
                        chatMap[tdApiObject.chatId]?.photo = tdApiObject.photo
                        updateChatList()
                    }
                    is TdApi.UpdateFile -> {
                        val currentFiles = _downloadedFiles.value.toMutableMap()
                        currentFiles[tdApiObject.file.id] = tdApiObject.file
                        _downloadedFiles.value = currentFiles
                        
                        // Handle download notifications
                        val fileLocal = tdApiObject.file.local
                        if (fileLocal.isDownloadingActive) {
                            val progress = if (tdApiObject.file.size > 0) {
                                ((fileLocal.downloadedSize.toFloat() / tdApiObject.file.size.toFloat()) * 100).toInt()
                            } else {
                                0
                            }
                            notificationManager?.showDownloadProgress(tdApiObject.file.id, progress, "File ${tdApiObject.file.id}")
                        } else if (fileLocal.isDownloadingCompleted && fileLocal.downloadedSize > 0) {
                            notificationManager?.downloadComplete(tdApiObject.file.id, "File ${tdApiObject.file.id}")
                        }
                    }
                    else -> {
                        // Log.d("TDLib", "Received update: ${tdApiObject.javaClass.simpleName}")
                    }
                }
            },
            { error -> 
                Log.e("TDLib", "Update Exception: ${error.message}", error)
            },
            { Log.e("TDLib", "Update handler closed") }
        )
    }

    fun setApiCredentials(id: Int, hash: String) {
        this.apiId = id
        this.apiHash = hash
        
        appContext?.getSharedPreferences("tdlib_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt("api_id", id)
            ?.putString("api_hash", hash)
            ?.apply()
            
        if (client == null) {
            appContext?.let { initialize(it) }
            return
        }
        
        // Now that we have credentials, trigger the parameter sending if waiting
        if (_authState.value is TdApi.AuthorizationStateWaitTdlibParameters && client != null) {
             val parameters = TdApi.SetTdlibParameters()
             parameters.apiId = apiId
             parameters.apiHash = apiHash
             parameters.databaseDirectory = "/data/user/0/com.notioff.telegramsuper/files/tdlib/"
             parameters.useMessageDatabase = true
             parameters.useSecretChats = true
             parameters.systemLanguageCode = "en"
             parameters.deviceModel = Build.MODEL
             parameters.systemVersion = Build.VERSION.RELEASE
             parameters.applicationVersion = "1.0"
     
             client?.send(parameters) { result -> 
                 Log.d("TDLib", "Set parameters result: $result")
             }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState, databasePath: String) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                if (apiId != 0 && apiHash.isNotEmpty()) {
                    val parameters = TdApi.SetTdlibParameters()
                    parameters.apiId = apiId
                    parameters.apiHash = apiHash
                    parameters.databaseDirectory = databasePath
                    parameters.useMessageDatabase = true
                    parameters.useSecretChats = true
                    parameters.systemLanguageCode = "en"
                    parameters.deviceModel = Build.MODEL
                    parameters.systemVersion = Build.VERSION.RELEASE
                    parameters.applicationVersion = "1.0"
    
                    client?.send(parameters) { result -> 
                        Log.d("TDLib", "Set parameters result: $result")
                    }
                }
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                Log.d("TDLib", "Logging out...")
            }
            is TdApi.AuthorizationStateClosed -> {
                Log.d("TDLib", "TDLib client closed. Clearing app data.")
                
                // Clear preferences
                appContext?.getSharedPreferences("tdlib_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.clear()
                    ?.apply()
                
                // Reset in-memory credentials
                apiId = 0
                apiHash = ""
                
                // Delete TDLib database directory
                appContext?.let { ctx ->
                    val tdlibDir = File(ctx.filesDir, "tdlib")
                    if (tdlibDir.exists()) {
                        tdlibDir.deleteRecursively()
                        Log.d("TDLib", "Deleted tdlib directory")
                    }
                }
                
                client = null
                _chatList.value = emptyList()
                chatMap.clear()
                
                // Return explicitly to parameters input screen
                _authState.value = TdApi.AuthorizationStateWaitTdlibParameters()
            }
            else -> {
                Log.d("TDLib", "New auth state: $state")
            }
        }
    }

    fun sendPhoneNumber(phoneNumber: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null)) { result ->
             Log.d("TDLib", "Phone number sent: $result")
        }
    }

    fun sendAuthCode(code: String) {
        _lastError.value = null
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
             Log.d("TDLib", "Auth code checked: $result")
             if (result is TdApi.Error) {
                 _lastError.value = "Error: ${result.message}"
             }
        }
    }

    fun sendPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
             Log.d("TDLib", "Password checked: $result")
        }
    }

    fun logOut() {
        client?.send(TdApi.LogOut()) { result ->
            Log.d("TDLib", "LogOut result: $result")
        }
    }

    fun getOptionBoolean(name: String, onResult: (Boolean) -> Unit) {
        client?.send(TdApi.GetOption(name)) { result ->
            if (result is TdApi.OptionValueBoolean) {
                onResult(result.value)
            } else {
                onResult(false)
            }
        }
    }

    fun setOptionBoolean(name: String, value: Boolean) {
        client?.send(TdApi.SetOption(name, TdApi.OptionValueBoolean(value))) { result ->
            Log.d("TDLib", "Set option $name to $value result: $result")
        }
    }

    fun getUser(userId: Long, onResult: (TdApi.User?) -> Unit) {
        client?.send(TdApi.GetUser(userId)) { result ->
            if (result is TdApi.User) {
                onResult(result)
            } else {
                onResult(null)
            }
        }
    }
    
    fun resolveBotAndOpenChat(username: String, onResult: (Long?) -> Unit) {
        client?.send(TdApi.SearchPublicChat(username)) { result ->
            if (result is TdApi.Chat) {
                onResult(result.id)
            } else {
                Log.e("TDLibPrivacy", "Failed to resolve bot $username: $result")
                onResult(null)
            }
        }
    }
    
    fun downloadFile(fileId: Int, priority: Int = 1, synchronous: Boolean = true) {
        client?.send(TdApi.DownloadFile(fileId, priority, 0, 0, synchronous)) { result ->
            if (result is TdApi.Error) {
                Log.e("TDLib", "Failed to download file $fileId: ${result.message}")
            }
        }
    }
    
    fun getStorageStatistics(chatLimit: Int = 50, onResult: (TdApi.StorageStatistics?) -> Unit) {
        client?.send(TdApi.GetStorageStatistics(chatLimit)) { result ->
            if (result is TdApi.StorageStatistics) {
                onResult(result)
            } else {
                Log.e("TDLibStorage", "Failed to get storage stats: $result")
                onResult(null)
            }
        }
    }
    
    fun optimizeStorage(
        sizeLimit: Long = -1,
        ttl: Int = -1,
        countLimit: Int = -1,
        immunityDelay: Int = -1,
        fileTypes: Array<TdApi.FileType> = emptyArray(),
        chatIds: LongArray = longArrayOf(),
        excludeChatIds: LongArray = longArrayOf(),
        returnDeletedFileStatistics: Boolean = false,
        chatLimit: Int = 50,
        onResult: (TdApi.StorageStatistics?) -> Unit
    ) {
        val request = TdApi.OptimizeStorage(
            sizeLimit,
            ttl,
            countLimit,
            immunityDelay,
            fileTypes,
            chatIds,
            excludeChatIds,
            returnDeletedFileStatistics,
            chatLimit
        )
        client?.send(request) { result ->
            if (result is TdApi.StorageStatistics) {
                onResult(result)
            } else {
                Log.e("TDLibStorage", "Failed to optimize storage: $result")
                onResult(null)
            }
        }
    }
    
    fun exportDownloadedFile(file: TdApi.File, customName: String? = null, onResult: (Boolean, String?) -> Unit) {
        if (!file.local.isDownloadingCompleted || file.local.path.isEmpty()) {
            onResult(false, "File not downloaded")
            return
        }
        
        try {
            val sourceFile = File(file.local.path)
            if (!sourceFile.exists()) {
                onResult(false, "Source file not found")
                return
            }
            
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val extension = sourceFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
            val fileName = customName ?: "TelegramFile_${file.id}$extension"
            val destFile = File(downloadsDir, fileName)
            
            sourceFile.copyTo(destFile, overwrite = true)
            onResult(true, destFile.absolutePath)
        } catch (e: Exception) {
            Log.e("TDLibPrivacy", "Failed to export file: ${e.message}")
            onResult(false, e.message)
        }
    }

    fun openChat(chatId: Long) {
        currentChatId = chatId
        _messagesFlow.value = emptyList()
        client?.send(TdApi.OpenChat(chatId)) { }
    }
    
    fun closeChat(chatId: Long) {
        if (currentChatId == chatId) {
            currentChatId = null
        }
        client?.send(TdApi.CloseChat(chatId)) { }
    }

    fun loadMessages(chatId: Long, fromMessageId: Long = 0, limit: Int = 50, onResult: (Int) -> Unit = {}) {
        // If getting initial messages, use offset 0 to start from newest
        client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) { result ->
            if (chatId != currentChatId) {
                Log.d("TDLibChat", "Ignoring messages for old chat $chatId")
                return@send
            }
            if (result is TdApi.Messages) {
                Log.d("TDLibChat", "Loaded ${result.messages.size} messages for chat $chatId, totalCount = ${result.totalCount}")
                val currentList = if (fromMessageId == 0L) emptyList() else _messagesFlow.value
                val newList = currentList + result.messages
                _messagesFlow.value = newList.distinctBy { it.id }.sortedByDescending { it.date }
                onResult(result.messages.size)
            } else if (result is TdApi.Error) {
                Log.e("TDLibChat", "GetChatHistory error: ${result.code} - ${result.message}")
                onResult(0)
            } else {
                Log.w("TDLibChat", "GetChatHistory unexpected result: $result")
                onResult(0)
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        val inputMessageContent = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()), 
            null, // linkPreviewOptions
            true  // clearDraft
        )
        val sendMessageRequest = TdApi.SendMessage(
            chatId, 
            null, // topicId
            null, // replyTo
            null, // options
            null, // replyMarkup
            inputMessageContent
        )
        client?.send(sendMessageRequest) { result ->
             Log.d("TDLib", "SendMessage result: $result")
        }
    }
    
    fun getChatInfo(chatId: Long, onResult: (TdApi.Chat?) -> Unit) {
        val chat = chatMap[chatId]
        if (chat != null) {
            onResult(chat)
        } else {
            client?.send(TdApi.GetChat(chatId)) { result ->
                if (result is TdApi.Chat) {
                    chatMap[chatId] = result
                    onResult(result)
                } else {
                    onResult(null)
                }
            }
        }
        }
    }

    fun getForumTopic(chatId: Long, messageThreadId: Long, onResult: (TdApi.ForumTopic?) -> Unit) {
        client?.send(TdApi.GetForumTopic(chatId, messageThreadId.toInt())) { result ->
            if (result is TdApi.ForumTopic) {
                onResult(result)
            } else {
                onResult(null)
            }
        }
    }
}
