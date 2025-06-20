package com.example.streakio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.viewmodel.CreateStreakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStreakScreen(
    repository: FirebaseRepository, // Pass repository for ViewModel factory
    onStreakCreated: () -> Unit,
    onBack: () -> Unit, // Add onBack for navigating back from TopAppBar
    viewModel: CreateStreakViewModel = viewModel(
        factory = CreateStreakViewModel.provideFactory(repository)
    )
) {
    val uiState = viewModel.uiState
    val focusManager = LocalFocusManager.current

    // Navigate back when streak is successfully created
    LaunchedEffect(uiState.isStreakCreated) {
        if (uiState.isStreakCreated) {
            onStreakCreated()
            viewModel.resetStreakCreatedFlag() // Reset flag after navigation
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Streak") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp)) // Adjust spacing after TopAppBar

            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChange(it) },
                label = { Text("Title*") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                isError = uiState.errorMessage?.contains("title", ignoreCase = true) == true,
                enabled = !uiState.isLoading
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("Description (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 100.dp), // Make it a bit taller
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (uiState.title.isNotBlank()) { // Only submit if title is not blank
                        viewModel.createStreak()
                    }
                }),
                enabled = !uiState.isLoading
            )
            Spacer(modifier = Modifier.height(20.dp))

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.createStreak()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.title.isNotBlank() && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Streak")
                }
            }
        }
    }
}