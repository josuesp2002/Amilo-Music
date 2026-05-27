package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val filePath: String,
    val lyrics: String = "",
    val isDemo: Boolean = false,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val coverColor: Int, // ARGB hex integer value for aesthetic visual theme
    val coverImagePath: String? = null
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: String
)

@Entity(tableName = "equalizer_presets")
data class EqualizerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val bandGains: String // Comma-separated Float string (e.g. "2.5,0.0,-1.5...")
)
