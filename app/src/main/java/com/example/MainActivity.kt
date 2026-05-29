package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.audio.AmbientSynthesizer
import com.example.audio.AudioPlaybackManager
import com.example.audio.EqualizerManager
import com.example.database.MusicDatabase
import com.example.repository.MusicRepository
import com.example.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MusicPlayerViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MusicPlayerViewModel
    
    private val playbackControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "PLAY_NEXT_TRACK" -> viewModel.playNextTrack()
                "TOGGLE_PLAY_TRACK" -> viewModel.togglePlay()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = AmiloMusicApp.instance

        // Custom Safe ViewModel Factory Injection
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val prefs = getSharedPreferences("amilo_prefs", Context.MODE_PRIVATE)
                return MusicPlayerViewModel(app.repository, app.playbackManager, app.equalizerManager, prefs) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[MusicPlayerViewModel::class.java]

        val filter = android.content.IntentFilter().apply {
            addAction("PLAY_NEXT_TRACK")
            addAction("TOGGLE_PLAY_TRACK")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackControlReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackControlReceiver, filter)
        }

        enableEdgeToEdge()
        setContent {
            val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.example.ui.AutoUpdater()
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(playbackControlReceiver)
        // Do not release playbackManager here so it survives when app is closed.
    }
}
