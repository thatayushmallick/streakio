package com.example.streakio.repository

import android.util.Log
// import androidx.compose.ui.geometry.isEmpty // Not used directly, can be removed if not needed elsewhere
import com.example.streakio.model.Streak
import com.example.streakio.model.StreakEntry
import com.example.streakio.model.User // Your User model
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions // For merging data
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // --- User Persistence in Firestore ---
    private suspend fun saveUserToFirestore(firebaseUser: FirebaseUser) {
        val uid = firebaseUser.uid
        val email = firebaseUser.email
        val displayName = firebaseUser.displayName

        val userToSave = User(
            id = uid,
            email = email ?: "",
            displayName = displayName ?: email?.substringBefore('@') ?: "User-${uid.take(4)}"
        )

        try {
            db.collection("users").document(uid)
                .set(userToSave, SetOptions.merge())
                .await()
            Log.d("FirebaseRepository", "User data saved to Firestore for UID: $uid")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error saving user data to Firestore for UID: $uid", e)
        }
    }

    suspend fun getUserById(userId: String): Result<User?> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            Log.w("FirebaseRepository", "getUserById called with blank userId.")
            return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank."))
        }
        try {
            Log.d("FirebaseRepository", "Fetching user by ID: $userId")
            val documentSnapshot = db.collection("users").document(userId).get().await()

            if (documentSnapshot.exists()) {
                val user = documentSnapshot.toObject(User::class.java)
                if (user != null) {
                    // It's good practice to ensure the ID from the document is set in the object,
                    // especially if your User data class's ID field might not be set by toObject()
                    // if it's not explicitly in the Firestore document fields (though it usually is if ID is a property).
                    // However, if 'id' is the document ID and a constructor parameter, toObject typically handles it.
                    // If your User class's id field is primarily derived from the documentId and not a stored field:
                    // val userWithId = user.copy(id = documentSnapshot.id)
                    // Result.success(userWithId)
                    // If 'id' IS a field in your Firestore 'users' documents that matches documentSnapshot.id:
                    Log.d("FirebaseRepository", "User found for ID $userId: $user")
                    Result.success(user)
                } else {
                    Log.e("FirebaseRepository", "User data for $userId was null after toObject conversion.")
                    Result.failure(Exception("Failed to parse user data for ID: $userId"))
                }
            } else {
                Log.d("FirebaseRepository", "No user found with ID: $userId")
                Result.success(null) // User not found, but not an error in fetching itself
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching user by ID $userId", e)
            Result.failure(e)
        }
    }

    // --- Authentication Functions ---
    suspend fun signInWithEmail(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                saveUserToFirestore(firebaseUser)
                Result.success(User(firebaseUser.uid, firebaseUser.email ?: "", firebaseUser.displayName))
            } else {
                Result.failure(Exception("Sign-in failed: Firebase user was null."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "signInWithEmail failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                saveUserToFirestore(firebaseUser)
                Result.success(User(firebaseUser.uid, firebaseUser.email ?: "", firebaseUser.displayName))
            } else {
                Result.failure(Exception("Google sign-in failed: Firebase user was null."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "signInWithGoogle failed", e)
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmail(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                saveUserToFirestore(firebaseUser)
                firebaseUser.sendEmailVerification().await()
                Result.success(firebaseUser)
            } else {
                Result.failure(Exception("Firebase user was null after creation."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error creating user", e)
            Result.failure(e)
        }
    }

    suspend fun sendVerificationEmail(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user != null) {
            try {
                user.sendEmailVerification().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "Error sending verification email", e)
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("No user logged in to send verification email."))
        }
    }

    suspend fun reloadCurrentUser(): FirebaseUser? = withContext(Dispatchers.IO) {
        try {
            auth.currentUser?.reload()?.await()
            auth.currentUser
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error reloading user", e)
            null
        }
    }

    fun isCurrentUserEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified ?: false
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentCustomUser(): User? {
        val firebaseUser = auth.currentUser
        return firebaseUser?.let { User(it.uid, it.email ?: "", it.displayName) }
    }

    fun getAuthStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser).isSuccess
        }
        auth.addAuthStateListener(authStateListener)
        awaitClose { auth.removeAuthStateListener(authStateListener) }
    }

    fun getCurrentFirebaseUser(): FirebaseUser? {
        return auth.currentUser
    }

    // --- Streak Functions ---

    // Create streak remains a suspend function as it's a one-time write operation.
    suspend fun createStreak(streak: Streak): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentUserUid = auth.currentUser?.uid
            if (currentUserUid == null) {
                return@withContext Result.failure(Exception("User not logged in to create streak."))
            }
            // Ensure creatorId and participants are set if not already handled by the caller
            val streakToCreate = streak.copy(
                creatorId = streak.creatorId.ifEmpty { currentUserUid },
                participants = if (streak.participants.isEmpty()) listOf(currentUserUid) else streak.participants.distinct()
            )

            val docRef = db.collection("streaks").add(streakToCreate).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error creating streak", e)
            Result.failure(e)
        }
    }

    // Get specific streak by ID (one-time fetch)
    suspend fun getStreakById(streakId: String): Result<Streak?> = withContext(Dispatchers.IO) {
        try {
            val documentSnapshot = db.collection("streaks").document(streakId).get().await()
            if (documentSnapshot.exists()) {
                val streak = documentSnapshot.toObject(Streak::class.java)?.copy(id = documentSnapshot.id)
                Result.success(streak)
            } else {
                Result.success(null) // Streak not found
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching streak by ID $streakId", e)
            Result.failure(e)
        }
    }

    // NEW: Flow-based method to get a specific streak by ID with real-time updates
    fun getStreakByIdFlow(streakId: String): Flow<Result<Streak?>> = callbackFlow {
        Log.d("FirebaseRepository", "getStreakByIdFlow called for $streakId")
        val docRef = db.collection("streaks").document(streakId)
        val listenerRegistration: ListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirebaseRepository", "Listen failed for streak $streakId", error)
                trySend(Result.failure(error)).isSuccess
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val streak = snapshot.toObject(Streak::class.java)?.copy(id = snapshot.id)
                Log.d("FirebaseRepository", "Streak $streakId data received: $streak")
                trySend(Result.success(streak)).isSuccess
            } else {
                Log.d("FirebaseRepository", "Streak $streakId not found or null snapshot")
                trySend(Result.success(null)).isSuccess // Streak not found or deleted
            }
        }
        awaitClose {
            Log.d("FirebaseRepository", "Closing listener for streak $streakId")
            listenerRegistration.remove()
        }
    }


    // Get streaks for a user (one-time fetch) - keeping for potential use cases
    // where a one-time load is explicitly needed.
    suspend fun getStreaksForUser(userId: String): Result<List<Streak>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("streaks")
                .whereArrayContains("participants", userId)
                // Add orderBy if you have a consistent field like 'createdAt' or 'lastUpdated'
                // .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val streaks = snapshot.documents.mapNotNull { document ->
                document.toObject(Streak::class.java)?.copy(id = document.id)
            }
            Result.success(streaks)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching streaks for user $userId", e)
            Result.failure(e)
        }
    }

    // NEW: Flow-based method to get streaks for a user with real-time updates
    fun getStreaksForUserFlow(userId: String): Flow<Result<List<Streak>>> = callbackFlow {
        Log.d("FirebaseRepository", "getStreaksForUserFlow called for $userId")
        val query = db.collection("streaks")
            .whereArrayContains("participants", userId)
        // Consider adding an orderBy clause if you need consistent ordering,
        // e.g., by creation time or last update. This might require a composite index.
        // .orderBy("createdAt", Query.Direction.DESCENDING) // Example

        val listenerRegistration: ListenerRegistration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e("FirebaseRepository", "Listen failed for user streaks $userId", error)
                trySend(Result.failure(error)).isSuccess
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val streaks = snapshots.documents.mapNotNull { document ->
                    document.toObject(Streak::class.java)?.copy(id = document.id)
                }
                Log.d("FirebaseRepository", "User $userId streaks data received: ${streaks.size} streaks")
                trySend(Result.success(streaks)).isSuccess
            } else {
                // This case might not occur often with snapshot listeners unless there's an issue.
                // It's often better to send an empty list if snapshots is null but no error.
                Log.d("FirebaseRepository", "User $userId streaks snapshots were null without error")
                trySend(Result.success(emptyList())).isSuccess
            }
        }
        awaitClose {
            Log.d("FirebaseRepository", "Closing listener for user $userId streaks")
            listenerRegistration.remove()
        }
    }


    // Add participant remains a suspend function as it's a one-time write operation.
    // The real-time listeners on the streak details will pick up the change.
    suspend fun addParticipantToStreak(streakId: String, emailToAdd: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserEmail = auth.currentUser?.email
            if (currentUserEmail == emailToAdd) {
                return@withContext Result.failure(Exception("You cannot add yourself again."))
            }

            val userQuerySnapshot = db.collection("users")
                .whereEqualTo("email", emailToAdd.trim()) // Trim email
                .limit(1)
                .get()
                .await()

            if (userQuerySnapshot.isEmpty) {
                return@withContext Result.failure(Exception("User with email '$emailToAdd' not found."))
            }

            val userIdToAdd = userQuerySnapshot.documents.first().id

            val streakDocRef = db.collection("streaks").document(streakId)
            val streakDoc = streakDocRef.get().await()
            val streak = streakDoc.toObject(Streak::class.java)

            if (streak?.participants?.contains(userIdToAdd) == true) {
                return@withContext Result.failure(Exception("User '$emailToAdd' is already a participant."))
            }

            streakDocRef.update("participants", FieldValue.arrayUnion(userIdToAdd)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error adding participant to streak $streakId", e)
            Result.failure(e)
        }
    }

    // Log streak entry remains a suspend function (one-time write).
    // Real-time listeners on streak entries will pick up the new entry.
    suspend fun logStreakEntry(streakId: String, userId: String, notes: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Ensure StreakEntry has a @ServerTimestamp annotated field for 'timestamp'
            // and that it's nullable or has a default value if not set here.
            // If `timestamp` is meant to be set by the server, it should be `null` here.
            val newEntry = StreakEntry(
                streakId = streakId,
                userId = userId,
                notes = notes,
                timestamp = null // Firestore populates this if @ServerTimestamp is used in StreakEntry model
            )
            val docRef = db.collection("streak_entries").add(newEntry).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error logging streak entry for streak $streakId", e)
            Result.failure(e)
        }
    }

    // Get streak entries (one-time fetch) - keeping for potential use cases.
    suspend fun getStreakEntries(streakId: String): Result<List<StreakEntry>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("streak_entries")
                .whereEqualTo("streakId", streakId)
                .orderBy("timestamp", Query.Direction.DESCENDING) // Common to order entries
                .get()
                .await()
            val entries = snapshot.documents.mapNotNull { document ->
                document.toObject(StreakEntry::class.java)?.copy(id = document.id)
            }
            Result.success(entries)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching streak entries for $streakId", e)
            Result.failure(e)
        }
    }

    // NEW: Flow-based method to get streak entries with real-time updates
    fun getStreakEntriesFlow(streakId: String): Flow<Result<List<StreakEntry>>> = callbackFlow {
        Log.d("FirebaseRepository", "getStreakEntriesFlow called for $streakId")
        val query = db.collection("streak_entries")
            .whereEqualTo("streakId", streakId)
            .orderBy("timestamp", Query.Direction.DESCENDING) // Ensure you have an index for this

        val listenerRegistration: ListenerRegistration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e("FirebaseRepository", "Listen failed for streak entries $streakId", error)
                trySend(Result.failure(error)).isSuccess
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val entries = snapshots.documents.mapNotNull { document ->
                    document.toObject(StreakEntry::class.java)?.copy(id = document.id)
                }
                Log.d("FirebaseRepository", "Streak $streakId entries data received: ${entries.size} entries")
                trySend(Result.success(entries)).isSuccess
            } else {
                Log.d("FirebaseRepository", "Streak $streakId entries snapshots were null without error")
                trySend(Result.success(emptyList())).isSuccess
            }
        }
        awaitClose {
            Log.d("FirebaseRepository", "Closing listener for streak $streakId entries")
            listenerRegistration.remove()
        }
    }

    suspend fun removeStreakEntryForDate(streakId: String, userId: String, date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        if (streakId.isBlank() || userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Streak ID and User ID cannot be blank."))
        }
        try {
            Log.d("FirebaseRepo", "Attempting to remove entry for streak: $streakId, user: $userId, date: $date")

            // Define the start and end of the day for the given LocalDate
            // Firestore Timestamps are usually UTC. Ensure your date logic is consistent.
            val startOfDayTimestamp = com.google.firebase.Timestamp(date.atStartOfDay().toEpochSecond(ZoneOffset.UTC), 0)
            val endOfDayTimestamp = com.google.firebase.Timestamp(date.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) -1 , 999_999_999) // End of the day

            Log.d("FirebaseRepo", "Querying entries between $startOfDayTimestamp and $endOfDayTimestamp")


            val entriesQuery = db.collection("streak_entries") // <--- Query the top-level collection
                .whereEqualTo("streakId", streakId)        // Filter by streakId field in the entry
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", startOfDayTimestamp)
                .whereLessThanOrEqualTo("timestamp", endOfDayTimestamp)
                .get()
                .await()

            if (entriesQuery.isEmpty) {
                Log.d("FirebaseRepo", "No entry found for user $userId on $date in streak $streakId to remove.")
                // It's not an error if no entry was found to delete.
                // Could also be Result.failure(Exception("No entry found to remove.")) if you prefer to signal this.
                return@withContext Result.success(Unit)
            }

            val batch: WriteBatch = db.batch()
            var deletedCount = 0
            for (document in entriesQuery.documents) {
                Log.d("FirebaseRepo", "Deleting entry document: ${document.id}")
                batch.delete(document.reference)
                deletedCount++
            }
            batch.commit().await()
            Log.d("FirebaseRepo", "Successfully deleted $deletedCount entries for user $userId on $date in streak $streakId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error removing streak entry for date $date: ${e.message}", e)
            Result.failure(e)
        }
    }
}
