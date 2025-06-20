package com.example.streakio.viewmodel // Or your ViewModel package

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.streakio.model.Streak
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.util.DataChangeNotifier // <-- IMPORT THIS
import kotlinx.coroutines.launch

data class CreateStreakUiState(
    val title: String = "",
    val description: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isStreakCreated: Boolean = false // To signal navigation
)

class CreateStreakViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {

    var uiState by mutableStateOf(CreateStreakUiState())
        private set

    fun onTitleChange(newTitle: String) {
        uiState = uiState.copy(title = newTitle, errorMessage = null)
    }

    fun onDescriptionChange(newDescription: String) {
        uiState = uiState.copy(description = newDescription, errorMessage = null)
    }

    fun createStreak() {
        if (uiState.title.isBlank()) {
            uiState = uiState.copy(errorMessage = "Title cannot be empty.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val firebaseUser = repository.getCurrentFirebaseUser()

            if (firebaseUser == null) {
                uiState = uiState.copy(isLoading = false, errorMessage = "User not authenticated. Please log in again.")
                return@launch
            }

            val newStreak = Streak(
                title = uiState.title.trim(),
                description = uiState.description.trim(),
                creatorId = firebaseUser.uid, // Use uid from FirebaseUser
                participants = listOf(firebaseUser.uid) // Use uid
            )

            // Make sure your repository.createStreak(Streak) method exists
            // and matches this signature.
            val result = repository.createStreak(newStreak)
            if (result.isSuccess) {
                DataChangeNotifier.notifyStreakDataChanged() // <-- ADD THIS LINE
                uiState = uiState.copy(isLoading = false, isStreakCreated = true)
            } else {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to create streak."
                )
            }
        }
    }

    fun resetStreakCreatedFlag() {
        uiState = uiState.copy(isStreakCreated = false)
    }

    fun clearErrorMessage() {
        uiState = uiState.copy(errorMessage = null)
    }


    // Companion object for ViewModel Factory
    companion object {
        fun provideFactory(
            repository: FirebaseRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreateStreakViewModel::class.java)) {
                    return CreateStreakViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
