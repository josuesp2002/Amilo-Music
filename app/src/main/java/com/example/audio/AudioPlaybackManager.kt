package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.net.Uri
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.database.SongEntity
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlaybackManager(
    private val context: Context,
    private val equalizerManager: EqualizerManager,
    private val ambientSynthesizer: AmbientSynthesizer
) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var onSongCompletedCallback: (() -> Unit)? = null
    var isShuffleActive = false
    private var isFadingOut = false

    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()
            
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener, android.os.Handler(android.os.Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            val result = audioManager.requestAudioFocus(request)
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    init {
        startProgressTracker()
        context.registerReceiver(noisyReceiver, android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private fun startPlaybackService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        val intent = Intent(context, MediaPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun updateCurrentSongStateHelper(song: SongEntity) {
        if (_currentSong.value?.id == song.id) {
            _currentSong.value = song
        }
    }

    fun playSong(song: SongEntity, onSongCompleted: () -> Unit) {
        requestAudioFocus()
        fadeJob?.cancel()
        _currentSong.value = song
        this.onSongCompletedCallback = onSongCompleted
        
        val recedingPlayer = mediaPlayer
        if (recedingPlayer != null) {
            try {
                if (recedingPlayer.isPlaying) recedingPlayer.stop()
                recedingPlayer.release()
            } catch(e: Exception) {}
        }
        mediaPlayer = null

        if (song.isDemo) {
            // Procedural Synthesizer track
            _playbackDuration.value = 300000L // Faux infinite/5 min progress helper for synth
            
            fadeJob = scope.launch(Dispatchers.Default) {
                ambientSynthesizer.start(song.id) { sessionId ->
                    scope.launch(Dispatchers.Main) {
                        equalizerManager.initEqualizer(sessionId)
                        _isPlaying.value = true
                    }
                }
            }
        } else {
            // Local file on device
            ambientSynthesizer.stop()
            
            fadeJob = scope.launch(Dispatchers.Main) {
                var localMp: MediaPlayer? = null
                try {
                    localMp = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val mp = MediaPlayer().apply {
                            setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            if (song.filePath.startsWith("content://")) {
                                setDataSource(context, Uri.parse(song.filePath))
                            } else {
                                setDataSource(song.filePath)
                            }
                            prepare() // Prepare on IO thread to avoid main thread freeze
                        }
                        mp
                    }
                    
                    if (!isActive) {
                        localMp.release()
                        return@launch
                    }

                    mediaPlayer = localMp
                    localMp.setVolume(1.0f, 1.0f)
                    isFadingOut = false
                    
                    _playbackDuration.value = localMp.duration.toLong()
                    equalizerManager.initEqualizer(localMp.audioSessionId)

                    // Normal completion
                    localMp.setOnCompletionListener {
                        onSongCompleted()
                    }

                    localMp.start()

                    _isPlaying.value = true
                    startPlaybackService()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    localMp?.release()
                    throw e
                } catch (e: Exception) {
                    localMp?.release()
                    Log.e("AudioPlaybackManager", "Error playing local audio track: ${e.message}")
                    onSongCompleted() // Move next on corrupt/missing file
                }
            }
        }
    }

    fun pause() {
        fadeJob?.cancel()
        val current = _currentSong.value ?: return

        fadeJob = scope.launch(Dispatchers.Main) {
            if (current.isDemo) {
                ambientSynthesizer.stop()
                _isPlaying.value = false
            } else {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                    _isPlaying.value = false
                }
            }
        }
    }

    fun resume(onSongCompleted: () -> Unit) {
        requestAudioFocus()
        fadeJob?.cancel()
        val current = _currentSong.value ?: return

        fadeJob = scope.launch(Dispatchers.Main) {
            if (current.isDemo) {
                playSong(current, onSongCompleted)
            } else {
                mediaPlayer?.let { mp ->
                    mp.start()
                    mp.setVolume(1.0f, 1.0f)
                    _isPlaying.value = true
                    startPlaybackService()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val current = _currentSong.value ?: return
        if (!current.isDemo) {
            mediaPlayer?.let { mp ->
                try {
                    mp.seekTo(positionMs.toInt())
                    _playbackProgress.value = positionMs
                } catch (e: Exception) {
                    Log.e("AudioPlaybackManager", "Seek failed: ${e.message}")
                }
            }
        } else {
            // Synth is infinite/generative, but we can set progress visually
            _playbackProgress.value = positionMs
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(200)
                val current = _currentSong.value
                val isPlay = _isPlaying.value
                
                if (current != null && isPlay) {
                    if (current.isDemo) {
                        // Advance synth virtual clock
                        val next = _playbackProgress.value + 200
                        if (next >= _playbackDuration.value) {
                            _playbackProgress.value = 0L
                        } else {
                            _playbackProgress.value = next
                        }
                    } else {
                        mediaPlayer?.let { mp ->
                            try {
                                if (mp.isPlaying) {
                                    val currentPos = mp.currentPosition.toLong()
                                    _playbackProgress.value = currentPos
                                    
                                    if (isShuffleActive && mp.duration > 8000 && mp.duration - currentPos <= 6000 && !isFadingOut) {
                                        isFadingOut = true
                                        fadeJob?.cancel()
                                        fadeJob = scope.launch(Dispatchers.Main) {
                                            val steps = 30
                                            val interval = 6000L / steps
                                            for (i in steps downTo 0) {
                                                if (!mp.isPlaying) break
                                                val vol = i.toFloat() / steps
                                                mp.setVolume(vol, vol)
                                                delay(interval)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore state issues
                            }
                        }
                    }
                }
            }
        }
    }



    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error stopping player: ${e.message}")
        }
        mediaPlayer = null
    }

    fun release() {
        scope.cancel()
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {}
        stopMediaPlayer()
        ambientSynthesizer.stop()
    }
}
