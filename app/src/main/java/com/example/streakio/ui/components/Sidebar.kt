package com.example.streakio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.streakio.model.User

@Composable
fun Sidebar(
    user: User?, // This should now correctly be your custom User model
    onLogout: () -> Unit,
    onCreateStreak: () -> Unit,
    onCloseDrawer: () -> Unit // Added for better control
) {
    ModalDrawerSheet { // Or Column, or whatever root you have
        // ...
        user?.let {
            Text("Logged in as: ${it.displayName ?: it.email ?: "User"}")
            // Use properties from your custom User model (id, email, displayName)
        }
        // ...
        NavigationDrawerItem(
            label = { Text("Create New Streak") },
            selected = false,
            onClick = {
                onCreateStreak()
                // onCloseDrawer() // Call this if you want the drawer to close after clicking
            }
        )
        NavigationDrawerItem(
            label = { Text("Logout") },
            selected = false,
            onClick = {
                onLogout()
                // onCloseDrawer() // Call this if you want the drawer to close
            }
        )
    }
}
