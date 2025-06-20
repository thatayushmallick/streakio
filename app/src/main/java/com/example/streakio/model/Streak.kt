package com.example.streakio.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Streak(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val creatorId: String = "",
    val participants: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null
)
