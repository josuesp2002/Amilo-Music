package com.example.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.MainActivity
import com.example.AmiloMusicApp
import com.example.database.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

import android.content.BroadcastReceiver
import android.content.IntentFilter

class MediaPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var mediaSession: MediaSessionCompat

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // User requested that unplugging headphones KEEP playing the music.
            // Do not pause on ACTION_AUDIO_BECOMING_NOISY.
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        registerReceiver(noisyReceiver, IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        mediaSession = MediaSessionCompat(this, "MusicService")
        
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                val manager = AmiloMusicApp.instance.playbackManager
                if (!manager.isPlaying.value) {
                    manager.resume {
                        sendBroadcast(Intent("PLAY_NEXT_TRACK").apply { setPackage(packageName) })
                    }
                }
            }
            override fun onPause() {
                AmiloMusicApp.instance.playbackManager.pause()
            }
            override fun onSkipToNext() {
                sendBroadcast(Intent("PLAY_NEXT_TRACK").apply { setPackage(packageName) })
            }
        })
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingMainIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)
        mediaSession.setSessionActivity(pendingMainIntent)

        serviceScope.launch {
            AmiloMusicApp.instance.playbackManager.currentSong.collect { song ->
                if (song != null) {
                    val isPlaying = AmiloMusicApp.instance.playbackManager.isPlaying.value
                    updateMediaSession(song, isPlaying)
                    showNotification(song, isPlaying)
                }
            }
        }
        
        serviceScope.launch {
            AmiloMusicApp.instance.playbackManager.isPlaying.collect { isPlaying ->
                val song = AmiloMusicApp.instance.playbackManager.currentSong.value
                if (song != null) {
                    updateMediaSession(song, isPlaying)
                    showNotification(song, isPlaying)
                }
            }
        }
        
        serviceScope.launch {
            AmiloMusicApp.instance.playbackManager.playbackProgress.collect { progress ->
                val song = AmiloMusicApp.instance.playbackManager.currentSong.value
                val isPlaying = AmiloMusicApp.instance.playbackManager.isPlaying.value
                if (song != null && isPlaying) {
                    updatePlaybackState(isPlaying, progress)
                }
            }
        }
    }
    
    private fun updatePlaybackState(isPlaying: Boolean, progress: Long) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, progress, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SEEK_TO)
                .build()
        )
    }

    private fun updateMediaSession(song: SongEntity, isPlaying: Boolean) {
        updatePlaybackState(isPlaying, AmiloMusicApp.instance.playbackManager.playbackProgress.value)
        
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, AmiloMusicApp.instance.playbackManager.playbackDuration.value)
            
        mediaSession.setMetadata(builder.build())
        mediaSession.isActive = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MUSIC_PLAYBACK",
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun scaleBitmapDown(bitmap: android.graphics.Bitmap?): android.graphics.Bitmap? {
        if (bitmap == null) return null
        val maxDim = 400
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        val width = Math.round(ratio * bitmap.width)
        val height = Math.round(ratio * bitmap.height)
        return android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun showNotification(song: SongEntity, isPlaying: Boolean) {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playAction = Intent(this, MediaPlaybackService::class.java).apply { action = "TOGGLE_PLAY" }
        val pPlayAction = PendingIntent.getService(this, 1, playAction, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextAction = Intent(this, MediaPlaybackService::class.java).apply { action = "NEXT" }
        val pNextAction = PendingIntent.getService(this, 2, nextAction, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingMainIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        var artworkBitmap: android.graphics.Bitmap? = null
        try {
            val customFile = java.io.File(filesDir, "custom_cover_${song.id}.jpg")
            if (customFile.exists()) {
                val bytes = customFile.readBytes()
                val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                artworkBitmap = scaleBitmapDown(rawBitmap)
            } else if (!song.isDemo && song.filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(song.filePath)
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(pfd.fileDescriptor)
                    val bytes = retriever.embeddedPicture
                    retriever.release()
                    if (bytes != null) {
                        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        artworkBitmap = scaleBitmapDown(rawBitmap)
                    }
                }
            }
        } catch (e: Exception) {}

        val builder = NotificationCompat.Builder(this, "MUSIC_PLAYBACK")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(pendingMainIntent)
            .setOngoing(isPlaying)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", pPlayAction)
            .addAction(android.R.drawable.ic_media_next, "Next", pNextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, builder.build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = AmiloMusicApp.instance.playbackManager
        when (intent?.action) {
            "TOGGLE_PLAY" -> {
                if (manager.isPlaying.value) {
                    manager.pause()
                } else {
                    manager.resume {
                        val broadcastIntent = Intent("PLAY_NEXT_TRACK").apply { setPackage(packageName) }
                        sendBroadcast(broadcastIntent)
                    }
                }
            }
            "NEXT" -> {
                val broadcastIntent = Intent("PLAY_NEXT_TRACK").apply { setPackage(packageName) }
                sendBroadcast(broadcastIntent)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(noisyReceiver)
        serviceScope.cancel()
    }
}
