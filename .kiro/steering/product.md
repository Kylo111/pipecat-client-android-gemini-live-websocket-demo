## Product Overview

Android demo application showcasing real-time voice interaction with Google's Gemini Multimodal Live API using the Pipecat Android client library over WebSocket connections.

The app provides a voice chat interface where users can speak to an AI assistant powered by Gemini, with real-time audio streaming, transcription, and visual feedback for audio levels and speaking states.

### Key Features:
- **Real-time bidirectional audio streaming** with Gemini
- **Background operation** - continues working when app is in background or screen is off
- **Foreground service** (VoiceService) maintains session active in background
- **Wake word detection** via Picovoice Porcupine (independent service)
- **LibreChat integration** - syncs conversations and transcripts
- **Session management** with auto-pause and bot response timeouts
- **Visual audio level indicators** for both user and bot
- **Permission handling** for microphone and camera access
- **Connection management** with API key configuration
- **Error handling** and user feedback dialogs
- **Session resumption** - can pause and resume conversations
- **Offline mode** - works without LibreChat connection

### Background Operation:
The app is designed to work continuously in the background:
- ✅ VoiceService as foreground service keeps session active
- ✅ Audio recording continues when screen is off
- ✅ WebSocket connection remains active in background
- ✅ Wake lock maintains CPU active for audio processing
- ✅ Picovoice wake word detection works independently

### Session Pause Conditions:
Session pauses ONLY when:
1. User manually pauses (button or wake word command)
2. Auto-pause timeout (user inactivity)
3. Bot response timeout (no Gemini response)
4. Critical memory pressure (emergency shutdown)

Session does NOT pause when:
- App goes to background
- Screen turns off
- User switches to another app
- Device orientation changes
