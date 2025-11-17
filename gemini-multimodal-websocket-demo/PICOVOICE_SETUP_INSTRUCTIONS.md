# Picovoice Wake Word Setup Instructions

This document provides step-by-step instructions for setting up the Picovoice wake word detection feature.

## Prerequisites

1. A Picovoice account (free tier available)
2. Access to [Picovoice Console](https://console.picovoice.ai)

## Step 1: Create Picovoice Account

1. Go to https://console.picovoice.ai
2. Click "Sign Up" or "Get Started"
3. Create a free account using email or social login
4. Verify your email if required

## Step 2: Get Access Key

1. Log in to Picovoice Console
2. Navigate to "Access Keys" in the left sidebar
3. Copy your access key (you'll need this in the app settings)
4. Keep this key secure - it's required for the app to work

## Step 3: Create System Wake Words

You need to create three system wake words in Polish language:

### Wake Word 1: "start"

1. In Picovoice Console, go to "Porcupine" → "Wake Words"
2. Click "Create Wake Word"
3. Enter phrase: **start**
4. Select language: **Polski (pl)**
5. Click "Train" (takes ~10 seconds)
6. Once trained, click "Download"
7. Select platform: **Android**
8. Download the .ppn file
9. Rename it to: `start_pl.ppn`
10. Place it in: `gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/`

### Wake Word 2: "stop"

1. Click "Create Wake Word" again
2. Enter phrase: **stop**
3. Select language: **Polski (pl)**
4. Click "Train"
5. Download for **Android**
6. Rename to: `stop_pl.ppn`
7. Place in: `gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/`

### Wake Word 3: "koniec"

1. Click "Create Wake Word" again
2. Enter phrase: **koniec**
3. Select language: **Polski (pl)**
4. Click "Train"
5. Download for **Android**
6. Rename to: `koniec_pl.ppn`
7. Place in: `gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/`

## Step 4: Create Activation Sound Files

You need two MP3 sound files for audio feedback:

### system_activation.mp3

**Purpose**: Quick feedback for system commands (start/stop/koniec)

**Specifications**:
- Duration: 200-300ms
- Type: Short beep or click
- Suggested: Single tone at 800Hz

**How to create**:
1. Use online tone generator: https://www.szynalski.com/tone-generator/
2. Set frequency to 800Hz
3. Generate and download as MP3
4. Trim to 200-300ms using audio editor
5. Save as: `system_activation.mp3`
6. Place in: `gemini-multimodal-websocket-demo/src/main/res/raw/`

### custom_activation.mp3

**Purpose**: Indicates app is launching and listening for custom wake words

**Specifications**:
- Duration: 500-800ms
- Type: Longer ascending tone or two-tone chime
- Suggested: Ascending sweep from 600Hz to 1200Hz

**How to create**:
1. Use audio editing software (Audacity, GarageBand, etc.)
2. Create an ascending tone sweep or two-tone chime
3. Export as MP3
4. Save as: `custom_activation.mp3`
5. Place in: `gemini-multimodal-websocket-demo/src/main/res/raw/`

**Alternative**: Find royalty-free notification sounds online

## Step 5: Configure the App

1. Build and install the app on your device
2. Open the app and go to Settings
3. Scroll to "Picovoice Voice Commands" section
4. Enter your Picovoice Access Key
5. Toggle "Enable Wake Word Detection" to ON
6. The app will start the background service

## Step 6: Test System Wake Words

1. With the app in foreground and a conversation active:
   - Say "start" or "stop" → Should toggle microphone
   - Say "koniec" → Should exit the app

2. Test from background:
   - Close the app
   - Say any system wake word
   - Check if the service is responding (check logs)

## Step 7: Create Custom Wake Words (Optional)

Users can create their own wake words for launching specific conversations:

1. In app settings, click "Add Wake Word"
2. Enter a name (e.g., "asystent")
3. Follow the in-app instructions to create it in Picovoice Console
4. Download the .ppn file
5. Import it using the file picker in the app
6. Assign it to a conversation thread in Thread Settings

## Tips for Good Wake Words

- Use multi-syllable words (2-3 syllables work best)
- Avoid common words used in everyday conversation
- Choose words with unique pronunciation
- Test in Picovoice Console before importing
- Avoid words that sound similar to each other

## Troubleshooting

### Wake words not detected
- Check microphone permission is granted
- Verify access key is correct
- Ensure .ppn files are in the correct directories
- Check that Picovoice is enabled in settings
- Review app logs for errors

### Service not starting
- Check RECORD_AUDIO permission
- Verify FOREGROUND_SERVICE permission
- Check battery optimization settings
- Review logcat for initialization errors

### Invalid access key error
- Verify you copied the complete access key
- Check for extra spaces or characters
- Generate a new access key in Picovoice Console

### .ppn file import fails
- Ensure file has .ppn extension
- Verify file is for Android platform
- Check file is not corrupted
- Try re-downloading from Picovoice Console

## File Structure Summary

```
gemini-multimodal-websocket-demo/
├── src/main/
│   ├── assets/
│   │   └── picovoice/
│   │       └── system/
│   │           ├── start_pl.ppn          ← Create this
│   │           ├── stop_pl.ppn           ← Create this
│   │           └── koniec_pl.ppn         ← Create this
│   └── res/
│       └── raw/
│           ├── system_activation.mp3     ← Create this
│           └── custom_activation.mp3     ← Create this
```

## Additional Resources

- [Picovoice Console](https://console.picovoice.ai)
- [Porcupine Documentation](https://picovoice.ai/docs/porcupine/)
- [Wake Word Best Practices](https://picovoice.ai/blog/wake-word-best-practices/)
- [Tone Generator](https://www.szynalski.com/tone-generator/)
- [Audacity Audio Editor](https://www.audacityteam.org/)

## Support

For issues with:
- Picovoice service: Contact Picovoice support
- App functionality: Check app logs and GitHub issues
- Wake word creation: Refer to Picovoice documentation
