# Google Play Store Release Checklist (keepsake AI Android)

## 1. Build Signed AAB (Android App Bundle)
1. Generate your upload keystore:
   ```bash
   keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias keepsake_ai_release
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill in your credentials.
3. Build the minified, split-enabled release bundle:
   ```bash
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`

## 2. Google Play Console Declarations
- **App Access / Permissions**:
  - `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Declare that screen capture is strictly user-initiated via the in-app **Screen Snap** button or Quick Settings tile, with a visible foreground notification.
  - `SYSTEM_ALERT_WINDOW`: Optional feature toggled by the user in Settings for the floating Snap Key.
  - `CAMERA`: Used for the **OSee Visual Scanner** (document scan, receipt capture, menu translation).
  - `RECORD_AUDIO`: Used for user-initiated voice memos and voice expense logging.
- **Data Safety Form**:
  - Local memories and expenses are stored on-device in Room SQLite.
  - API keys are stored locally in `EncryptedSharedPreferences`.
  - If the user configures BYOK API keys, user-submitted prompts/OCR text are sent directly from the device to the selected provider (Google Gemini, OpenAI, or Perplexity).
