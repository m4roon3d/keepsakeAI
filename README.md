# keepsake AI Android (Native Kotlin + Jetpack Compose)

An open-source, Android-native personal AI memory hub inspired by **OPPO AI Mind Space**, **AI Mind Pilot**, **AI Mind Suggest**, **AI Expense Tracking**, and **OSee** visual capture.

## Features Implemented

1. **Multimodal Quick Capture (`capture/`)**
   - **Global Share Sheet Receiver** (`MainActivity.kt`): Share text, URLs (articles, YouTube, TikTok), or screenshots/images from any app directly into MindSpace.
   - **Floating Snap Key Overlay** (`FloatingSnapService.kt`): System-wide floating trigger (`SYSTEM_ALERT_WINDOW`) to capture what you're doing at any time.
   - **Quick Settings Tile** (`QuickCaptureTileService.kt`): Pull down notification shade -> tap **Snap to MindSpace**.
   - **MediaProjection Screen Capture** (`ScreenCaptureService.kt`): Foreground service for capturing the current screen and running on-device OCR.
   - **On-Device OCR** (`OcrProcessor.kt`): Uses Google ML Kit Text Recognition to extract text from screenshots and physical receipts locally before AI processing.
   - **Voice Memo & Voice Expense Logging**: Uses Android `SpeechRecognizer` in-app.

2. **Smart Collections & Local Memory Hub (`data/local/`)**
   - **Room Database** (`MindSpaceDatabase.kt`, `Entities.kt`, `MindSpaceDao.kt`): Stores memories, auto-classified Smart Collections (`Travel`, `Study Notes`, `Events`, `Personal Projects`, `Receipts`, `Watch Later`), expenses, and daily suggestions.
   - **Zero-cloud lock-in**: All memories live locally in SQLite; AI calls are BYOK (Bring Your Own Key) stored in `EncryptedSharedPreferences`.

3. **AI Mind Pilot — Ask Mode & Agent Mode (`ai/MultiModelMindPilotClient.kt`, `ui/screens/MindPilotScreen.kt`)**
   - **Ask Mode (Multi-Model Side-by-Side)**: Query **Gemini**, **ChatGPT (OpenAI)**, and **Perplexity** simultaneously, grounded with your saved MindSpace memories as context.
   - **Agent Mode**: Multi-step task planner that turns your saved memories into structured itineraries, organized collections, or action plans.

4. **AI Expense Tracking (`ui/screens/ExpensesScreen.kt`)**
   - Extracts merchant, amount, currency, and category automatically from receipt OCR, payment screenshots, or voice logs, and renders a monthly category breakdown.

5. **AI Mind Suggest (`worker/DailyDigestWorker.kt`)**
   - Background `WorkManager` job that synthesizes recent memories, upcoming events, and pending tasks into a proactive daily digest card on the Home screen.

## Setup in Android Studio

1. Open the `keepsake_ai_android` folder in **Android Studio (Ladybug / Meerkat or newer)**.
2. Let Gradle sync (`compileSdk = 35`, `minSdk = 26`, Kotlin `2.0.21`).
3. Run on a physical Android device or emulator.
4. Open **Settings** inside the app to:
   - Add your **Gemini**, **OpenAI**, and/or **Perplexity** API keys (stored via AndroidX `EncryptedSharedPreferences`).
   - Grant **Display over other apps** permission if you want to enable the **Floating Snap Key**.
   - Add the **Snap to MindSpace** tile from your Android Quick Settings editor.

## Notes on Google Play & Privacy
- `MediaProjection` and `SYSTEM_ALERT_WINDOW` are declared with explicit user-triggered flows and foreground service types (`mediaProjection`).
- All on-device OCR runs locally via ML Kit before any optional cloud LLM call.
