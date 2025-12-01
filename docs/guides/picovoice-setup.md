# Picovoice Wake Word Setup Guide

**Source Documents:**
- PICOVOICE_WAKE_WORD_SETUP_GUIDE.md
- PICOVOICE_QUICK_START.md
- PICOVOICE_TROUBLESHOOTING.md

**Last Updated:** 2025-12-01

---

## Overview

Picovoice provides wake word detection for hands-free control of the app. The app comes with a built-in "ALEXA" wake word and supports custom wake words.

---

## Quick Start

### Using Built-In Wake Word

The app includes the "ALEXA" wake word by default - no setup required!

1. **Enable Picovoice** in app settings
2. You'll see notification: "Picovoice aktywny - Nasłuchiwanie 1 komend głosowych"
3. Say **"Alexa"** to pause/resume session (toggle microphone)

**That's it!** No need to create `.ppn` files or configure anything.

---

## Custom Wake Words (Advanced)

If you want to use Polish wake words like "start" and "stop" instead of "Alexa":

### Step 1: Create Picovoice Account

1. Go to: https://console.picovoice.ai
2. Register or login
3. Copy your Access Key (you'll need it later)

### Step 2: Create Wake Words

You need to create 3 wake words in Polish:

#### A. Word "start"
1. In Picovoice Console go to: **Porcupine** → **Wake Words**
2. Click **"Create Wake Word"**
3. Enter: `start`
4. Select language: **Polski (pl)**
5. Click **"Train"** (~10 seconds)
6. After training, click **"Download"**
7. Select platform: **Android**
8. Download the `.ppn` file
9. Rename to: `start_pl.ppn`

#### B. Word "stop"
1. Repeat steps above
2. Enter: `stop`
3. Select language: **Polski (pl)**
4. Download for **Android**
5. Rename to: `stop_pl.ppn`

#### C. Word "koniec"
1. Repeat steps above
2. Enter: `koniec`
3. Select language: **Polski (pl)**
4. Download for **Android**
5. Rename to: `koniec_pl.ppn`

### Step 3: Add Files to Project

Copy all 3 `.ppn` files to:
```
gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/
```

Directory structure should look like:
```
gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/
├── README.md
├── start_pl.ppn
├── stop_pl.ppn
└── koniec_pl.ppn
```

### Step 4: Rebuild and Install

```bash
./gradlew clean build
./gradlew installDebug
```

### Step 5: Enable in App

1. Open app
2. Go to **Settings**
3. Find **Picovoice** section
4. Enable **"Enable Picovoice"**
5. (Optional) Enter your Access Key

### Step 6: Test

After enabling, you should see notification:
```
Picovoice aktywny
Nasłuchiwanie 3 komend głosowych
```

Now you can use:
- **"start"** or **"stop"** - toggle microphone during conversation
- **"koniec"** - close app

---

## Troubleshooting

### Problem: "Alexa" Not Working

#### Step 1: Check if Picovoice is Enabled

1. Open app
2. Go to **Settings**
3. Find **"Picovoice Wake Word Detection"**
4. Ensure toggle is **ON**

#### Step 2: Check Notification

You should see:
```
Picovoice aktywny
Nasłuchiwanie 1 komend głosowych
```

If not, Picovoice is not running.

#### Step 3: Check Permissions

App needs microphone permission:
1. Go to phone settings
2. Apps → Gemini Multimodal Demo
3. Permissions → Microphone
4. Set to **"Allow always"** or **"Allow only while using app"**

#### Step 4: Check Logs (Developers)

```bash
# Clear logs
adb logcat -c

# Monitor Picovoice logs
adb logcat | grep -i "picovoice\|porcupine\|alexa"
```

You should see:
```
Using built-in wake word: ALEXA (toggle mic)
Loaded built-in system wake word: alexa
Porcupine initialized and started successfully
```

#### Step 5: Test Detection

1. Ensure you're in active conversation (connected to Gemini)
2. Say clearly: **"Alexa"** (English pronunciation)
3. Check if session pauses/resumes

---

### Problem: No Notification

**Solution:**
- Enable Picovoice in app settings
- Restart app
- Check notification permissions (Android 13+)

---

### Problem: "Alexa" Not Detected

**Solutions:**
- Speak clearly and loudly
- Pronounce "Alexa" in English (not "Aleksa")
- Increase sensitivity in Picovoice settings
- Check microphone works (test in another app)
- Ensure you're in active conversation

---

### Problem: Multiple Toggles

**Explanation:**
- Picovoice may detect the word multiple times during pronunciation
- Each detection toggles state: ON→OFF→ON→OFF
- This is normal behavior

**Solution:**
- Speak the wake word once and wait
- Consider adding debouncing (development task)

---

### Problem: Picovoice Doesn't Start After Reboot

**Solution:**
- Enable "Start on boot" in Picovoice settings
- Check app has "Autostart" permission in phone settings
- Verify RECEIVE_BOOT_COMPLETED permission granted

---

### Problem: "No Wake Words to Load"

**Solution:**
- `.ppn` files not in assets folder
- Check you copied all 3 files to correct location
- Rebuild app after adding files

---

### Problem: "PorcupineException"

**Solution:**
- Check Access Key is correct
- Update Access Key in app settings
- Verify `.ppn` files are for Android platform

---

### Problem: Words Not Detected

**Solutions:**
1. Increase sensitivity (sensitivity slider in settings)
2. Speak clearly and loudly
3. Ensure microphone works
4. Check app has microphone permission
5. Test with built-in "Alexa" first

---

## Configuration

### Sensitivity

Adjust wake word detection sensitivity:
1. Go to Settings → Picovoice
2. Adjust **Sensitivity** slider
3. Higher = more sensitive (more false positives)
4. Lower = less sensitive (may miss detections)

### Auto-Start on Boot

Enable Picovoice to start automatically when device boots:
1. Go to Settings → Picovoice
2. Enable **"Auto-start on boot"**
3. Requires explicit user consent for privacy

### Access Key

If using custom wake words:
1. Go to Settings → Picovoice
2. Enter **Access Key** from Picovoice Console
3. Save

---

## Verification Commands

### Check if Picovoice is Enabled

```bash
# Check config file exists
adb shell "run-as ai.pipecat.gemini_multimodal_websocket_demo ls shared_prefs/"

# Check contents
adb shell "run-as ai.pipecat.gemini_multimodal_websocket_demo cat shared_prefs/picovoice_prefs.xml"
```

Should show:
```xml
<boolean name="picovoice_enabled" value="true" />
```

### Monitor Wake Word Detection

```bash
adb logcat | grep -i "wake word\|picovoice\|porcupine"
```

Look for:
```
Wake word detected: alexa (SYSTEM)
Handling wake word: alexa (SYSTEM)
System command: alexa
```

---

## Reset Picovoice

If nothing works:

1. Disable Picovoice in settings
2. Close app completely
3. (Optional) Clear app cache
4. Open app
5. Enable Picovoice in settings

---

## Important Notes

- `.ppn` files are platform-specific (Android/iOS/Linux/etc.)
- Always download **Android** version
- Wake words are language-specific
- Free Picovoice account has usage limits
- Built-in "ALEXA" works without account

---

## Advanced: Rate Limiting

To prevent wake word spam:

**Current Implementation:**
- Minimum 5 second interval between detections
- Prevents battery drain from repeated triggers
- Protects against audio loop attacks

**Configuration:**
```kotlin
private val MIN_WAKE_WORD_INTERVAL = 5000L // 5 seconds
```

---

## Support

For issues:
1. Check logs for error messages
2. Verify `.ppn` files are correct
3. Test with built-in "Alexa" first
4. Check Picovoice Console for account limits
5. Contact development team with logs

---

## References

- [Picovoice Console](https://console.picovoice.ai)
- [Porcupine Documentation](https://picovoice.ai/docs/porcupine/)
- [Android Integration Guide](https://picovoice.ai/docs/porcupine/android/)

---

**Document Status:** ACTIVE  
**Review Cycle:** Quarterly  
**Next Review:** 2026-03-01
