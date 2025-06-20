# Streakio - Android Streaks Tracking App

Streakio is an Android application built with Kotlin and Jetpack Compose, designed to help users track their streaks for various habits or activities. It utilizes Firebase for backend services including authentication and real-time data storage with Firestore.

## Features

- **User Authentication:** Secure email/password and Google Sign-In options via Firebase Authentication.
- **Streak Creation & Management:** Users can create new streaks, providing a name and description.
- **Streak Participation:** Users can invite others (via email) to participate in shared streaks.
- **Daily Logging:** Participants can log their daily progress for each streak.
- **Real-time Updates:** Data syncs in real-time across all participant devices using Firestore snapshot listeners.
- **Streak History View:** Users can view a calendar-like history of their logged entries for a streak.
- **Modern UI:** Clean and intuitive user interface built with Jetpack Compose and Material 3 design principles.

## Tech Stack

- **Programming Language:** Kotlin
- **UI Framework:** Jetpack Compose (with Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **Asynchronous Programming:** Kotlin Coroutines & Flow
- **Backend:** Firebase
  - **Authentication:** Firebase Authentication (Email/Password, Google Sign-In)
  - **Database:** Cloud Firestore (for real-time data storage)
- **Navigation:** Jetpack Navigation Compose
- **Dependency Management:** Gradle with Kotlin DSL, Version Catalogs

## Downloads

You can download the latest installable APK by [**clicking here**](https://github.com/thatayushmallick/streakio/releases/download/v0.3.1-beta/Streakio-beta-0.3.1.apk), or check out other versions from the [Releases](https://github.com/YOUR_USERNAME/YOUR_REPOSITORY_NAME/releases) page.

_(Note: You might need to enable "Install from Unknown Sources" in your Android settings to install APKs directly.)_

---

> Developed by [Ayush Mallick](https://ayushmallick.vercel.app)
