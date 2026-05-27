package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Songs
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: String, isFavorite: Boolean)

    @Query("DELETE FROM songs WHERE isDemo = 1")
    suspend fun clearDemoSongs()

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // Playlist - Song junctions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY s.title ASC
    """)
    fun getSongsInPlaylist(playlistId: Int): Flow<List<SongEntity>>

    // Equalizer Presets
    @Query("SELECT * FROM equalizer_presets ORDER BY name ASC")
    fun getAllEqualizerPresets(): Flow<List<EqualizerPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEqualizerPreset(preset: EqualizerPresetEntity)

    @Delete
    suspend fun deleteEqualizerPreset(preset: EqualizerPresetEntity)
}
