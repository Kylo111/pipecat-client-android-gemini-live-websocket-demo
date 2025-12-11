# Requirements Document

## Introduction

Radykalne uproszczenie Core audio aplikacji Gemini Live. Obecna architektura (5000+ linii) jest zbyt skomplikowana i powoduje ciągłe błędy: opóźnienia audio 15+ sekund, urywanie słów, brak działającego full-duplex. Celem jest przepisanie Core wzorując się na oficjalnym demo Gemini (~300 linii), eliminując zbędne warstwy abstrakcji i własne implementacje funkcjonalności, które Gemini/AudioTrack już zapewniają.

## Glossary

- **AudioEngine**: Uproszczony komponent odpowiedzialny za nagrywanie (AudioRecord) i odtwarzanie (AudioTrack) audio
- **GeminiClient**: Prosty klient WebSocket do komunikacji z Gemini Live API
- **VoiceClientManager**: Główny manager sesji głosowej (uproszczony)
- **turnComplete**: Event od Gemini sygnalizujący koniec wypowiedzi bota
- **interrupted**: Event od Gemini sygnalizujący przerwanie przez użytkownika
- **AudioTrack**: Android API do odtwarzania audio z wbudowanym buforem
- **AudioRecord**: Android API do nagrywania audio
- **PCM16**: Format audio używany przez Gemini (16-bit, 24kHz output, 16kHz input)
- **AEC (Acoustic Echo Cancellation)**: Eliminacja echa - zapobiega słyszeniu przez mikrofon dźwięku z głośników
- **VOICE_COMMUNICATION**: Tryb AudioSource na Androidzie włączający systemowe AEC
- **MODE_IN_COMMUNICATION**: Tryb AudioManager wymagany dla AEC i Bluetooth SCO
- **AudioDeviceHandler**: Helper do zarządzania routingiem audio (Bluetooth, słuchawki, głośnik)
- **setCommunicationDevice**: API Android 31+ do wymuszania routingu audio

## Requirements

### Requirement 1

**User Story:** As a user, I want to hear bot responses without delay, so that conversation feels natural.

#### Acceptance Criteria

1. WHEN audio chunk is received from Gemini THEN the AudioEngine SHALL write it directly to AudioTrack without custom batching
2. WHEN bot starts speaking THEN the AudioEngine SHALL begin playback within 100ms of first chunk arrival
3. WHEN multiple chunks arrive in burst THEN the AudioEngine SHALL let AudioTrack handle buffering natively
4. WHEN audio queue size is measured THEN the AudioEngine SHALL maintain queue below 500ms equivalent

### Requirement 2

**User Story:** As a user, I want bot to finish speaking complete sentences, so that I understand the full response.

#### Acceptance Criteria

1. WHEN Gemini sends turnComplete event THEN the VoiceClientManager SHALL wait for AudioTrack to finish playing before signaling end of bot turn
2. WHEN checking if bot finished speaking THEN the AudioEngine SHALL verify AudioTrack playback position equals total written samples
3. WHEN turnComplete is received THEN the system SHALL NOT rely on custom silence detection

### Requirement 3

**User Story:** As a user, I want to interrupt the bot while speaking, so that I can have natural conversation.

#### Acceptance Criteria

1. WHEN Gemini sends interrupted event THEN the AudioEngine SHALL immediately call AudioTrack.flush() to stop playback
2. WHEN interrupted event is received THEN the AudioEngine SHALL clear any pending audio data
3. WHEN user speaks during bot response THEN the system SHALL send audio to Gemini for interrupt detection
4. WHEN AudioTrack is flushed THEN the AudioEngine SHALL be ready to play new audio immediately

### Requirement 4

**User Story:** As a user, I want clean audio without artifacts, so that I can understand the bot clearly.

#### Acceptance Criteria

1. WHEN writing to AudioTrack THEN the AudioEngine SHALL use appropriate buffer size (minimum 40ms chunks)
2. WHEN AudioTrack is created THEN the AudioEngine SHALL configure it with MODE_STREAM for continuous playback
3. WHEN audio format is configured THEN the AudioEngine SHALL use PCM16 at 24kHz for output
4. WHEN audio is played THEN the AudioEngine SHALL NOT introduce gaps between chunks

