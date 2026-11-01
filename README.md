# SortedApp

A free, ad-free Android app drawer that automatically sorts every installed app into categories. No network access, no accounts, nothing leaves your phone.

## How apps get sorted

1. Your own choice (long-press an app to move it, or reset it to automatic)
2. The category the app developer declared to Android
3. Keyword rules in [`Categorizer.kt`](app/src/main/java/com/benclift/sortedapp/Categorizer.kt), matched against the app name and package name
4. Otherwise "Other"

To teach it about an app, add a keyword to the right list in `Categorizer.kt`.

## Install

Every push to `main` builds the app with GitHub Actions and publishes a numbered release
(1.0.N, where N is the build number) to the [Releases](../../releases) page. The newest build is always at
https://github.com/ben-clift/SortedApp/releases/latest/download/SortedApp.apk

Download `SortedApp.apk` on your phone and open it. Android will ask you to allow installs from your
browser the first time. The installed version shows under Settings > Apps > SortedApp.

**Installing over an older version:** Android only allows this if both were signed with the same key.
Add the repo secrets `KEYSTORE_B64` (a base64-encoded keystore with alias `sorted`) and `KEYSTORE_PASSWORD`
and every build is signed with it. Without them each build gets a fresh debug key, so uninstall the old
version first.

To use it as your home screen: Settings > Apps > Default apps > Home app > SortedApp.

## Build locally

Open the folder in Android Studio, or run `gradle assembleDebug` (Gradle 8.10+, JDK 17).
