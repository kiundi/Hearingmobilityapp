package com.example.hearingmobilityapp

/**
 * Data class for chat messages used in the chat interface
 * @param text The message content
 * @param isUser Whether the message is from the user (true) or system (false)
 * @param timestamp When the message was created (defaults to current time)
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) 