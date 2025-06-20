package com.example.streakio.model

import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

data class StreakEntry(
    val id: String = "",
    val streakId: String = "",
    val userId: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null,
    val notes: String? = null
)