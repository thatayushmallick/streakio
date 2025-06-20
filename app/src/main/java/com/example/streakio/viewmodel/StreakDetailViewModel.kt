package com.example.streakio.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.streakio.model.Streak
import com.example.streakio.model.StreakEntry
import com.example.streakio.model.User
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.util.DataChangeNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

// Data class for the history table structure
data class UserDailyEntryStatus(
    val userId: String,
    val userName: String,
    val entriesByDate: Map<LocalDate, Boolean>
)

// Data class for the UI state of the history table
data class StreakHistoryUiState(
    val dateHeaders: List<LocalDate> = emptyList(),
    val userEntries: List<UserDailyEntryStatus> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// Updated main UI State
data class StreakDetailUiState(
    val streak: Streak? = null,
    val entries: List<StreakEntry> = emptyList(),
    val streakHistory: StreakHistoryUiState = StreakHistoryUiState(isLoading = true),
    val isLoadingStreak: Boolean = false,
    val errorMessage: String? = null,
    val isAddingParticipant: Boolean = false,
    val isLoggingEntry: Boolean = false,
    val entryLoggedSuccessfully: Boolean = false,
    val participantAddedSuccessfully: Boolean = false,
    val hasLoggedToday: Boolean = false, // New: To control log button state
    val isRemovingEntry: Boolean = false, // New: For entry removal action
    val entryRemovedSuccessfully: Boolean = false // New: For removal snackbar
)

class StreakDetailViewModel(
    private val repository: FirebaseRepository,
    private val streakId: String
) : ViewModel() {

    var uiState by mutableStateOf(StreakDetailUiState(isLoadingStreak = true))
        private set

    var participantEmailInput by mutableStateOf("")
        private set

    private var streakDataProcessingJob: Job? = null

    init {
        observeStreakDataForHistory()

        viewModelScope.launch {
            DataChangeNotifier.streakDataChangedEvent.collectLatest {
                Log.d("StreakDetailVM", "DataChangeNotifier event received for $streakId. Re-evaluating data.")
                // Re-triggering the observer ensures all states are fresh
                observeStreakDataForHistory()
            }
        }
    }

    private suspend fun getUserDetails(userId: String): User? {
        return repository.getUserById(userId).fold(
            onSuccess = { it },
            onFailure = { e ->
                Log.e("StreakDetailVM", "Failed to get user details for $userId", e)
                null
            }
        )
    }

    private fun observeStreakDataForHistory() {
        streakDataProcessingJob?.cancel()
        uiState = uiState.copy(
            isLoadingStreak = true,
            streakHistory = uiState.streakHistory.copy(isLoading = true, errorMessage = null),
            hasLoggedToday = false // Reset, will be re-evaluated
        )

        streakDataProcessingJob = viewModelScope.launch {
            repository.getStreakByIdFlow(streakId)
                .combine(repository.getStreakEntriesFlow(streakId)) { streakResult, entriesResult ->
                    Pair(streakResult, entriesResult)
                }
                .catch { e ->
                    Log.e("StreakDetailVM", "Error in combined flow for $streakId", e)
                    uiState = uiState.copy(
                        isLoadingStreak = false,
                        streakHistory = uiState.streakHistory.copy(
                            isLoading = false,
                            errorMessage = "Error loading streak data: ${e.message}"
                        ),
                        errorMessage = uiState.errorMessage ?: "Error loading streak data: ${e.message}"
                    )
                }
                .collectLatest { (streakResult, entriesResult) ->
                    val currentStreak = streakResult.getOrNull()
                    val currentEntries = entriesResult.getOrNull() ?: emptyList()

                    if (streakResult.isFailure || (entriesResult.isFailure && currentStreak != null)) {
                        val streakError = if (streakResult.isFailure) streakResult.exceptionOrNull()?.message else null
                        val entriesError = if (entriesResult.isFailure) entriesResult.exceptionOrNull()?.message else null
                        val combinedError = listOfNotNull(streakError, entriesError).joinToString("; ")
                        Log.e("StreakDetailVM", "Failure in data streams: $combinedError")
                        uiState = uiState.copy(
                            isLoadingStreak = false,
                            streak = currentStreak,
                            entries = if (entriesResult.isSuccess) currentEntries else uiState.entries,
                            streakHistory = uiState.streakHistory.copy(
                                isLoading = false,
                                errorMessage = "Failed to update details: $combinedError"
                            ),
                            errorMessage = uiState.errorMessage ?: "Failed to update details: $combinedError"
                        )
                        return@collectLatest
                    }

                    uiState = uiState.copy(
                        streak = currentStreak,
                        entries = currentEntries,
                        isLoadingStreak = false
                    )

                    if (currentStreak == null) {
                        uiState = uiState.copy(
                            streakHistory = StreakHistoryUiState(isLoading = false, errorMessage = "Streak not found."),
                            errorMessage = uiState.errorMessage ?: "Streak not found."
                        )
                        return@collectLatest
                    }

                    uiState = uiState.copy(streakHistory = uiState.streakHistory.copy(isLoading = true, errorMessage = null))

                    val today = LocalDate.now()
                    val last7Days = List(7) { i -> today.minusDays(i.toLong()) }.reversed()

                    val participantUserJobs = currentStreak.participants.map { userId ->
                        async { getUserDetails(userId) }
                    }
                    val participantsDetails = participantUserJobs.awaitAll().filterNotNull()

                    if (participantsDetails.isEmpty() && currentStreak.participants.isNotEmpty()) {
                        Log.w("StreakDetailVM", "No participant details could be fetched for streak ${currentStreak.id} despite participants list having IDs.")
                    }

                    var currentUserHasLoggedToday = false
                    val currentUserId = repository.getCurrentFirebaseUser()?.uid

                    val userEntryStatuses = participantsDetails.map { user ->
                        val userEntriesOnDates = last7Days.associateWith { date ->
                            val entryExists = currentEntries.any { entry ->
                                entry.userId == user.id &&
                                        entry.timestamp != null &&
                                        entry.timestamp.toLocalDate() == date
                            }
                            if (user.id == currentUserId && date == today && entryExists) {
                                currentUserHasLoggedToday = true
                            }
                            entryExists
                        }
                        UserDailyEntryStatus(
                            user.id,
                            user.displayName ?: user.email.substringBefore('@').ifBlank { "User ${user.id.take(6)}" },
                            userEntriesOnDates
                        )
                    }
                    Log.d("StreakDetailVM", "Processed history. User $currentUserId hasLoggedToday: $currentUserHasLoggedToday for streak $streakId")

                    uiState = uiState.copy(
                        streakHistory = StreakHistoryUiState(
                            dateHeaders = last7Days,
                            userEntries = userEntryStatuses,
                            isLoading = false
                        ),
                        hasLoggedToday = currentUserHasLoggedToday
                    )
                }
        }
    }

    private fun com.google.firebase.Timestamp.toLocalDate(): LocalDate {
        return this.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun onParticipantEmailChange(email: String) {
        participantEmailInput = email
        if (uiState.errorMessage != null || uiState.participantAddedSuccessfully || uiState.entryLoggedSuccessfully || uiState.entryRemovedSuccessfully) {
            uiState = uiState.copy(
                errorMessage = null,
                participantAddedSuccessfully = false,
                entryLoggedSuccessfully = false,
                entryRemovedSuccessfully = false
            )
        }
    }

    fun addParticipant() {
        if (participantEmailInput.isBlank()) {
            uiState = uiState.copy(errorMessage = "Participant email cannot be empty.")
            return
        }
        val emailToAdd = participantEmailInput.trim()

        uiState = uiState.copy(isAddingParticipant = true, errorMessage = null, participantAddedSuccessfully = false)
        viewModelScope.launch {
            val result = repository.addParticipantToStreak(streakId, emailToAdd)
            result.fold(
                onSuccess = {
                    participantEmailInput = ""
                    // DataChangeNotifier should ideally trigger the flow to refresh participants.
                    // If not, a manual call to observeStreakDataForHistory() might be needed after a short delay,
                    // or a direct update to uiState.streak.participants if the repository returns the updated streak.
                    // For now, relying on DataChangeNotifier and the flow.
                    uiState = uiState.copy(isAddingParticipant = false, participantAddedSuccessfully = true)
                },
                onFailure = { exception ->
                    uiState = uiState.copy(
                        isAddingParticipant = false,
                        errorMessage = exception.message ?: "Failed to add participant."
                    )
                }
            )
        }
    }

    fun logTodayEntry() {
        val currentUserId = repository.getCurrentFirebaseUser()?.uid
        if (currentUserId == null) {
            uiState = uiState.copy(errorMessage = "User not logged in to log entry.")
            return
        }

        if (uiState.hasLoggedToday) {
            // This case should ideally be prevented by disabling the button in UI,
            // but good to have a safeguard.
            Log.w("StreakDetailVM", "Attempted to log entry when already logged for today.")
            uiState = uiState.copy(errorMessage = "You have already logged an entry for today.")
            return
        }

        uiState = uiState.copy(isLoggingEntry = true, errorMessage = null, entryLoggedSuccessfully = false)
        viewModelScope.launch {
            val result = repository.logStreakEntry(streakId, currentUserId, notes = null)
            result.fold(
                onSuccess = {
                    // The flow observer will pick up the new entry and update hasLoggedToday.
                    uiState = uiState.copy(isLoggingEntry = false, entryLoggedSuccessfully = true)
                },
                onFailure = { exception ->
                    uiState = uiState.copy(
                        isLoggingEntry = false,
                        errorMessage = exception.message ?: "Failed to log entry."
                    )
                }
            )
        }
    }

    fun removeEntryForDate(dateToRemove: LocalDate) {
        val currentUserId = repository.getCurrentFirebaseUser()?.uid
        if (currentUserId == null) {
            uiState = uiState.copy(errorMessage = "User not logged in to remove entry.")
            return
        }

        Log.d("StreakDetailVM", "Attempting to remove entry for user $currentUserId on date $dateToRemove for streak $streakId")

        uiState = uiState.copy(isRemovingEntry = true, errorMessage = null, entryRemovedSuccessfully = false)
        viewModelScope.launch {
            val result = repository.removeStreakEntryForDate(streakId, currentUserId, dateToRemove)
            result.fold(
                onSuccess = {
                    // The flow observer will pick up the change and update hasLoggedToday if today's entry was removed.
                    uiState = uiState.copy(isRemovingEntry = false, entryRemovedSuccessfully = true)
                    Log.d("StreakDetailVM", "Successfully initiated removal of entry for $dateToRemove.")
                },
                onFailure = { exception ->
                    uiState = uiState.copy(
                        isRemovingEntry = false,
                        errorMessage = exception.message ?: "Failed to remove entry."
                    )
                    Log.e("StreakDetailVM", "Failed to remove entry for $dateToRemove: ${exception.message}")
                }
            )
        }
    }

    fun clearErrorMessage() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun clearSuccessMessages() {
        uiState = uiState.copy(
            entryLoggedSuccessfully = false,
            participantAddedSuccessfully = false,
            entryRemovedSuccessfully = false // Clear this too
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("StreakDetailVM", "onCleared called for $streakId. Cancelling data processing job.")
        streakDataProcessingJob?.cancel()
    }

    companion object {
        fun provideFactory(
            repository: FirebaseRepository,
            streakId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StreakDetailViewModel::class.java)) {
                    return StreakDetailViewModel(repository, streakId) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}