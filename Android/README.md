# Reimagined Winner · Android native source

This is the full Java Android application. It does not load the Windows HTML or run Node.js.

## Build

- Android Studio (or Android Gradle Plugin 8.7.3)
- JDK 17
- Android SDK Platform 35

From this directory, run `gradlew.bat assembleDebug` on Windows or `./gradlew assembleDebug` on macOS/Linux. The first build may download Gradle and Android tooling. Debug signing is generated locally when the original workspace signing key is absent.

## Features

- Home, confirmed option signals, portfolio research, AI assistant, and settings screens.
- In-app environment editor. Values are encrypted with Android Keystore and remain in app-private storage.
- Portfolio CSV import and manual positions. Research uses actual completed daily Upstox candles and a mapped liquid constituent universe; unknown symbols or missing history do not receive an actionable recommendation.
- Daily Turtle Soup rule for portfolio research and a clearly labeled 20-bar intraday failed-breakout proxy for option signals. Scores are research ranks, not calibrated probabilities.
- Custom market turtle/candlestick launcher mark with adaptive and monochrome Android icon resources.
- No order placement. Trade validity and notifications operate while the app is active.

Do not add `.env`, API credentials, local signing keys, or generated APKs to source control. Import your own configuration after installing a build.
