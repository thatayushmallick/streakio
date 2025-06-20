package com.example.streakio.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * A simple event bus to notify parts of the app about data changes.
 */
object DataChangeNotifier {
    // Application-level scope for the notifier.
    // SupervisorJob ensures that if one coroutine in this scope fails, others are not cancelled.
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    // Event for when any streak data might have changed (creation, update, deletion)
    // extraBufferCapacity = 1 can be useful to ensure the last event is buffered if there are no active collectors
    // replay = 0 means new subscribers don't get past events.
    private val _streakDataChangedEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val streakDataChangedEvent: SharedFlow<Unit> = _streakDataChangedEvent.asSharedFlow()

    /**
     * Notifies listeners that streak data has potentially changed.
     */
    fun notifyStreakDataChanged() {
        // Use tryEmit as it's non-suspending and suitable for SharedFlow from non-suspending contexts
        // or when you don't want to wait for subscribers.
        // If you were in a suspend function and wanted to ensure emission, you could use emit.
        val emitted = _streakDataChangedEvent.tryEmit(Unit)
        if (!emitted) {
            // Optional: Log if emission failed (e.g., buffer full and no collectors fast enough)
            // This is unlikely with extraBufferCapacity = 1 unless events are fired extremely rapidly
            // without collectors.
            println("DataChangeNotifier: Failed to emit streakDataChangedEvent")
        }
    }
}
