package com.example

import android.app.Application
import com.example.audio.AmbientSynthesizer
import com.example.audio.AudioPlaybackManager
import com.example.audio.EqualizerManager
import com.example.database.MusicDatabase
import com.example.repository.MusicRepository

class AmiloMusicApp : Application() {
    lateinit var db: MusicDatabase
    lateinit var repository: MusicRepository
    lateinit var equalizerManager: EqualizerManager
    lateinit var ambientSynthesizer: AmbientSynthesizer
    lateinit var playbackManager: AudioPlaybackManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = MusicDatabase.getInstance(this)
        repository = MusicRepository(db.musicDao)
        equalizerManager = EqualizerManager()
        ambientSynthesizer = AmbientSynthesizer()
        playbackManager = AudioPlaybackManager(this, equalizerManager, ambientSynthesizer)
    }

    companion object {
        @JvmStatic
        lateinit var instance: AmiloMusicApp
            private set
    }
}
