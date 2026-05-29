package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.db.ChatDatabase
import com.example.data.db.ChatMessage
import com.example.data.db.ChatSession
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Int? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val inputText: String = "",
    val customApiKey: String = "",
    val showSettingsDialog: Boolean = false,
    val errorNotification: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("chatbot_prefs", Context.MODE_PRIVATE)

    private val repository: ChatRepository by lazy {
        val database = ChatDatabase.getDatabase(application)
        ChatRepository(database.chatSessionDao(), database.chatMessageDao())
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null

    init {
        // Load persistable custom API Key if any
        val savedKey = sharedPrefs.getString("custom_api_key", "") ?: ""
        _uiState.update { it.copy(customApiKey = savedKey) }

        // Start collecting chat sessions reactively
        viewModelScope.launch {
            repository.allSessions.collect { sessionList ->
                _uiState.update { it.copy(sessions = sessionList) }
                
                // If there's no current session select the most recent one automatically
                if (_uiState.value.currentSessionId == null && sessionList.isNotEmpty()) {
                    selectSession(sessionList.first().id)
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun selectSession(sessionId: Int) {
        if (_uiState.value.currentSessionId == sessionId) return
        
        _uiState.update { it.copy(currentSessionId = sessionId, errorNotification = null) }
        
        // Cancel previous message flow and start observing the new session's messages
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessagesFlow(sessionId).collect { messageList ->
                _uiState.update { it.copy(messages = messageList) }
            }
        }
    }

    fun startNewSession() {
        messagesJob?.cancel()
        _uiState.update { 
            it.copy(
                currentSessionId = null,
                messages = emptyList(),
                inputText = "",
                errorNotification = null
            ) 
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                // Return to null session (which defaults to starting a new session/auto selecting)
                _uiState.update { it.copy(currentSessionId = null, messages = emptyList()) }
            }
        }
    }

    fun clearHistory() {
        val currentId = _uiState.value.currentSessionId ?: return
        viewModelScope.launch {
            repository.clearSessionHistory(currentId)
        }
    }

    fun sendMessage() {
        val prompt = _uiState.value.inputText.trim()
        if (prompt.isEmpty() || _uiState.value.isTyping) return

        // Clear input field immediately
        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            try {
                // 1. Resolve API key
                val apiKey = getEffectiveApiKey()
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.update { 
                        it.copy(
                            errorNotification = "API Key is missing or invalid. Please configure it in Settings.",
                            inputText = prompt // Restore prompt upon key failure
                        ) 
                    }
                    return@launch
                }

                _uiState.update { it.copy(isTyping = true, errorNotification = null) }

                // 2. Resolve or create Session
                var sessionId = _uiState.value.currentSessionId
                val isNewSession = sessionId == null
                
                if (sessionId == null) {
                    // Extract initial title from prefix of prompt (max 30 chars)
                    val rawTitle = if (prompt.length > 30) "${prompt.take(30)}..." else prompt
                    sessionId = repository.createNewSession(rawTitle)
                    
                    // Select this newly generated session to trigger messages list flow
                    selectSession(sessionId)
                }

                // 3. Save User message to database
                repository.saveMessage(sessionId!!, isUser = true, text = prompt)

                // 4. Hit remote Gemini API
                val responseText = repository.fetchAiResponse(
                    sessionId = sessionId,
                    apiKey = apiKey
                )

                // 5. Save Model response message to database
                repository.saveMessage(sessionId, isUser = false, text = responseText)

                // 6. Dynamic renaming of session if it was the first message
                if (isNewSession) {
                    val formattedTitle = if (prompt.length > 25) "${prompt.take(25)}..." else prompt
                    repository.updateSessionActive(sessionId, title = formattedTitle)
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(errorNotification = "Failing to generate message: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isTyping = false) }
            }
        }
    }

    fun updateCustomApiKey(key: String) {
        sharedPrefs.edit().putString("custom_api_key", key).apply()
        _uiState.update { it.copy(customApiKey = key) }
    }

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun clearErrorNotification() {
        _uiState.update { it.copy(errorNotification = null) }
    }

    private fun getEffectiveApiKey(): String {
        val customKey = _uiState.value.customApiKey.trim()
        if (customKey.isNotEmpty()) {
            return customKey
        }
        
        // Fallback to BuildConfig if available and is not the default env.example template string
        val buildConfigKey = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("")
        return if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }
}
