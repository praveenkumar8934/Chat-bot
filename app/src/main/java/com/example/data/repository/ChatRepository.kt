package com.example.data.repository

import com.example.data.api.*
import com.example.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val apiService: GeminiApiService = RetrofitClient.service
) {
    val allSessions: Flow<List<ChatSession>> = chatSessionDao.getAllSessionsFlow()

    fun getMessagesFlow(sessionId: Int): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForSessionFlow(sessionId)
    }

    suspend fun getMessagesSync(sessionId: Int): List<ChatMessage> = withContext(Dispatchers.IO) {
        chatMessageDao.getMessagesForSessionSync(sessionId)
    }

    suspend fun createNewSession(title: String): Int = withContext(Dispatchers.IO) {
        val session = ChatSession(title = title, lastActiveTimestamp = System.currentTimeMillis())
        chatSessionDao.insertSession(session).toInt()
    }

    suspend fun updateSessionActive(sessionId: Int, title: String? = null) = withContext(Dispatchers.IO) {
        val existing = chatSessionDao.getSessionById(sessionId)
        if (existing != null) {
            val updated = existing.copy(
                title = title ?: existing.title,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            chatSessionDao.updateSession(updated)
        }
    }

    suspend fun deleteSession(sessionId: Int) = withContext(Dispatchers.IO) {
        chatSessionDao.deleteSessionById(sessionId)
    }

    suspend fun saveMessage(sessionId: Int, isUser: Boolean, text: String): ChatMessage = withContext(Dispatchers.IO) {
        val message = ChatMessage(
            sessionId = sessionId,
            isUser = isUser,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val id = chatMessageDao.insertMessage(message).toInt()
        
        // Update the session's active time
        updateSessionActive(sessionId)
        
        message.copy(id = id)
    }

    suspend fun clearSessionHistory(sessionId: Int) = withContext(Dispatchers.IO) {
        chatMessageDao.clearMessagesForSession(sessionId)
    }

    /**
     * Sends the complete chat history for the session to Gemini API.
     * This provides conversational memory.
     */
    suspend fun fetchAiResponse(
        sessionId: Int,
        apiKey: String,
        model: String = "gemini-3.5-flash",
        systemInstructionText: String? = "You are a helpful, smart, and friendly AI chatbot assistant. Answer the user queries accurately and effectively."
    ): String = withContext(Dispatchers.IO) {
        try {
            // Get all existing messages for context
            val messages = chatMessageDao.getMessagesForSessionSync(sessionId)
            
            // Map db messages to API Content objects
            val contents = messages.map { msg ->
                Content(
                    role = if (msg.isUser) "user" else "model",
                    parts = listOf(Part(text = msg.text))
                )
            }

            // Create the request
            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = systemInstructionText?.let {
                    Content(parts = listOf(Part(text = it)))
                },
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    maxOutputTokens = 2048
                )
            )

            // Execute the API call
            val response = apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )

            // Extract response text
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            text ?: "The AI did not return a response. Please try again."
        } catch (e: Exception) {
            "Network Error: ${e.localizedMessage ?: e.message ?: "Unknown error"}. Check your API Key settings."
        }
    }
}
