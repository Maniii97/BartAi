package com.example.bartai

data class MessageModel(
    val message: String,
    val isUserMessage: Boolean,
    val sectionLink: String? = null
)
