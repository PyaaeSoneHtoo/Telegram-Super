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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object TelegramClient {
    private var client: Client? = null
    private var appContext: Context? = null
    
    const val VIRTUAL_ID_ALL = -1
    const val VIRTUAL_ID_PERSONAL = -2
    const val VIRTUAL_ID_GROUPS = -3
    const val VIRTUAL_ID_CHANNELS = -4
    
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
    
    private val _chatFolders = MutableStateFlow<List<TdApi.ChatFolderInfo>>(emptyList())
    val chatFolders: StateFlow<List<TdApi.ChatFolderInfo>> = _chatFolders.asStateFlow()
    
    private val _currentChatList = MutableStateFlow<TdApi.ChatList>(TdApi.ChatListMain())
    val currentChatList: StateFlow<TdApi.ChatList> = _currentChatList.asStateFlow()
    
    private val _currentVirtualFolderId = MutableStateFlow<Int>(VIRTUAL_ID_ALL)
    val currentVirtualFolderId: StateFlow<Int> = _currentVirtualFolderId.asStateFlow()
    
    private val userRequestedFileIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val userStatusMap = java.util.concurrent.ConcurrentHashMap<Long, TdApi.UserStatus>()
    private val _userStatuses = MutableStateFlow<Map<Long, TdApi.UserStatus>>(emptyMap())
    val userStatuses: StateFlow<Map<Long, TdApi.UserStatus>> = _userStatuses.asStateFlow()
    
    private var currentChatId: Long? = null
    private var currentThreadId: Long = 0L
    
    private var notificationManager: DownloadNotificationManager? = null

    private fun updateChatList() {
        val currentList = _currentChatList.value
        val virtualId = _currentVirtualFolderId.value
        
        // Base chats for the selected TDLib list (Main or explicit Folder)
        val filtered = chatMap.values.filter { chat ->
            chat.positions.any { isSameChatList(it.list, currentList) }
        }
        
        // Secondary filtering for virtual folders (only applies if we are in ChatListMain according to the plan)
        val virtualFiltered = if (currentList is TdApi.ChatListMain) {
            when (virtualId) {
                VIRTUAL_ID_PERSONAL -> filtered.filter { chat ->
                    chat.type is TdApi.ChatTypePrivate || chat.type is TdApi.ChatTypeSecret
                }
                VIRTUAL_ID_GROUPS -> filtered.filter { chat ->
                    val type = chat.type
                    type is TdApi.ChatTypeBasicGroup || (type is TdApi.ChatTypeSupergroup && !type.isChannel)
                }
                VIRTUAL_ID_CHANNELS -> filtered.filter { chat ->
                    val type = chat.type
                    type is TdApi.ChatTypeSupergroup && type.isChannel
                }
                else -> filtered
            }
        } else {
            filtered
        }

        val sorted = virtualFiltered.sortedByDescending { chat ->
            chat.positions.first { isSameChatList(it.list, currentList) }.order
        }
        _chatList.value = sorted
    }

    private fun isSameChatList(l1: TdApi.ChatList, l2: TdApi.ChatList): Boolean {
        if (l1.constructor != l2.constructor) return false
        if (l1 is TdApi.ChatListFolder && l2 is TdApi.ChatListFolder) {
            return l1.chatFolderId == l2.chatFolderId
        }
        return true
    }

    fun setChatList(chatListId: Int) {
        _currentVirtualFolderId.value = chatListId
        val tdLibList = when {
            chatListId >= 0 -> TdApi.ChatListFolder(chatListId)
            else -> TdApi.ChatListMain()
        }
        _currentChatList.value = tdLibList
        updateChatList()
        client?.send(TdApi.LoadChats(tdLibList, 100)) { }
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
                            val newPositions = chat.positions.filter { !isSameChatList(it.list, tdApiObject.position.list) }.toMutableList()
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
                    is TdApi.UpdateChatFolders -> {
                        _chatFolders.value = tdApiObject.chatFolders.toList()
                    }
                    is TdApi.UpdateUserStatus -> {
                        userStatusMap[tdApiObject.userId] = tdApiObject.status
                        _userStatuses.value = userStatusMap.toMap()
                    }
                    is TdApi.UpdateFile -> {
                        val currentFiles = _downloadedFiles.value.toMutableMap()
                        currentFiles[tdApiObject.file.id] = tdApiObject.file
                        _downloadedFiles.value = currentFiles
                        Log.d("TDLibFile", "UpdateFile: id=${tdApiObject.file.id}, downloaded=${tdApiObject.file.local.downloadedSize}/${tdApiObject.file.size}, path=${tdApiObject.file.local.path}")
                        
                        // Handle download notifications only for user-requested files
                        if (userRequestedFileIds.contains(tdApiObject.file.id)) {
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
                                userRequestedFileIds.remove(tdApiObject.file.id)
                            }
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
                // Cache the status from the user object
                userStatusMap[userId] = result.status
                _userStatuses.value = userStatusMap.toMap()
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
    
    fun downloadFile(fileId: Int, priority: Int = 1, synchronous: Boolean = false, isUserRequested: Boolean = false) {
        val finalPriority = if (isUserRequested) 32 else priority
        if (isUserRequested) {
            userRequestedFileIds.add(fileId)
        }
        client?.send(TdApi.DownloadFile(fileId, finalPriority, 0, 0, synchronous)) { result ->
            if (result is TdApi.Error) {
                Log.e("TDLib", "Failed to download file $fileId: ${result.message}")
                if (isUserRequested) userRequestedFileIds.remove(fileId)
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

    fun openChat(chatId: Long, threadId: Long = 0L) {
        currentChatId = chatId
        currentThreadId = threadId
        _messagesFlow.value = emptyList()
        client?.send(TdApi.OpenChat(chatId)) { }
    }
    
    fun closeChat(chatId: Long) {
        if (currentChatId == chatId) {
            currentChatId = null
        }
        client?.send(TdApi.CloseChat(chatId)) { }
    }

    fun loadMessages(chatId: Long, threadId: Long = 0, fromMessageId: Long = 0, limit: Int = 50, retryCount: Int = 0, onResult: (Int) -> Unit = {}) {
        val function = if (threadId != 0L) {
            // For forum topics: use the dedicated GetForumTopicHistory API
            TdApi.GetForumTopicHistory(chatId, threadId.toInt(), fromMessageId, 0, limit)
        } else {
            TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)
        }
        
        client?.send(function) { result ->
            // Reject messages that don't belong to the currently open chat+thread
            if (chatId != currentChatId || threadId != currentThreadId) {
                Log.d("TDLibChat", "Ignoring stale messages for chat $chatId thread $threadId (current: $currentChatId/$currentThreadId)")
                return@send
            }
            if (result is TdApi.Messages) {
                Log.d("TDLibChat", "Loaded ${result.messages.size} messages for chat $chatId (thread $threadId)")
                val currentList = if (fromMessageId == 0L) emptyList() else _messagesFlow.value
                val newList = currentList + result.messages
                _messagesFlow.value = newList.distinctBy { it.id }.sortedByDescending { it.date }
                onResult(result.messages.size)
            } else if (result is TdApi.Error) {
                Log.e("TDLibChat", "Load messages error: ${result.code} - ${result.message}")
                onResult(0)
            } else {
                Log.w("TDLibChat", "Load messages unexpected result: $result")
                onResult(0)
            }
        }
    }

    /**
     * Load messages around [targetMessageId]. Clears current messages and loads 50 messages
     * ending at (and including) the target message, plus some newer ones.
     * Calls [onLoaded] with the index of the target message in the new list (for scrolling).
     */
    fun loadMessagesFromId(chatId: Long, threadId: Long, targetMessageId: Long, onLoaded: (Int) -> Unit) {
        // Load 25 messages older than target and keep the target itself
        // GetChatHistory/GetForumTopicHistory: fromMessageId=target gives messages < target
        // Use targetMessageId+1 as fromMessageId to include the target
        val fromMessageId = targetMessageId + 1
        val limit = 50

        val function: TdApi.Function<TdApi.Messages> = if (threadId != 0L) {
            TdApi.GetForumTopicHistory(chatId, threadId.toInt(), fromMessageId, 0, limit)
        } else {
            TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)
        }

        client?.send(function) { result ->
            if (chatId != currentChatId || threadId != currentThreadId) return@send
            if (result is TdApi.Messages) {
                val msgs = result.messages.toList().sortedByDescending { it.date }
                _messagesFlow.value = msgs.distinctBy { it.id }
                val idx = msgs.indexOfFirst { it.id == targetMessageId }
                onLoaded(if (idx >= 0) idx else 0)
            } else {
                Log.e("TDLibChat", "loadMessagesFromId error: $result")
                onLoaded(0)
            }
        }
    }

    fun sendMessage(chatId: Long, text: String, threadId: Long = 0) {
        val inputMessageContent = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()), 
            null, // linkPreviewOptions
            true  // clearDraft
        )
        val sendMessageRequest = TdApi.SendMessage(
            chatId, 
            if (threadId != 0L) TdApi.MessageTopicForum(threadId.toInt()) else null, 
            null, // replyTo
            null, // options
            null, // replyMarkup
            inputMessageContent
        )
        client?.send(sendMessageRequest) { result ->
             Log.d("TDLib", "SendMessage result: $result")
        }
    }

    enum class MediaType { PHOTO, VIDEO, AUDIO, DOCUMENT }

    fun sendMediaMessage(chatId: Long, filePath: String, mimeType: String, threadId: Long = 0) {
        val topicId = if (threadId != 0L) TdApi.MessageTopicForum(threadId.toInt()) else null
        val localFile = TdApi.InputFileLocal(filePath)
        val content: TdApi.InputMessageContent = when {
            mimeType == "audio/voice" -> TdApi.InputMessageVoiceNote(
                localFile, 0, byteArrayOf(), null, null
            )
            mimeType.startsWith("image/") -> TdApi.InputMessagePhoto(
                localFile, null, intArrayOf(), 0, 0, null, false, null, false
            )
            mimeType.startsWith("video/") -> TdApi.InputMessageVideo(
                localFile, null, null, 0, intArrayOf(), 0, 0, 0, true, null, false, null, false
            )
            mimeType.startsWith("audio/") -> TdApi.InputMessageAudio(
                localFile, null, 0, "", "", null
            )
            else -> TdApi.InputMessageDocument(localFile, null, false, null)
        }
        val request = TdApi.SendMessage(chatId, topicId, null, null, null, content)
        client?.send(request) { result ->
            Log.d("TDLib", "SendMedia result: $result")
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

    /** Search Telegram server for chats matching [query]. Returns up to [limit] Chat objects. */
    fun searchChatsGlobal(query: String, limit: Int = 20, onResult: (List<TdApi.Chat>) -> Unit) {
        if (query.isBlank()) { onResult(emptyList()); return }
        client?.send(TdApi.SearchChatsOnServer(query, limit)) { result ->
            if (result is TdApi.Chats) {
                val ids = result.chatIds
                if (ids.isEmpty()) { onResult(emptyList()); return@send }
                val resolved = mutableListOf<TdApi.Chat>()
                var remaining = ids.size
                ids.forEach { id ->
                    getChatInfo(id) { chat ->
                        synchronized(resolved) {
                            if (chat != null) resolved.add(chat)
                            remaining--
                            if (remaining == 0) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onResult(resolved)
                                }
                            }
                        }
                    }
                }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(emptyList()) }
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

    fun getForumTopics(chatId: Long, onResult: (TdApi.ForumTopics?) -> Unit) {
        client?.send(TdApi.GetForumTopics(chatId, "", 0, 0, 0, 50)) { result ->
            if (result is TdApi.ForumTopics) {
                onResult(result)
            } else {
                onResult(null)
            }
        }
    }
    fun getChatPinnedMessage(chatId: Long, onResult: (TdApi.Message?) -> Unit) {
        client?.send(TdApi.GetChatPinnedMessage(chatId)) { result ->
            if (result is TdApi.Message) {
                onResult(result)
            } else {
                onResult(null)
            }
        }
    }

    fun getPinnedMessages(chatId: Long, fromMessageId: Long = 0, limit: Int = 50, onResult: (List<TdApi.Message>) -> Unit) {
        client?.send(TdApi.SearchChatMessages(
            chatId,
            null, // topic_id
            "", // query
            null, // sender
            fromMessageId,
            0, // offset
            limit,
            TdApi.SearchMessagesFilterPinned()
        )) { result ->
            if (result is TdApi.FoundChatMessages) {
                onResult(result.messages.toList())
            } else {
                onResult(emptyList())
            }
        }
    }

    fun deleteMessages(chatId: Long, messageIds: LongArray, revoke: Boolean) {
        client?.send(TdApi.DeleteMessages(chatId, messageIds, revoke)) { result ->
            if (result is TdApi.Error) {
                Log.e("TDLib", "Delete messages error: ${result.message}")
            } else {
                Log.d("TDLib", "Messages deleted successfully")
            }
        }
    }

    fun toggleChatIsPinned(chatId: Long, isPinned: Boolean) {
        client?.send(TdApi.ToggleChatIsPinned(TdApi.ChatListMain(), chatId, isPinned)) { result ->
            if (result is TdApi.Error) Log.e("TDLib", "Toggle pin error: ${result.message}")
        }
    }

    fun toggleChatIsMarkedAsUnread(chatId: Long, isMarkedAsUnread: Boolean) {
        client?.send(TdApi.ToggleChatIsMarkedAsUnread(chatId, isMarkedAsUnread)) { result ->
            if (result is TdApi.Error) Log.e("TDLib", "Toggle unread error: ${result.message}")
        }
    }

    fun deleteChatHistory(chatId: Long, removeFromChatList: Boolean, revoke: Boolean) {
        client?.send(TdApi.DeleteChatHistory(chatId, removeFromChatList, revoke)) { result ->
            if (result is TdApi.Error) Log.e("TDLib", "Delete history error: ${result.message}")
        }
    }

    fun deleteChat(chatId: Long) {
        client?.send(TdApi.DeleteChat(chatId)) { result ->
            if (result is TdApi.Error) Log.e("TDLib", "Delete chat error: ${result.message}")
        }
    }

    fun blockUser(userId: Long) {
        client?.send(TdApi.SetMessageSenderBlockList(TdApi.MessageSenderUser(userId), TdApi.BlockListMain())) { result ->
            if (result is TdApi.Error) Log.e("TDLib", "Block user error: ${result.message}")
        }
    }

    fun leaveChat(chatId: Long) {
        client?.send(TdApi.LeaveChat(chatId)) { result ->
            if (result is TdApi.Error) Log.e("TDLib", "Leave chat error: ${result.message}")
        }
    }

    fun getInternalLinkInfo(link: String, onResult: (TdApi.InternalLinkType?) -> Unit) {
        client?.send(TdApi.GetInternalLinkType(link)) { result ->
            if (result is TdApi.InternalLinkType) {
                onResult(result)
            } else {
                Log.e("TDLibLink", "Failed to get internal link type for $link: $result")
                onResult(null)
            }
        }
    }
}
