package com.example.hearingmobilityapp


import com.aallam.openai.api.BetaOpenAI
import com.example.hearingmobilityapp.BuildConfig
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIHelper {
    private val apiKey = BuildConfig.OPENAI_API_KEY
    private val openAI = OpenAI(OpenAIConfig(apiKey))

    @OptIn(BetaOpenAI::class)
    suspend fun getChatResponse(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val chatCompletionRequest = ChatCompletionRequest(
                    model = ModelId("gpt-3.5-turbo"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = "You are a helpful transit assistant. When users ask about stops or routes that don't exist, provide helpful alternatives or suggestions. Keep responses concise and focused on transit-related queries."
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = query
                        )
                    )
                )

                val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
                completion.choices.first().message.content ?: "I apologize, but I couldn't generate a response at the moment."
            } catch (e: Exception) {
                "I apologize, but I'm having trouble connecting to the AI service at the moment. Please try asking about specific stop names or numbers."
            }
        }
    }
}
