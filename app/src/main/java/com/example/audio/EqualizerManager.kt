package com.example.audio

import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.*

class EqualizerManager {

    private var equalizer: Equalizer? = null
    var activeSessionId: Int = 0
        private set

    // Default 5-band fallback configuration in case hardware effects are unavailable
    var bandFrequencies = listOf(60, 230, 910, 4000, 14000)
        private set

    var numBands = 5
        private set

    // Current gains in dB (-15 to 15 range)
    private var bandGains = FloatArray(5) { 0.0f }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var adaptiveJob: Job? = null
    var isAdaptiveEnabled = false
        set(value) {
            field = value
            if (value) startAdaptiveAutomation()
            else stopAdaptiveAutomation()
        }

    fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        
        // Release previous equalizer first
        release()
        activeSessionId = audioSessionId

        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = true
            
            val tempNumBands = eq.numberOfBands.toInt()
            if (tempNumBands > 0) {
                numBands = tempNumBands
                val tempFreqs = mutableListOf<Int>()
                val tempGains = FloatArray(tempNumBands) { 0.0f }
                
                for (i in 0 until tempNumBands) {
                    val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000 // mHz to Hz
                    tempFreqs.add(centerFreqHz)
                    
                    // Convert milli-decibels to standard dB
                    val currentMilliDb = eq.getBandLevel(i.toShort()).toFloat()
                    tempGains[i] = currentMilliDb / 100f
                }
                
                bandFrequencies = tempFreqs
                bandGains = tempGains
                equalizer = eq
                Log.d("EqualizerManager", "Hardware equalizer successfully initialized with $numBands bands.")
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Hardware equalizer failed to initialize, using software fallback: ${e.message}")
            equalizer = null
        }
    }

    fun getGains(): List<Float> {
        return bandGains.toList()
    }

    fun setBandGain(band: Int, gainDb: Float) {
        val clampedGain = gainDb.coerceIn(-15.0f, 15.0f)
        if (band >= 0 && band < numBands) {
            bandGains[band] = clampedGain
            
            equalizer?.let { eq ->
                try {
                    // Convert to Millibels
                    val milliDb = (clampedGain * 100).toInt().toShort()
                    eq.setBandLevel(band.toShort(), milliDb)
                } catch (e: Exception) {
                    Log.e("EqualizerManager", "Failed to set hardware band level: ${e.message}")
                }
            }
        }
    }

    private fun startAdaptiveAutomation() {
        adaptiveJob?.cancel()
        adaptiveJob = scope.launch {
            while (isActive) {
                delay(4000)
                if (isAdaptiveEnabled) {
                    val currentGains = bandGains.toList()
                    val modulatedGains = currentGains.mapIndexed { index, gain ->
                        val timeFactor = System.currentTimeMillis() / 2000.0 + index
                        val variance = (Math.sin(timeFactor) * 0.45).toFloat()
                        (gain + variance).coerceIn(-15.0f, 15.0f)
                    }
                    applyPreset(modulatedGains)
                }
            }
        }
    }

    private fun stopAdaptiveAutomation() {
        adaptiveJob?.cancel()
    }

    fun applyPreset(presetGains: List<Float>) {
        for (i in 0 until numBands) {
            if (i < presetGains.size) {
                setBandGain(i, presetGains[i])
            } else {
                setBandGain(i, 0.0f)
            }
        }
    }

    fun release() {
        try {
            equalizer?.apply {
                enabled = false
                release()
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error releasing hardware equalizer: ${e.message}")
        }
        equalizer = null
        activeSessionId = 0
    }
}
