package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.util.Log
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Simplified service to handle Azure Speech-to-Text and Text-to-Speech
 * following standard SDK practices.
 */
class AzureSpeechService(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "AzureSpeechService"

    private var speechConfig: SpeechConfig? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var pushStream: PushAudioInputStream? = null

    // Callbacks
    var onTranscriptionReceived: ((String) -> Unit)? = null
    var onIntermediateResult: ((String) -> Unit)? = null
    var onSpeechDetected: (() -> Unit)? = null 
    var onAudioDataReceived: ((ByteArray) -> Unit)? = null 
    var onError: ((String) -> Unit)? = null 
    var onSessionStarted: (() -> Unit)? = null 
    var onSessionStopped: (() -> Unit)? = null 

    private var currentLanguage = "pl-PL"

    @Volatile
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        initializeSpeechConfig()
    }

    /**
     * Change STT language and reinitialize the recognizer.
     */
    fun setLanguage(languageCode: String) {
        if (currentLanguage == languageCode && speechRecognizer != null) return
        
        Log.i(tag, "Changing STT language to: $languageCode")
        currentLanguage = languageCode
        
        // Stop and release previous recognizer
        try {
            speechRecognizer?.stopContinuousRecognitionAsync()
            speechRecognizer?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing previous recognizer", e)
        }
        
        initializeSpeechConfig()
    }

    private fun initializeSpeechConfig() {
        val key = Preferences.azureApiKey.value?.trim()
        val region = Preferences.azureRegion.value?.trim()

        if (!key.isNullOrBlank() && !region.isNullOrBlank()) {
            try {
                Log.d(tag, "Initializing STT with region: $region, language: $currentLanguage")
                speechConfig = SpeechConfig.fromSubscription(key, region)
                speechConfig?.speechRecognitionLanguage = currentLanguage
                
                // Configure STT input stream - MUST match AudioEngine (16kHz, 16-bit, Mono)
                val format = AudioStreamFormat.getWaveFormatPCM(16000L, 16.toShort(), 1.toShort())
                pushStream = AudioInputStream.createPushStream(format)
                val audioConfig = AudioConfig.fromStreamInput(pushStream)
                speechRecognizer = SpeechRecognizer(speechConfig, audioConfig)

                // ... [rest of initialization unchanged] ...
                // Note: I will use multi_replace for accuracy in a real scenario, 
                // but for this block I will keep the existing listeners by including them.
                
                // STT Listeners
                speechRecognizer?.sessionStarted?.addEventListener { _, e ->
                    Log.i(tag, "STT Session Started: ${e.sessionId}")
                    onSessionStarted?.invoke()
                }

                speechRecognizer?.sessionStopped?.addEventListener { _, e ->
                    Log.i(tag, "STT Session Stopped: ${e.sessionId}")
                    onSessionStopped?.invoke()
                }
                
                speechRecognizer?.recognizing?.addEventListener { _, e ->
                    if (e.result.text.isNotEmpty()) {
                        Log.d(tag, "STT Recognizing: ${e.result.text}")
                        onSpeechDetected?.invoke()
                        onIntermediateResult?.invoke(e.result.text)
                    }
                }

                speechRecognizer?.recognized?.addEventListener { _, e ->
                    Log.d(tag, "STT Recognized event: reason=${e.result.reason}, text='${e.result.text}'")
                    if (e.result.reason == ResultReason.RecognizedSpeech) {
                        Log.i(tag, "STT Recognized: ${e.result.text}")
                        onTranscriptionReceived?.invoke(e.result.text)
                    } else if (e.result.reason == ResultReason.NoMatch) {
                        val noMatch = NoMatchDetails.fromResult(e.result)
                        Log.d(tag, "STT NoMatch: reason=${noMatch.reason}")
                    }
                }

                speechRecognizer?.canceled?.addEventListener { _, e ->
                    Log.e(tag, "STT Canceled: Reason=${e.reason}, ErrorDetails=${e.errorDetails}")
                    if (e.reason == CancellationReason.Error) {
                        Log.e(tag, "STT Error Code=${e.errorCode}")
                        val errorMsg = when (e.errorCode) {
                            CancellationErrorCode.AuthenticationFailure -> "Błąd uwierzytelnienia (401). Sprawdź klucz API i region."
                            CancellationErrorCode.ConnectionFailure -> "Błąd połączenia. Sprawdź internet."
                            else -> "Błąd Azure STT: ${e.errorDetails}"
                        }
                        onError?.invoke(errorMsg)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize Azure Speech SDK", e)
            }
        } else {
            Log.w(tag, "Azure API key or region is missing in Preferences")
        }
    }

    fun startSTT() {
        Log.d(tag, "Starting Azure STT ($currentLanguage)")
        try {
            speechRecognizer?.startContinuousRecognitionAsync()
        } catch (e: Exception) {
            Log.e(tag, "Error starting STT", e)
        }
    }

    fun stopSTT() {
        Log.d(tag, "Stopping Azure STT")
        try {
            speechRecognizer?.stopContinuousRecognitionAsync()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping STT", e)
        }
    }

    private var lastFeedLog = 0L
    
    /**
     * Feed raw PCM audio data (16kHz, 16-bit, Mono) to Azure STT.
     */
    fun feedAudio(data: ByteArray) {
        if (System.currentTimeMillis() - lastFeedLog > 5000) {
            Log.v(tag, "Feeding audio to Azure STT: ${data.size} bytes")
            lastFeedLog = System.currentTimeMillis()
        }
        pushStream?.write(data)
    }

    /**
     * Standard TTS Synthesis.
     * @param voiceOverride Optional voice name to use (e.g. pl-PL-MarekNeural). If null, uses preference.
     */
    fun synthesize(text: String, voiceOverride: String? = null) {
        if (text.isBlank()) return
        
        val key = Preferences.azureApiKey.value?.trim()
        val region = Preferences.azureRegion.value?.trim()
        val voice = voiceOverride ?: Preferences.azureTtsVoice.value?.trim() ?: "pl-PL-ZofiaNeural"
        
        if (key.isNullOrBlank() || region.isNullOrBlank()) {
            Log.w(tag, "synthesize: Credentials missing")
            return
        }

        Log.i(tag, "🎙️ TTS Start: \"${text.take(30)}...\" Voice: $voice")
        
        try {
            executor.execute {
                var synth: SpeechSynthesizer? = null
                var config: SpeechConfig? = null
                try {
                    config = SpeechConfig.fromSubscription(key, region).apply {
                        speechRecognitionLanguage = currentLanguage
                        setSpeechSynthesisVoiceName(voice)
                        setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm)
                    }
                    
                    synth = SpeechSynthesizer(config, null)
                    val result = synth.SpeakTextAsync(text).get()
                    
                    if (result?.reason == ResultReason.SynthesizingAudioCompleted) {
                        val audio = result.audioData
                        if (audio != null && audio.isNotEmpty()) {
                            Log.d(tag, "🎙️ TTS Success: ${audio.size} bytes for \"${text.take(20)}...\"")
                            onAudioDataReceived?.invoke(audio)
                        } else {
                            Log.w(tag, "🎙️ TTS Success but empty audio")
                        }
                    } else if (result?.reason == ResultReason.Canceled) {
                        val cancellation = SpeechSynthesisCancellationDetails.fromResult(result)
                        Log.w(tag, "🎙️ TTS Canceled/Interrupted: ${cancellation.reason}")
                        if (cancellation.reason == CancellationReason.Error) {
                            Log.e(tag, "🎙️ TTS Error Detail: ${cancellation.errorDetails}")
                            val errorMsg = when (cancellation.errorCode) {
                                CancellationErrorCode.AuthenticationFailure -> "Błąd uwierzytelnienia (401). Sprawdź klucz i region."
                                else -> "Błąd Azure TTS: ${cancellation.errorDetails}"
                            }
                            onError?.invoke(errorMsg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "🎙️ TTS Background thread exception", e)
                } finally {
                    try {
                        synth?.close()
                        config?.close()
                    } catch (e: Exception) {}
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            Log.w(tag, "🎙️ TTS submission rejected (likely interrupted/shutting down)")
        } catch (e: Exception) {
            Log.e(tag, "🎙️ TTS submission failed", e)
        }
    }

    /**
     * Stop any ongoing synthesis immediately.
     */
    fun stopSynthesis() {
        Log.i(tag, "Stopping ongoing synthesis tasks (request level)")
        
        // 1. Shutdown executor to cancel queued tasks
        val oldExecutor = executor
        executor = Executors.newSingleThreadExecutor()
        try {
            oldExecutor.shutdownNow()
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down old executor", e)
        }
    }

    fun release() {
        try {
            speechRecognizer?.stopContinuousRecognitionAsync()
            speechRecognizer?.close()
            pushStream?.close()
            executor.shutdownNow()
        } catch (e: Exception) {
            Log.e(tag, "Error during release", e)
        } finally {
            speechRecognizer = null
            pushStream = null
        }
    }
}
