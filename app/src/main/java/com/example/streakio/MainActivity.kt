package com.example.streakio

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts // Keep this for StartActivityForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.streakio.repository.FirebaseRepository
import com.example.streakio.ui.screens.CreateStreakScreen
import com.example.streakio.ui.screens.LoginScreen
import com.example.streakio.ui.screens.MainScreen
import com.example.streakio.ui.screens.StreakDetailScreen
import com.example.streakio.ui.theme.StreakioTheme
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    private val repository = FirebaseRepository()
    private lateinit var navController: NavHostController

    // This launcher is for starting the IntentSender from Google One Tap
    // and receiving its ActivityResult.
    // In MainActivity.kt / oneTapSignInLauncher

    private val oneTapSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                if (intent != null) {
                    try {
                        val credential = Identity.getSignInClient(this).getSignInCredentialFromIntent(intent)
                        val googleIdToken = credential.googleIdToken
                        if (googleIdToken != null) {
                            lifecycleScope.launch {
                                // The key is that after this signInWithGoogle,
                                // the AuthStateListener in the repository's getAuthStateFlow()
                                // should fire, which will update `currentUser` in AppContent,
                                // triggering the LaunchedEffect for navigation.
                                repository.signInWithGoogle(googleIdToken)
                                    .onSuccess { firebaseUser -> // Changed 'it' to 'firebaseUser' for clarity
                                        Log.d("MainActivity", "Google Sign-In Successful, user: ${firebaseUser.displayName}")
                                        // Navigation is now primarily handled by the reactive currentUser state
                                        // in AppContent. No explicit navigation call needed here.
                                    }
                                    .onFailure { e ->
                                        Log.e("MainActivity", "Firebase Google Auth Failed", e)
                                        Toast.makeText(this@MainActivity, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        } else {
                            Log.e("MainActivity", "Google ID token is null")
                            Toast.makeText(this@MainActivity, "Google Sign-In failed: No ID token.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Google Sign-In failed to get credential", e)
                        Toast.makeText(this@MainActivity, "Google Sign-In error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("MainActivity", "Google Sign-In intent is null from result")
                    Toast.makeText(this@MainActivity, "Google Sign-In failed: No data returned.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w("MainActivity", "Google Sign-In cancelled or failed by One Tap UI. ResultCode: ${result.resultCode}")
                if (result.resultCode != Activity.RESULT_CANCELED) {
                    Toast.makeText(this@MainActivity, "Google Sign-In attempt failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreakioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    navController = rememberNavController()
                    // Pass the oneTapSignInLauncher to AppContent (and thus to LoginScreen)
                    AppContent(navController, repository, oneTapSignInLauncher)
                }
            }
        }
    }
}

// In MainActivity.kt

@Composable
fun AppContent(
    navController: NavHostController,
    repository: FirebaseRepository,
    oneTapSignInLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
) {
    // Observe currentUser from the repository's auth state Flow
    val currentUser by repository.getAuthStateFlow().collectAsState(initial = repository.getCurrentFirebaseUser())

    // Determine startDestination based on the observed currentUser
    // This LaunchedEffect will re-evaluate if currentUser changes
    val startDestination = remember(currentUser) {
        if (currentUser != null) "main" else "login"
    }

    // This LaunchedEffect handles navigation based on currentUser changes.
    LaunchedEffect(currentUser, navController) { // Key on navController as well if its graph can change
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentUser != null) {
            // User is logged in
            if (currentRoute == "login" || currentRoute == null /* Initial case */) {
                navController.navigate("main") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
            // Add other conditions if needed, e.g., if on a specific screen and auth changes, navigate appropriately.
        } else {
            // User is logged out
            if (currentRoute != "login") {
                navController.navigate("login") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // It's important that NavHost's startDestination is stable or correctly re-evaluated
    // if it can change *after* initial composition.
    // The NavHost composable itself might not recompose just because startDestination string changes
    // if the NavHost is already part of the composition.
    // However, the LaunchedEffect above should handle the navigation correctly.
    // To be super safe, you could make the NavHost key on the startDestination if it truly
    // needs to re-initialize with a different graph starting point. But typically,
    // navigating explicitly is the more common pattern.

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
                    // This callback might not be strictly necessary anymore
                    // if LaunchedEffect handles navigation based on currentUser.
                    // However, it doesn't hurt.
                    Log.d("AppContent", "LoginScreen onLoginSuccess called.")
                    // Consider if immediate navigation is needed here, or trust LaunchedEffect.
                    // If you navigate here, ensure it doesn't conflict with LaunchedEffect.
                    // For now, let LaunchedEffect handle it.
                },
                googleSignInLauncher = oneTapSignInLauncher
            )
        }
        composable("main") {
            // Ensure currentUser is not null here, or handle gracefully if it could be
            // (though LaunchedEffect should prevent reaching here if null)
            currentUser?.let { user -> // Or pass user details to MainScreen if needed
                MainScreen(
                    repository = repository,
                    onLogout = {
                        repository.signOut() // Auth state change will trigger LaunchedEffect
                    },
                    onCreateStreak = { navController.navigate("createStreak") },
                    onStreakClick = { streakId -> navController.navigate("streakDetail/$streakId") }
                )
            } ?: run {
                // Fallback or loading if currentUser is unexpectedly null
                // This state should ideally be prevented by the LaunchedEffect navigating to login.
                Log.w("AppContent", "MainScreen composed with null currentUser, redirecting to login.")
                // Potentially navigate to login again if this state is reached.
                // However, the LaunchedEffect should handle this.
            }
        }
        composable("createStreak") {
            CreateStreakScreen(
                repository = repository,
                onStreakCreated = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable("streakDetail/{streakId}") { backStackEntry ->
            val streakId = backStackEntry.arguments?.getString("streakId") ?: ""
            StreakDetailScreen(
                repository = repository,
                streakId = streakId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}