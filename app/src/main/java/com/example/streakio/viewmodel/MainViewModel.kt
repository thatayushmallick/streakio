package com.example.streakio.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.streakio.model.Streak
import com.example.streakio.model.User
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.util.DataChangeNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log

data class MainUiState(
    val streaks: List<Streak> = emptyList(),
    val currentUser: User? = null,
    val isLoadingStreaks: Boolean = false, // More specific loading state
    val isLoadingUser: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(private val repository: FirebaseRepository) : ViewModel() {

    var uiState by mutableStateOf(MainUiState(isLoadingUser = true, isLoadingStreaks = true))
        private set

    private var streaksListenerJob: Job? = null

    init {
        loadCurrentUserAndObserveStreaks()

        // Optional: Listen for general data changes if needed for actions
        // not covered by direct Firestore listeners, or for explicit refresh triggers.
        viewModelScope.launch {
            DataChangeNotifier.streakDataChangedEvent.collectLatest {
                Log.d("MainViewModel", "DataChangeNotifier event received. Current listeners should cover most cases. Consider if specific action needed.")
                // Potentially, you might want to re-trigger loadCurrentUserAndObserveStreaks() if this
                // signal implies something that Firestore listeners wouldn't pick up,
                // or if you want an explicit "pull-to-refresh" that also re-fetches the user.
                // For now, primary reliance is on Firestore listeners for streak data.
                // refreshStreaks() // If you keep this as a manual trigger
            }
        }
    }

    private fun loadCurrentUserAndObserveStreaks() {
        uiState = uiState.copy(isLoadingUser = true, errorMessage = null)
        viewModelScope.launch {
            val customUser = repository.getCurrentCustomUser()
            uiState = uiState.copy(currentUser = customUser, isLoadingUser = false)

            streaksListenerJob?.cancel() // Cancel any existing listener before starting a new one

            if (customUser?.id != null) {
                uiState = uiState.copy(isLoadingStreaks = true) // Set loading true for streaks
                streaksListenerJob = viewModelScope.launch {
                    repository.getStreaksForUserFlow(customUser.id)
                        .catch { e ->
                            Log.e("MainViewModel", "Error collecting streaks flow for user ${customUser.id}", e)
                            uiState = uiState.copy(
                                isLoadingStreaks = false,
                                errorMessage = "Failed to load streaks: ${e.message}"
                            )
                        }
                        .collect { result ->
                            result.fold(
                                onSuccess = { userStreaks ->
                                    Log.d("MainViewModel", "Streaks updated for user ${customUser.id}: ${userStreaks.size} streaks")
                                    uiState = uiState.copy(
                                        streaks = userStreaks,
                                        isLoadingStreaks = false, // Loaded
                                        errorMessage = null // Clear previous error on success
                                    )
                                },
                                onFailure = { exception ->
                                    Log.e("MainViewModel", "Failed to get streaks update for user ${customUser.id}", exception)
                                    uiState = uiState.copy(
                                        isLoadingStreaks = false,
                                        errorMessage = "Error updating streaks: ${exception.message}"
                                    )
                                }
                            )
                        }
                }
            } else {
                uiState = uiState.copy(
                    isLoadingStreaks = false, // Not loading if no user
                    streaks = emptyList(),
                    errorMessage = if (customUser == null) "User not logged in." else null
                )
            }
        }
    }

    // This function can be kept for an explicit pull-to-refresh if desired,
    // or if there are scenarios where you want to force a re-fetch of the user AND streaks.
    fun refreshAllData() {
        Log.d("MainViewModel", "Manual refreshAllData triggered.")
        // This will re-fetch the user and re-establish the streaks flow.
        loadCurrentUserAndObserveStreaks()
    }

    // If you only want to refresh streaks based on the current user (e.g., after a DataChangeNotifier event)
    // and assume the user is still the same. However, loadCurrentUserAndObserveStreaks is safer.
    // fun refreshStreaks() {
    // val userId = uiState.currentUser?.id
    // if (userId != null && streaksListenerJob?.isActive != true) { // Check if job isn't already running
    // Log.d("MainViewModel", "Explicitly refreshing streaks for user $userId")
    // observeStreaksForUser(userId) // A separated function to just observe streaks
    // } else if (userId == null) {
    // uiState = uiState.copy(isLoadingStreaks = false, streaks = emptyList(), errorMessage = "Cannot refresh: User not available.")
    // }
    // }

    fun handleLogout(onLogoutCallback: () -> Unit) {
        streaksListenerJob?.cancel() // Important to cancel listeners on logout
        repository.signOut()
        uiState = MainUiState(isLoadingUser = false, isLoadingStreaks = false) // Reset state
        onLogoutCallback()
    }

    companion object {
        fun provideFactory(
            repository: FirebaseRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    return MainViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
