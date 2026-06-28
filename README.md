# REALO Guard — autopilot scam shield (Android)

Native Android app that runs the [REALO](https://realo.ai) anti-scam engine on **autopilot**.

## What it does
- Uses a `NotificationListenerService` to read incoming message text from **user-selected** apps
  (WhatsApp, Telegram, Messenger, Instagram, Signal, Gmail, etc.) — **no SMS** (Google already covers that).
- Sends the text to the REALO engine (`/api/scan`) and, if it's a scam, fires a high-priority warning.
- **Consent-first:** nothing is watched until the user (a) grants Notification Access and (b) opts each app in.
  Nothing is stored or shared; processing is for scam-check only.

## Build (no Android Studio needed)
Push to `main` → GitHub Actions builds a debug APK → download it from the run's **Artifacts**.

## Install (sideload)
Download `app-debug.apk` on the phone → install (allow unknown sources once) →
open REALO Guard → **Turn on protection** → pick apps. Done — it runs in the background.

## Stack
Kotlin · Jetpack Compose (Material 3) · Coroutines · OkHttp · NotificationListenerService
