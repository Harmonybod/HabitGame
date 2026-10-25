# HabitGame

An Android app that turns habits into a game: finish tasks and habits to earn XP and coins, level up through ranks, keep your streak, and spend coins in the Shop to unlock apps you've locked.

## Features

- **100-day challenge** board, with weekly XP, focus and savings graphs
- **Tasks** (one-time, repeating, location-based, shopping list with deadlines) and **habits** with reminders
- **Focus timer** with custom lengths, pause and Pomodoro breaks; keeps running in a bubble when you leave the app
- **App lock**: distracting apps stay locked until you spend coins to open them
- **Journal** with a song or album of the day, **Notes** (including notes shared from Samsung Notes) and **Savings**
- **Shop** with app unlocks, boosters, 25 themes, Glass, badges and icon packs
- **Sync** across phones with a Google account (Firebase), or by transfer code
- **Google Calendar** events for tasks and habits, and home-screen widgets

## Building

Requirements: Android Studio (or JDK 17 and Gradle 8.13), Android SDK 35.

```
gradle :app:assembleDebug
```

Two files are kept out of the repository:

- `app/google-services.json`: the Firebase config. Without it the app still builds; Google sign-in and cloud sync are simply off. To turn them on, create a Firebase project for `com.habitgame`, enable Google sign-in and Firestore, publish [`firestore.rules`](firestore.rules), and download the file into `app/`.
- `keystore/` and `keystore.properties`: the release signing key. Without them, release builds aren't signed.

## Themes

The 20 colour themes come from [`design/realtime_colors_20_themes.css`](design/realtime_colors_20_themes.css). After changing it, run `py design/generate_themes.py` to regenerate `RealtimeThemes.kt`.

## Tests

```
gradle :app:testDebugUnitTest
```
