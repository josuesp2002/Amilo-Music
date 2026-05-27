package com.example.repository

import com.example.database.*
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {

    val allSongs: Flow<List<SongEntity>> = musicDao.getAllSongs()
    val favoriteSongs: Flow<List<SongEntity>> = musicDao.getFavoriteSongs()
    val allPlaylists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
    val allEqualizerPresets: Flow<List<EqualizerPresetEntity>> = musicDao.getAllEqualizerPresets()

    suspend fun insertSongs(songs: List<SongEntity>) {
        musicDao.insertSongs(songs)
    }

    suspend fun updateSong(song: SongEntity) {
        musicDao.updateSong(song)
    }

    suspend fun updateFavoriteStatus(songId: String, isFavorite: Boolean) {
        musicDao.updateFavoriteStatus(songId, isFavorite)
    }

    suspend fun clearDemoSongs() {
        musicDao.clearDemoSongs()
    }

    suspend fun deleteSong(song: SongEntity) {
        musicDao.deleteSong(song)
        try {
            val file = java.io.File(song.filePath)
            if (file.exists()) {
                file.delete() // delete from local storage as requested
            }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to delete file")
        }
    }

    suspend fun insertPlaylistEntity(playlist: PlaylistEntity): Long {
        return musicDao.insertPlaylist(playlist)
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        musicDao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        musicDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: String) {
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<SongEntity>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    suspend fun insertEqualizerPreset(name: String, gains: List<Float>) {
        val gainsStr = gains.joinToString(",") { it.toString() }
        musicDao.insertEqualizerPreset(EqualizerPresetEntity(name = name, bandGains = gainsStr))
    }

    suspend fun deleteEqualizerPreset(preset: EqualizerPresetEntity) {
        musicDao.deleteEqualizerPreset(preset)
    }
}
