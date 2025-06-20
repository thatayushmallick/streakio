package com.example.streakio.ui.screens

import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn // Keep if needed for other parts
// import androidx.compose.foundation.lazy.items // Keep if needed for other parts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.streakio.model.StreakEntry // Keep if EntryCard is used as fallback
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.viewmodel.StreakDetailViewModel
// StreakDetailUiState is implicitly used via viewModel.uiState
import java.text.SimpleDateFormat
import java.time.LocalDate // Import LocalDate
import java.util.Locale
import com.example.streakio.ui.components.StreakHistoryTable // Import the new component

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakDetailScreen(
    repository: FirebaseRepository,
    streakId: String,
    onBack: () -> Unit,
    viewModel: StreakDetailViewModel = viewModel(
        factory = StreakDetailViewModel.provideFactory(repository, streakId)
    )
) {
    val uiState = viewModel.uiState
    val participantEmail = viewModel.participantEmailInput
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar for generic errors
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(
                message = uiState.errorMessage ?: "An unknown error occurred",
                duration = SnackbarDuration.Short
            )
            viewModel.clearErrorMessage()
        }
    }

    // Snackbar for entry logged successfully
    LaunchedEffect(uiState.entryLoggedSuccessfully) {
        if (uiState.entryLoggedSuccessfully) {
            snackbarHostState.showSnackbar(
                message = "Entry logged successfully!",
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessages()
        }
    }

    // Snackbar for participant added successfully
    LaunchedEffect(uiState.participantAddedSuccessfully) {
        if (uiState.participantAddedSuccessfully) {
            snackbarHostState.showSnackbar(
                message = "Participant added successfully!",
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessages()
        }
    }

    // Snackbar for entry removed successfully
    LaunchedEffect(uiState.entryRemovedSuccessfully) {
        if (uiState.entryRemovedSuccessfully) {
            snackbarHostState.showSnackbar(
                message = "Entry removed successfully!",
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.streak?.title ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (uiState.isLoadingStreak && uiState.streak == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.streak == null && !uiState.isLoadingStreak) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                    Text(
                        uiState.errorMessage ?: "Streak not found or an error occurred.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                uiState.streak?.let { streak ->
                    Text(streak.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(streak.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(24.dp))

                    // --- Add Participant Section ---
                    Text("Add Participant", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = participantEmail,
                        onValueChange = { viewModel.onParticipantEmailChange(it) },
                        label = { Text("Participant Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (participantEmail.isNotBlank()) viewModel.addParticipant()
                        }),
                        enabled = !uiState.isAddingParticipant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.addParticipant()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAddingParticipant && participantEmail.isNotBlank()
                    ) {
                        if (uiState.isAddingParticipant) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Add Participant")
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // --- Today's Action Section (Log / Remove Entry) ---
                    Text("Today's Action", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentUserId = repository.getCurrentFirebaseUser()?.uid
                    // Show buttons only if the user is a participant
                    if (streak.participants.contains(currentUserId)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp) // Spacing between buttons
                        ) {
                            // Log Today's Entry Button
                            Button(
                                onClick = { viewModel.logTodayEntry() },
                                modifier = Modifier.weight(1f), // Takes available space
                                enabled = !uiState.isLoggingEntry && !uiState.hasLoggedToday && !uiState.isRemovingEntry
                            ) {
                                if (uiState.isLoggingEntry) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Log Today's Entry")
                                }
                            }

                            // Remove Today's Entry Button (Conditional)
                            if (uiState.hasLoggedToday) {
                                Button(
                                    onClick = { viewModel.removeEntryForDate(LocalDate.now()) },
                                    modifier = Modifier.weight(1f), // Takes available space
                                    enabled = !uiState.isRemovingEntry && !uiState.isLoggingEntry,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    if (uiState.isRemovingEntry) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onError
                                        )
                                    } else {
                                        Text("Remove Today's Entry")
                                    }
                                }
                            }
                        }
                    } else if (currentUserId != null) { // User is logged in but not a participant
                        Text(
                            "You are not a participant in this streak, so you cannot log or remove entries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    // If currentUserId is null, the ViewModel will handle showing an error if actions are attempted,
                    // but the buttons won't even show due to the participant check.
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // --- Streak History Table ---
                StreakHistoryTable(
                    historyState = uiState.streakHistory,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Fallback or Simple List (Optional) ---
                /*
                Text("All Raw Entries (Debug/Simple List)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                // ... (rest of your optional list logic)
                */
            }
        }
    }
}

// EntryCard Composable (remains unchanged)
@Composable
fun EntryCard(entry: StreakEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "User: ${entry.userId.take(8)}...",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = entry.timestamp?.let { timestampDate ->
                    SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault()).format(timestampDate.toDate())
                } ?: "Timestamp not available",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
