package com.example.streakio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.ui.components.Sidebar // Ensure this import is correct
import com.example.streakio.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: FirebaseRepository,
    onLogout: () -> Unit,
    onCreateStreak: () -> Unit,
    onStreakClick: (String) -> Unit,
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(repository)
    )
) {
    val uiState = viewModel.uiState
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The MainViewModel's init block and Firestore listeners handle initial loads
                // and real-time updates.
                // refreshAllData() could be called here if you specifically want to
                // force a re-fetch of the user and re-initiate listeners on every resume.
                // However, with active Firestore listeners, this might be redundant unless
                // there's a specific scenario where data might get stale ONLY when the app is paused.
                // Consider if this explicit refresh is still needed.
                // For now, let's assume the listeners are sufficient.
                // If an explicit refresh on resume is desired:
                // if (viewModel.uiState.currentUser != null) {
                //     viewModel.refreshAllData()
                // }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Sidebar(
                user = uiState.currentUser,
                onLogout = {
                    viewModel.handleLogout(onLogout)
                },
                onCreateStreak = {
                    coroutineScope.launch { drawerState.close() }
                    onCreateStreak()
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Streaks") },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onCreateStreak) {
                    Icon(Icons.Default.Add, contentDescription = "Create New Streak")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Use the more specific isLoadingStreaks or a combination
                val isLoading = uiState.isLoadingUser || uiState.isLoadingStreaks
                if (isLoading && uiState.streaks.isEmpty()) { // Show loading if user or streaks are loading AND streaks list is empty
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.streaks.isEmpty() && !isLoading) { // Show empty state only if not loading
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (uiState.currentUser == null && !uiState.isLoadingUser) "Please log in to see your streaks."
                            else "You haven't joined or created any streaks yet. Tap the '+' button to start one!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.streaks, key = { it.id }) { streak ->
                            StreakItemCard(
                                streak = streak,
                                onClick = { onStreakClick(streak.id) }
                            )
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakItemCard(
    streak: com.example.streakio.model.Streak,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = streak.title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (streak.description.isNotBlank()) {
                Text(
                    text = streak.description.take(100) + if (streak.description.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
        }
    }
}
