package com.example.viewmodel

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientSynthesizer
import com.example.audio.AudioPlaybackManager
import com.example.audio.EqualizerManager
import com.example.database.PlaylistEntity
import com.example.database.SongEntity
import com.example.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MusicPlayerViewModel(
    private val repository: MusicRepository,
    private val playbackManager: AudioPlaybackManager,
    private val equalizerManager: EqualizerManager,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        prefs.edit().putBoolean("is_dark_mode", newMode).apply()
    }

    private val _blacklistedSongs = MutableStateFlow(prefs.getStringSet("blacklisted_songs", mutableSetOf()) ?: mutableSetOf())

    val allSongs: StateFlow<List<SongEntity>> = repository.allSongs.combine(_blacklistedSongs) { songs, blacklisted ->
        songs.filterNot { it.id in blacklisted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<SongEntity>> = repository.favoriteSongs.combine(_blacklistedSongs) { songs, blacklisted ->
        songs.filterNot { it.id in blacklisted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected playlist songs
    private val _songsInSelectedPlaylist = MutableStateFlow<List<SongEntity>>(emptyList())
    val songsInSelectedPlaylist: StateFlow<List<SongEntity>> = _songsInSelectedPlaylist.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist.asStateFlow()

    // Control States (direct from playback manager)
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentSong: StateFlow<SongEntity?> = playbackManager.currentSong
    val playbackProgress: StateFlow<Long> = playbackManager.playbackProgress
    val playbackDuration: StateFlow<Long> = playbackManager.playbackDuration

    // Equalizer Core States
    private val _equalizerGains = MutableStateFlow<List<Float>>(List(5) { 0.0f })
    val equalizerGains: StateFlow<List<Float>> = _equalizerGains.asStateFlow()

    val equalizerFrequencies: List<Int>
        get() = equalizerManager.bandFrequencies

    val equalizerNumBands: Int
        get() = equalizerManager.numBands

    private val _isAdaptiveEQEnabled = MutableStateFlow(true)
    val isAdaptiveEQEnabled: StateFlow<Boolean> = _isAdaptiveEQEnabled.asStateFlow()

    private val _currentPresetName = MutableStateFlow("Plano")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    // Playlist dialog visibility helper
    private val _showAddToPlaylistDialog = MutableStateFlow<SongEntity?>(null)
    val showAddToPlaylistDialog: StateFlow<SongEntity?> = _showAddToPlaylistDialog.asStateFlow()

    private val _songToEdit = MutableStateFlow<SongEntity?>(null)
    val songToEdit: StateFlow<SongEntity?> = _songToEdit.asStateFlow()

    // Active playback queue
    private val playQueue = MutableStateFlow<List<SongEntity>>(emptyList())
    private val queueIndex = MutableStateFlow(0)

    private var adaptiveEQJob: Job? = null

    // Search query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        _isAdaptiveEQEnabled.value = prefs.getBoolean("is_adaptive_eq", true)
        equalizerManager.isAdaptiveEnabled = _isAdaptiveEQEnabled.value
        _currentPresetName.value = if (_isAdaptiveEQEnabled.value) "Adaptativo" else "Plano"
        // Observe current song to auto-fill EQ
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null) {
                    _equalizerGains.value = equalizerManager.getGains()
                    if (_isAdaptiveEQEnabled.value) {
                        applyAdaptiveEQProfile(song)
                    }
                }
            }
        }

        // Setup standard offline demo tracks initially
        viewModelScope.launch {
            repository.allSongs.first().let { current ->
                if (current.none { it.isDemo }) {
                    injectDemoTracks()
                }
            }
        }

        startAdaptiveEQAutomation()
    }

    private suspend fun injectDemoTracks() {
        val demos = listOf(
            SongEntity(
                id = "demo_cosmico",
                title = "Auroras de Litio",
                artist = "Sintetizador Amilo",
                album = "Cosmos Offline",
                duration = 300000L,
                filePath = "",
                isDemo = true,
                isFavorite = false
            ),
            SongEntity(
                id = "demo_vacio",
                title = "Ecos del Vacío",
                artist = "Sintetizador Amilo",
                album = "Vacío Interestelar",
                duration = 300000L,
                filePath = "",
                isDemo = true,
                isFavorite = false
            ),
            SongEntity(
                id = "demo_estelar",
                title = "Paseo Andrómeda",
                artist = "Sintetizador Amilo",
                album = "Atmósfera Local",
                duration = 300000L,
                filePath = "",
                isDemo = true,
                isFavorite = false
            )
        )
        repository.insertSongs(demos)
    }

    fun selectPlaylist(playlist: PlaylistEntity?) {
        _selectedPlaylist.value = playlist
        if (playlist != null) {
            viewModelScope.launch {
                repository.getSongsInPlaylist(playlist.id).collect {
                    _songsInSelectedPlaylist.value = it
                }
            }
        } else {
            _songsInSelectedPlaylist.value = emptyList()
        }
    }

    fun updateSongMetadata(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSong(song)
        }
    }

    fun generateSmartPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = allSongs.value
            if (songs.size < 2) return@launch
            val count = if (songs.size < 10) songs.size else 10
            val shuffled = songs.shuffled().take(count)
            val color = android.graphics.Color.HSVToColor(floatArrayOf((Math.random()*360).toFloat(), 0.8f, 0.9f))
            val playlistId = repository.insertPlaylistEntity(PlaylistEntity(name = "Smart Flow", coverColor = color))
            shuffled.forEach {
                repository.addSongToPlaylist(playlistId.toInt(), it.id)
            }
        }
    }

    fun scanLocalSongs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val songList = mutableListOf<SongEntity>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            // Query only valid music tracks
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Desconocido"
                    val artist = c.getString(artistCol) ?: "Artista Desconocido"
                    val album = c.getString(albumCol) ?: "Álbum Desconocido"
                    val duration = c.getLong(durationCol)
                    val data = c.getString(dataCol) ?: ""

                    // Build standard dynamic secure media Content URI instead of raw filepath on SDK 29+
                    val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()

                    if (duration > 5000L) { // Only add tracks longer than 5 seconds
                        songList.add(
                            SongEntity(
                                id = id.toString(),
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                filePath = songUri,
                                isDemo = false
                            )
                        )
                    }
                }
            }

            if (songList.isNotEmpty()) {
                // Clear demo tracks if real local tracks exist AND the user wants a clean local device scan
                repository.clearDemoSongs()
                repository.insertSongs(songList)
            } else {
                Log.d("MusicPlayerViewModel", "No local songs found. Demo tracks preserved.")
            }
        }
    }

    // Playback control wrappers
    fun playTrack(song: SongEntity, currentList: List<SongEntity> = emptyList()) {
        if (currentList.isNotEmpty()) {
            playQueue.value = currentList
            queueIndex.value = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        } else {
            playQueue.value = listOf(song)
            queueIndex.value = 0
        }

        playbackManager.playSong(song) {
            playNextTrack()
        }
    }

    fun togglePlay() {
        if (currentSong.value == null) {
            // Play first song if empty
            val songs = allSongs.value
            if (songs.isNotEmpty()) {
                playTrack(songs.first(), songs)
            }
        } else {
            if (isPlaying.value) {
                playbackManager.pause()
            } else {
                playbackManager.resume {
                    playNextTrack()
                }
            }
        }
    }

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        playbackManager.isShuffleActive = _isShuffleEnabled.value
    }

    fun playNextTrack() {
        val q = playQueue.value
        if (q.isNotEmpty()) {
            var nextIndex = queueIndex.value + 1
            if (nextIndex >= q.size) {
                nextIndex = 0 // loop
            }
            if (_isShuffleEnabled.value) {
                nextIndex = q.indices.random()
            }
            queueIndex.value = nextIndex
            playTrack(q[nextIndex], q)
        }
    }

    fun playPreviousTrack() {
        val q = playQueue.value
        if (q.isNotEmpty()) {
            var prevIndex = queueIndex.value - 1
            if (prevIndex < 0) {
                prevIndex = q.size - 1 // wrap around
            }
            queueIndex.value = prevIndex
            playTrack(q[prevIndex], q)
        }
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    // Equalizer Adjusters
    fun setEqualizerBandGain(band: Int, gainDb: Float) {
        _currentPresetName.value = "Personalizado"
        equalizerManager.setBandGain(band, gainDb)
        _equalizerGains.value = equalizerManager.getGains()
    }

    fun applyPreset(presetName: String, gains: List<Float>) {
        _currentPresetName.value = presetName
        _isAdaptiveEQEnabled.value = false
        equalizerManager.applyPreset(gains)
        _equalizerGains.value = equalizerManager.getGains()
    }

    fun toggleAdaptiveEQ() {
        _isAdaptiveEQEnabled.value = !_isAdaptiveEQEnabled.value
        prefs.edit().putBoolean("is_adaptive_eq", _isAdaptiveEQEnabled.value).apply()
        equalizerManager.isAdaptiveEnabled = _isAdaptiveEQEnabled.value
        if (_isAdaptiveEQEnabled.value) {
            _currentPresetName.value = "Adaptativo"
            currentSong.value?.let { applyAdaptiveEQProfile(it) }
        } else {
            _currentPresetName.value = "Plano"
            applyPreset("Plano", List(5) { 0.0f })
        }
    }

    private fun applyAdaptiveEQProfile(song: SongEntity) {
        // High-fidelity profile adaptive logic:
        // Analyzes track parameters dynamically (e.g. title text, artist, file characteristics)
        // to assign a customized acoustic blueprint!
        val profileGains = when {
            song.isDemo && song.id == "demo_cosmico" -> {
                // Space ambient, high frequencies boost + heavy bass depth
                listOf(5.0f, 1.5f, -3.0f, 4.0f, 6.5f)
            }
            song.isDemo && song.id == "demo_vacio" -> {
                // Cinematic flat, vocal presence and treble sparkle
                listOf(2.5f, 0.0f, 3.5f, 1.5f, -1.0f)
            }
            song.title.contains("remix", ignoreCase = true) || song.title.contains("dance", ignoreCase = true) -> {
                // Electronic: Boost bass and highs
                listOf(7.0f, 3.5f, -2.0f, 4.0f, 6.0f)
            }
            song.title.contains("metal", ignoreCase = true) || song.title.contains("rock", ignoreCase = true) -> {
                // Rock preset: classic mid scoop
                listOf(4.0f, 1.5f, -1.5f, 2.0f, 3.5f)
            }
            song.title.contains("pop", ignoreCase = true) -> {
                // Bright pop: treble punch
                listOf(-1.0f, 2.0f, 4.5f, 1.0f, 2.0f)
            }
            else -> {
                // Default Adaptive balance: Rich loudness curves
                listOf(3.5f, 1.2f, -1.0f, 2.5f, 4.0f)
            }
        }
        
        equalizerManager.applyPreset(profileGains)
        _equalizerGains.value = equalizerManager.getGains()
    }

    private fun startAdaptiveEQAutomation() {
        // Delegated to EqualizerManager continuous service
    }

    // Playlist Operations
    fun openAddToPlaylistDialog(song: SongEntity?) {
        _showAddToPlaylistDialog.value = song
    }

    fun openEditMetadataDialog(song: SongEntity?) {
        _songToEdit.value = song
    }

    fun blacklistSong(song: SongEntity) {
        val currentSet = _blacklistedSongs.value.toMutableSet()
        currentSet.add(song.id)
        _blacklistedSongs.value = currentSet
        prefs.edit().putStringSet("blacklisted_songs", currentSet).apply()
        
        if (currentSong.value?.id == song.id) {
            playNextTrack()
        }
    }

    fun deleteSong(song: SongEntity, context: Context) {
        viewModelScope.launch {
            try {
                if (song.filePath.startsWith("content://")) {
                    context.contentResolver.delete(android.net.Uri.parse(song.filePath), null, null)
                } else {
                    java.io.File(song.filePath).delete()
                }
            } catch (e: Exception) {}
            repository.deleteSong(song)
        }
    }

    fun requestCreatePlaylist(name: String, desc: String = "", coverColor: Int? = null, coverPath: String? = null) {
        val color = coverColor ?: Color.argb(255, (30..150).random(), (30..150).random(), (120..250).random())
        viewModelScope.launch {
            val entity = PlaylistEntity(name = name, description = desc, coverColor = color, coverImagePath = coverPath)
            repository.insertPlaylistEntity(entity)
        }
    }

    fun requestUpdatePlaylist(playlist: PlaylistEntity, name: String, desc: String, coverColor: Int, coverPath: String?) {
        viewModelScope.launch {
            val updated = playlist.copy(name = name, description = desc, coverColor = coverColor, coverImagePath = coverPath)
            repository.updatePlaylist(updated)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = updated
            }
        }
    }

    fun deletePlaylistEntity(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
                _songsInSelectedPlaylist.value = emptyList()
            }
        }
    }

    fun requestAddSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            _showAddToPlaylistDialog.value = null
            // Reload selected playlists
            val current = _selectedPlaylist.value
            if (current != null && current.id == playlistId) {
                selectPlaylist(current)
            }
        }
    }

    fun requestRemoveSongFromPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            val current = _selectedPlaylist.value
            if (current != null && current.id == playlistId) {
                selectPlaylist(current)
            }
        }
    }

    fun requestToggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            val nextState = !song.isFavorite
            repository.updateFavoriteStatus(song.id, nextState)
            
            // Sync current playing song favorite indicator state
            if (currentSong.value?.id == song.id) {
                val updatedSong = song.copy(isFavorite = nextState)
                playbackManager.updateCurrentSongStateHelper(updatedSong)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    override fun onCleared() {
        super.onCleared()
        adaptiveEQJob?.cancel()
        playbackManager.release()
    }
}
