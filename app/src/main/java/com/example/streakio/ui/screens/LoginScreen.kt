package com.example.streakio.ui.screens

import android.util.Log
import android.util.Patterns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
// It's good practice to import getValue and setValue if you are using them directly,
// but for property delegates 'by', they are resolved implicitly.
// import androidx.compose.runtime.getValue
// import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.streakio.R
import com.example.streakio.repository.FirebaseRepository
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import kotlin.text.contains // Make sure this is used or remove
import kotlin.text.first
import kotlin.text.split


enum class MessageType {
    INFO,
    ERROR
}

object OneTimeMessageHandler {
    private var messageToShow: Pair<String, MessageType>? = null
    private var showResendButton: Boolean = false

    fun setMessage(message: String, type: MessageType, showResend: Boolean = false) {
        messageToShow = message to type
        showResendButton = showResend
    }

    fun consumeMessage(): Triple<Pair<String, MessageType>?, Boolean, String?>? {
        val msg = messageToShow
        val resend = showResendButton
        messageToShow = null
        showResendButton = false
        return if (msg != null) Triple(msg, resend, null) else null
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: FirebaseRepository,
    onLoginSuccess: () -> Unit,
    googleSignInLauncher: ActivityResultLauncher<IntentSenderRequest>
) {
    val coroutineScope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var showResendVerificationButton by rememberSaveable { mutableStateOf(false) }
    var emailForResend by rememberSaveable { mutableStateOf("") }


    var displayMessage by rememberSaveable(stateSaver = Saver(
        save = { it?.let { p -> "${p.first}||${p.second.name}" } },
        restore = {
            it?.split("||")?.let { parts ->
                if (parts.size == 2) Pair(parts[0], enumValueOf<MessageType>(parts[1])) else null
            }
        }
    )) { mutableStateOf<Pair<String, MessageType>?>(null) } // <-- FIXED HERE

    LaunchedEffect(Unit) {
        OneTimeMessageHandler.consumeMessage()?.let { (msgPair, showResend, _) ->
            if (msgPair != null) {
                displayMessage = msgPair
                showResendVerificationButton = showResend
            }
        }
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val webClientId = context.getString(R.string.web_client_id)

    fun isEmailValid(emailStr: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()
    }

    val isFormValid = email.isNotBlank() && password.isNotBlank() && isEmailValid(email)

    LaunchedEffect(isSignUp) {
        if (displayMessage?.first?.contains("Account created!", ignoreCase = true) == false &&
            displayMessage?.first?.contains("Please verify your email", ignoreCase = true) == false) {
            displayMessage = null
        }
        if (!showResendVerificationButton) {
            // This condition might need adjustment based on desired stickiness of the resend button
            // For now, if OneTimeMessageHandler set it, LaunchedEffect(Unit) will handle it.
            // If isSignUp changes and it wasn't a one-time message, it's okay to hide.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) "Create Account" else "Login",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (displayMessage?.second == MessageType.ERROR) {
                    displayMessage = null
                }
                if (showResendVerificationButton) showResendVerificationButton = false
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            isError = (displayMessage?.second == MessageType.ERROR && displayMessage?.first?.contains("email", ignoreCase = true) == true) ||
                    (email.isNotBlank() && !isEmailValid(email)),
            modifier = Modifier.fillMaxWidth()
        )
        if (email.isNotBlank() && !isEmailValid(email)) {
            Text(
                text = "Please enter a valid email address.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (displayMessage?.second == MessageType.ERROR) {
                    displayMessage = null
                }
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            isError = displayMessage?.second == MessageType.ERROR && displayMessage?.first?.contains("password", ignoreCase = true) == true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        displayMessage?.let { (text, type) ->
            Text(
                text = text,
                color = if (type == MessageType.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showResendVerificationButton && !isLoading) {
            Button(
                onClick = {
                    isLoading = true
                    displayMessage = null
                    coroutineScope.launch {
                        val result = repository.sendVerificationEmail()
                        isLoading = false
                        if (result.isSuccess) {
                            displayMessage = "A new verification email has been sent to $emailForResend. Please check your inbox." to MessageType.INFO
                            showResendVerificationButton = false
                        } else {
                            displayMessage = (result.exceptionOrNull()?.message ?: "Failed to resend verification email.") to MessageType.ERROR
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Resend Verification Email")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                isLoading = true
                displayMessage = null // Clear general messages before action
                // showResendVerificationButton will be set by OneTimeMessageHandler if needed after this action
                emailForResend = email

                coroutineScope.launch {
                    if (isSignUp) {
                        val result = repository.createUserWithEmail(email, password)
                        isLoading = false
                        if (result.isSuccess) {
                            OneTimeMessageHandler.setMessage(
                                "Account created! Please check your email ($emailForResend) to verify your account. Then, log in.",
                                MessageType.INFO,
                                showResend = false
                            )
                            isSignUp = false
                        } else {
                            displayMessage = (result.exceptionOrNull()?.message ?: "Sign up failed.") to MessageType.ERROR
                        }
                    } else { // LOGIN Logic
                        val loginResult = repository.signInWithEmail(email, password)
                        if (loginResult.isSuccess) {
                            val reloadedUser = repository.reloadCurrentUser()
                            isLoading = false
                            if (reloadedUser?.isEmailVerified == true) {
                                onLoginSuccess()
                            } else {
                                OneTimeMessageHandler.setMessage(
                                    "Please verify your email address ($emailForResend). Check your inbox or click 'Resend'.",
                                    MessageType.ERROR,
                                    showResend = true
                                )
                                repository.signOut()
                            }
                        } else {
                            isLoading = false
                            displayMessage = (loginResult.exceptionOrNull()?.message ?: "Login failed.") to MessageType.ERROR
                            if (loginResult.exceptionOrNull()?.message?.contains("user-not-found", ignoreCase = true) == true ||
                                loginResult.exceptionOrNull()?.message?.contains("wrong-password", ignoreCase = true) == true ||
                                loginResult.exceptionOrNull()?.message?.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) == true ) {
                                showResendVerificationButton = false // Explicitly hide for these cases
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isFormValid && !isLoading
        ) {
            Text(if (isSignUp) "Create Account & Verify" else "Login")
        }
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                isSignUp = !isSignUp
                displayMessage = null
                showResendVerificationButton = false
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSignUp) "Already have an account? Login" else "Don't have an account? Create Account")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Button( // Google Sign-In
            onClick = {
                focusManager.clearFocus()
                isLoading = true
                displayMessage = null
                showResendVerificationButton = false // Reset for Google Sign-In
                val signInRequest = BeginSignInRequest.builder()
                    .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                            .setSupported(true)
                            .setServerClientId(webClientId)
                            .setFilterByAuthorizedAccounts(false)
                            .build()
                    )
                    .setAutoSelectEnabled(false)
                    .build()

                val oneTapClient = Identity.getSignInClient(context)
                oneTapClient.beginSignIn(signInRequest)
                    .addOnSuccessListener { result ->
                        try {
                            val intentSenderRequest =
                                IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                            googleSignInLauncher.launch(intentSenderRequest)
                        } catch (e: Exception) {
                            isLoading = false
                            displayMessage = "Google Sign-In failed to start: ${e.localizedMessage}" to MessageType.ERROR
                            Log.e("LoginScreenGoogle", "Couldn't start One Tap UI: ${e.localizedMessage}")
                        }
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        displayMessage = "Google Sign-In failed: ${e.localizedMessage}" to MessageType.ERROR
                        Log.e("LoginScreenGoogle", "Google Sign-In beginSignIn failure: ${e.localizedMessage}")
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Sign in with Google")
        }
    }
}
