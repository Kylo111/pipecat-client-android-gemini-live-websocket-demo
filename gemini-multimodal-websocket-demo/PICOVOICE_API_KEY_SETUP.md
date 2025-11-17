# Picovoice API Key Setup

## Overview

The app uses a **default Picovoice API key** that is embedded in the application. This allows all users to use wake word detection without needing to create their own Picovoice account.

However, **advanced users can override** this default key with their own custom key from Picovoice Console.

## For Developers: Setting the Default API Key

1. Get your Picovoice API key from https://console.picovoice.ai
2. Open `build.gradle.kts` in the `gemini-multimodal-websocket-demo` module
3. Find this line:
   ```kotlin
   buildConfigField("String", "DEFAULT_PICOVOICE_KEY", "\"YOUR_PICOVOICE_API_KEY_HERE\"")
   ```
4. Replace `YOUR_PICOVOICE_API_KEY_HERE` with your actual API key
5. Rebuild the project

Example:
```kotlin
buildConfigField("String", "DEFAULT_PICOVOICE_KEY", "\"abcd1234efgh5678ijkl9012mnop3456\"")
```

## For End Users: Using Custom API Key

Users can optionally set their own Picovoice API key in the app settings:

1. Open app settings
2. Navigate to Picovoice section
3. Click "Ustaw własny klucz" (Set custom key)
4. Enter your API key from https://console.picovoice.ai
5. Click "Zapisz" (Save)

### Benefits of Custom Key:
- Higher usage limits (if you have a paid Picovoice plan)
- Separate usage tracking
- Full control over your wake words

### Reverting to Default Key:
1. Open app settings
2. Navigate to Picovoice section
3. Click "Zmień klucz" (Change key)
4. Click "Przywróć domyślny" (Restore default)

## Key Priority

The app uses keys in this order:
1. **Custom key** (if user has set one in settings)
2. **Default key** (from BuildConfig)

## Security Notes

- The default key is embedded in the APK and can be extracted
- For production apps, consider using a backend service to provide keys
- Custom keys are stored in encrypted SharedPreferences
- Keys are never logged or exposed in error messages

## Free Plan Limits

Picovoice Free Plan includes:
- Unlimited wake word creations
- Up to 3 wake words active at once
- Suitable for personal use and testing

For production apps with many users, consider:
- Picovoice paid plans
- Backend key management
- Usage monitoring
