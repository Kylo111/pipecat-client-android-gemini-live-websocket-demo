# System Wake Word Files (Optional)

## Default Behavior (No Files Needed)

By default, the app uses the built-in wake word **"ALEXA"** to toggle the microphone.
This works immediately without any setup!

## Custom Wake Words (Optional)

You can optionally create custom wake word .ppn files for Polish language:

- `start_pl.ppn` - Wake word for "start" command (enable mic)
- `stop_pl.ppn` - Wake word for "stop" command (disable mic)

## How to Create Custom Files

1. Go to [Picovoice Console](https://console.picovoice.ai)
2. Create a free account or log in
3. Navigate to "Porcupine" → "Wake Words"
4. Create each wake word:
   - Click "Create Wake Word"
   - Enter the wake word phrase (start or stop)
   - Select language: **Polski (pl)** or **English (en)**
   - Click "Train" (takes ~10 seconds)
   - Download the .ppn file for **Android**
5. Rename the downloaded files to match the names above
6. Place them in this directory

## Important Notes

- **Without custom files**: App uses built-in "ALEXA" wake word
- **With custom files**: App uses your custom "start" and "stop" wake words
- Custom wake words override the built-in "ALEXA" wake word
- The .ppn files are copied to internal storage on first app launch
