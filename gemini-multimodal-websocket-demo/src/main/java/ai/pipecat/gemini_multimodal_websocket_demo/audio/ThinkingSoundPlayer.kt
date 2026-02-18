package ai.pipecat.gemini_multimodal_websocket_demo.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays a discrete "thinking" sound (ticking) to indicate background processing.
 * 
 * Features:
 * - Delayed start (defaults to 1000ms) to avoid noise on fast responses.
 * - Auto-cancellation if stopped before delay elapses.
 * - Looping pattern without external assets.
 */
class ThinkingSoundPlayer(
    private val scope: CoroutineScope
) {
    private val tag = "ThinkingSoundPlayer"
    private var toneGenerator: ToneGenerator? = null
    private var playerJob: Job? = null
    private val isPlaying = AtomicBoolean(false)
    
    init {
        try {
            // Volume 50 is relative to stream volume
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 40)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Schedule thinking sound to start after [delayMs].
     * If stop() is called before delayMs, sound never plays.
     */
    fun startWithDelay(delayMs: Long = 1000L) {
        stop() // Reset any existing job
        
        isPlaying.set(true)
        playerJob = scope.launch {
            try {
                // Wait for the delay (e.g. 1 second)
                delay(delayMs)
                
                // If still supposed to be playing, start the loop
                if (isPlaying.get()) {
                    Log.d(tag, "Starting thinking sound loop (latency > ${delayMs}ms)")
                    startLoop()
                }
            } catch (e: Exception) {
                // Job cancelled, do nothing
            }
        }
    }

    fun stop() {
        if (isPlaying.getAndSet(false)) {
            Log.d(tag, "Stopping thinking sound")
        }
        playerJob?.cancel()
        playerJob = null
    }

    private suspend fun startLoop() {
        while (isPlaying.get()) {
            // Play a very short "tick"
            // TONE_PROP_BEEP is usually a short, non-intrusive beep
            // We use a very short duration to make it sound like a "tick" or "click"
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 30) // 30ms beep
            
            // Wait for next tick (creating a rhythm)
            delay(800) // Tick....... Tick.......
        }
    }

    fun release() {
        stop()
        toneGenerator?.release()
        toneGenerator = null
    }
}
