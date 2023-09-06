package com.example.bartai

data class MessageModel(
    val message: String,
    val isUserMessage: Boolean,
    val timeStamp: String? = null
)
