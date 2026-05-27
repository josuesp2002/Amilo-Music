package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.sin

class AmbientSynthesizer {

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isPlaying = false

    @Volatile
    var currentSongId: String = ""
        private set

    // Simple frequency mappings
    private val NOTES = mapOf(
        "C2" to 65.41f, "E2" to 82.41f, "G2" to 98.00f, "A2" to 110.00f, "B2" to 123.47f,
        "C3" to 130.81f, "D3" to 146.83f, "E3" to 164.81f, "F3" to 174.61f, "G3" to 196.00f, "A3" to 220.00f, "B3" to 246.94f,
        "C4" to 261.63f, "D4" to 293.66f, "E4" to 329.63f, "F4" to 349.23f, "G4" to 392.00f, "A4" to 440.00f, "B4" to 493.88f,
        "C5" to 523.25f, "E5" to 659.25f, "G5" to 783.99f, "A5" to 880.00f, "B5" to 987.77f
    )

    fun getAudioSessionId(): Int {
        return audioTrack?.audioSessionId ?: 0
    }

    fun start(songId: String, onSessionIdReady: (Int) -> Unit) {
        if (isPlaying && currentSongId == songId) return
        stop()

        currentSongId = songId
        isPlaying = true

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        var bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
        if (bufferSize <= 0) {
            bufferSize = 4096
        }

        try {
            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack = track
            onSessionIdReady(track.audioSessionId)
            track.play()
        } catch (t: Throwable) {
            Log.e("AmbientSynthesizer", "Error starting track playback: ${t.message}")
            audioTrack = null
            isPlaying = false
            currentSongId = ""
            return
        }

        synthJob = scope.launch {
            try {
                audioTrack?.let { generateAudioLoop(it, sampleRate, songId) }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (t: Throwable) {
                Log.e("AmbientSynthesizer", "Error in synth loop: ${t.message}")
            }
        }
    }

    fun stop() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e("AmbientSynthesizer", "Error releasing audio track: ${e.message}")
        }
        audioTrack = null
        currentSongId = ""
    }

    private suspend fun generateAudioLoop(track: AudioTrack, sampleRate: Int, songId: String) {
        val buffer = ShortArray(1024)
        var globalPhase = 0L

        // Base tempo configuration
        val beatsPerMinute = 52.0f
        val beatDurationInSamples = (sampleRate * 60 / beatsPerMinute).toLong()

        // Chord Progressions
        val progressions = when (songId) {
            "demo_cosmico" -> listOf(
                listOf("C3", "E3", "G3", "B3", "E4", "G4"), // Cmaj7
                listOf("A2", "C3", "E3", "G3", "C4", "E4"), // Am7
                listOf("F2", "A2", "C3", "E3", "A3", "C4"), // Fmaj7
                listOf("G2", "B2", "D3", "E3", "G3", "B3")  // G6
            )
            "demo_vacio" -> listOf(
                listOf("E2", "G2", "B2", "D3", "F3", "A3"), // Em9
                listOf("C2", "E2", "G2", "B2", "D3", "G3"), // Cmaj9
                listOf("G2", "B2", "D3", "F3", "A3", "B3"), // Gmaj9
                listOf("D2", "A2", "D3", "F3", "A3", "D4")  // Dadd4
            )
            else -> listOf(
                listOf("F2", "A2", "C3", "E3", "G3", "A3"), // Fmaj7
                listOf("G2", "B2", "D3", "G3", "B3", "D4"), // G
                listOf("E2", "G2", "B2", "E3", "G3", "B3"), // Em
                listOf("A2", "C3", "E3", "A3", "C4", "E4")  // Am
            )
        }

        while (isPlaying) {
            val totalBeats = globalPhase / beatDurationInSamples
            val currentProgressionIndex = ((totalBeats / 4) % progressions.size).toInt()
            val currentChord = progressions[currentProgressionIndex]

            // Arpeggio note calculation based on beat phase
            val beatPhase = (globalPhase % beatDurationInSamples).toFloat() / beatDurationInSamples
            val relativeSubbeat = (beatPhase * 4).toInt() % 4 // 16th or 8th spacing
            
            // Choose note from current chord to arpeggiate
            val leadNoteName = currentChord[relativeSubbeat % currentChord.size]
            val leadFreq = NOTES[leadNoteName] ?: 220f

            // Bass note
            val bassFreq1 = NOTES[currentChord[0]] ?: 110f
            val bassFreq2 = NOTES[currentChord[2]] ?: 165f

            // Fill buffer block
            for (i in buffer.indices) {
                val t = (globalPhase + i).toDouble() / sampleRate
                
                // 1. Synthesize Bass (Pads, low-passed, rich and warm)
                // Combine low notes with slow vibrato and clean sine waves
                val bassWave = sin(2.0 * Math.PI * bassFreq1 * t) * 0.4 +
                               sin(2.0 * Math.PI * bassFreq2 * t) * 0.2

                // 2. Synthesize Arpeggio Lead (Sine + Pluck Envelope)
                val subbeatPhase = (globalPhase + i) % (beatDurationInSamples / 4)
                val envelopePhase = subbeatPhase.toFloat() / (beatDurationInSamples / 4)
                // ADSR envelope: Fast attack, slow decay exp
                val pluckEnvelope = if (envelopePhase < 0.05f) {
                    envelopePhase / 0.05f // Linear attack
                } else {
                    val decayPhase = (envelopePhase - 0.05f) / 0.95f
                    Math.exp(-4.0 * decayPhase).toFloat() // Exponential decay
                }

                // Lead sound: Triangle-ish wave blended with sine to make it sound warm
                val leadSine = sin(2.0 * Math.PI * leadFreq * t)
                val leadTriangle = 2.0 / Math.PI * Math.asin(sin(2.0 * Math.PI * leadFreq * t))
                val leadWave = (leadSine * 0.4 + leadTriangle * 0.6) * pluckEnvelope * 0.25

                // 3. Ambient texture (very subtle low vinyl-like static noise or lofi whistle)
                val slowMod = sin(2.0 * Math.PI * 0.05 * t) // Lofi pitch wow and flutter
                val chorusWave = sin(2.0 * Math.PI * (leadFreq + 2.0 * slowMod) * t) * pluckEnvelope * 0.08

                // Mix together
                val mixed = bassWave + leadWave + chorusWave

                // High-cut low-pass filter logic in software (optional, simple moving average to soften)
                // Normalize and write to buffer
                // Master volume set to 0.7f to keep it quiet and pleasant
                val scaled = (mixed * 32767.0 * 0.45).toInt().coerceIn(-32768, 32767)
                buffer[i] = scaled.toShort()
            }

            globalPhase += buffer.size

            // Block-write buffer to AudioTrack
            if (isPlaying) {
                try {
                    val written = track.write(buffer, 0, buffer.size)
                    if (written < 0) {
                        Log.e("AmbientSynthesizer", "Error writing to audio track: $written")
                        break
                    }
                } catch (t: Throwable) {
                    Log.e("AmbientSynthesizer", "AudioTrack write exception caught: ${t.message}")
                    break
                }
            }

            // Yield control back to cooperative scheduler
            yield()
        }
    }
}