### Requirement 9

**User Story:** As a user, I want full-duplex conversation without echo, so that bot does not hear itself and interrupt.

#### Acceptance Criteria

1. WHEN AudioRecord is created THEN the AudioEngine SHALL use VOICE_COMMUNICATION audio source for system AEC
2. WHEN AcousticEchoCanceler is available THEN the AudioEngine SHALL enable hardware AEC
3. WHEN bot is speaking THEN the microphone SHALL NOT capture bot audio as user input
4. WHEN both recording and playback are active THEN the system SHALL prevent audio feedback loop

### Requirement 10

**User Story:** As a developer, I want non-blocking audio writes, so that WebSocket messages are not delayed.

#### Acceptance Criteria

1. WHEN audio chunk is received from WebSocket THEN the system SHALL NOT block WebSocket thread with AudioTrack.write()
2. WHEN audio data arrives THEN the system SHALL use Kotlin Channel to decouple WebSocket from AudioTrack
3. WHEN AudioTrack buffer is full THEN the write operation SHALL block only the audio coroutine, not WebSocket
4. WHEN interrupted event arrives THEN the system SHALL process it immediately without waiting for audio writes

### Requirement 11

**User Story:** As a user, I want to use Bluetooth headphones, so that I can have private conversations.

#### Acceptance Criteria

1. WHEN Bluetooth headset is connected THEN the system SHALL route audio to Bluetooth device
2. WHEN Bluetooth headset is disconnected during call THEN the system SHALL fallback to phone speaker
3. WHEN wired headphones are connected THEN the system SHALL prioritize wired over speaker
4. WHEN audio session starts THEN the system SHALL set AudioManager mode to MODE_IN_COMMUNICATION

### Requirement 12

**User Story:** As a developer, I want proper permission handling, so that app does not crash on missing permissions.

#### Acceptance Criteria

1. WHEN RECORD_AUDIO permission is missing THEN the AudioEngine SHALL throw descriptive exception instead of crash
2. WHEN BLUETOOTH_CONNECT permission is missing on Android 12+ THEN the system SHALL fallback to speaker without crash
3. WHEN initializing audio THEN the system SHALL verify all required permissions before creating AudioRecord

### Requirement 5

**User Story:** As a developer, I want simple architecture, so that I can debug and maintain the code easily.

#### Acceptance Criteria

1. WHEN implementing audio core THEN the system SHALL have maximum 3 main classes (GeminiClient, AudioEngine, VoiceClientManager)
2. WHEN handling Gemini events THEN the system SHALL NOT duplicate Gemini state machine locally
3. WHEN detecting end of bot speech THEN the system SHALL use turnComplete event instead of custom silence detection
4. WHEN managing audio THEN the system SHALL NOT implement custom batching logic

### Requirement 6

**User Story:** As a user, I want the app to work in background, so that I can multitask while talking to bot.

#### Acceptance Criteria

1. WHEN app goes to background THEN the VoiceClientManager SHALL maintain WebSocket connection
2. WHEN app goes to background THEN the AudioEngine SHALL continue recording and playback
3. WHEN screen is off THEN the system SHALL keep audio session active via foreground service

### Requirement 7

**User Story:** As a user, I want to see transcriptions, so that I can follow the conversation visually.

#### Acceptance Criteria

1. WHEN Gemini sends inputTranscription THEN the VoiceClientManager SHALL emit user transcript to UI
2. WHEN Gemini sends outputTranscription THEN the VoiceClientManager SHALL emit bot transcript to UI
3. WHEN transcription is received THEN the system SHALL display it without affecting audio playback

### Requirement 8

**User Story:** As a developer, I want to test audio components, so that I can verify correctness.

#### Acceptance Criteria

1. WHEN AudioEngine is implemented THEN it SHALL expose methods for unit testing (start, stop, writeAudio, flush)
2. WHEN GeminiClient is implemented THEN it SHALL use event-based callbacks for testability
3. WHEN implementing audio pipeline THEN the system SHALL allow mocking of AudioTrack/AudioRecord for tests
