package com.example.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.PlaylistEntity
import com.example.database.SongEntity
import com.example.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (isPlayerExpanded) {
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            } else {
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    // Permission handle logic
    if (isPlayerExpanded) {
        androidx.activity.compose.BackHandler {
            isPlayerExpanded = false
        }
    }
    
    val permissionsToRequest = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true || permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (audioGranted) {
            viewModel.scanLocalSongs(context)
        } else {
            Toast.makeText(context, "Permiso de almacenamiento denegado.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest.toTypedArray())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Screen Content Pages switcher
        Crossfade(
            targetState = activeTab,
            label = "TabTransition",
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 40.dp, bottom = 120.dp)
                .fillMaxHeight()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
        ) { tab ->
            when (tab) {
                0 -> LibraryPage(viewModel, isSearchExpanded, { isSearchExpanded = false }, onScanRequested = { launcher.launch(permissionsToRequest.toTypedArray()) })
                1 -> PlaylistsPage(viewModel)
                2 -> EqualizerPage(viewModel)
                else -> EqualizerPage(viewModel)
            }
        }

        // Floating Navigation & Player
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mini Player Pill
            currentSong?.let { song ->
                AnimatedVisibility(
                    visible = !isPlayerExpanded,
                    enter = scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) + fadeIn(animationSpec = tween(300)),
                    exit = scaleOut(targetScale = 0.9f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) + fadeOut(animationSpec = tween(300))
                ) {
                    MiniPlayerBar(
                        song = song,
                        viewModel = viewModel,
                        onBarClicked = { isPlayerExpanded = true }
                    )
                }
            }
            
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            // Navigation Pill and Search Row
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation Pill
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .border(
                            width = 0.5.dp,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f), // Glassmorphism reflection
                            shape = RoundedCornerShape(32.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) // Semitransparent background
                ) {
                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Icons.Rounded.MusicNote to Icons.Outlined.MusicNote,
                            Icons.Rounded.QueueMusic to Icons.Outlined.QueueMusic,
                            Icons.Rounded.Tune to Icons.Outlined.Tune
                        )

                        tabs.forEachIndexed { index, tab ->
                            val selected = activeTab == index
                            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { activeTab = index }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.first else tab.second,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Search Icon Button
                IconButton(
                    onClick = { 
                        activeTab = 0
                        isSearchExpanded = !isSearchExpanded 
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 0.5.dp,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }


            // Playlist Selection Dialog
            val songToAddToPlaylist by viewModel.showAddToPlaylistDialog.collectAsStateWithLifecycle()
            songToAddToPlaylist?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    playlists = viewModel.playlists.collectAsStateWithLifecycle().value,
                    onDismiss = { viewModel.openAddToPlaylistDialog(null) },
                    onPlaylistSelected = { playlistId ->
                        viewModel.requestAddSongToPlaylist(playlistId, song.id)
                    },
                    onCreatePlaylistRequested = { name ->
                        viewModel.requestCreatePlaylist(name)
                    }
                )
            }
            
            // Edit Metadata Dialog
            val songToEdit by viewModel.songToEdit.collectAsStateWithLifecycle()
            songToEdit?.let { song ->
                EditMetadataDialog(
                    song = song,
                    onDismiss = { viewModel.openEditMetadataDialog(null) },
                    onSave = { updatedSong ->
                        viewModel.updateSongMetadata(updatedSong)
                        viewModel.openEditMetadataDialog(null)
                    }
                )
            }
        // Expanded Player Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = scaleIn(
                initialScale = 0.9f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = tween(300)),
            exit = scaleOut(
                targetScale = 0.9f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            ) + fadeOut(animationSpec = tween(300))
        ) {
            currentSong?.let { song ->
                NowPlayingScreen(
                    song = song,
                    viewModel = viewModel,
                    onCollapse = { isPlayerExpanded = false }
                )
            }
        }
    }
}

// LIBRARY SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPage(viewModel: MusicPlayerViewModel, isSearchExpanded: Boolean, onSearchClose: () -> Unit, onScanRequested: () -> Unit) {
    val context = LocalContext.current
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                (it.album?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Amilo Music",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64B5F6) // Light blue color
                )
                
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when (currentHour) {
                    in 6..11 -> "Buenos días ☕"
                    in 12..18 -> "Buenas tardes ⛅"
                    else -> "Buenas noches 🌙"
                }
                
                Text(
                    text = greeting,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { viewModel.toggleDarkMode() },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f), CircleShape)
            ) {
                val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
                Icon(
                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Cambiar Tema",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Search text field overlay (if expanded)
        AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Buscar canciones, artistas...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            viewModel.updateSearchQuery("") 
                        }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Limpiar")
                        }
                    } else {
                        IconButton(onClick = onSearchClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (filteredSongs.isEmpty()) {
            EmptyLibraryState(onScanRequested)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    val isCurrent = currentSong?.id == song.id
                    
                    // Display artist
                    val displaySubtitle = song.artist
                    
                    SongListItem(
                        song = song.copy(artist = displaySubtitle),
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onPlayClicked = { viewModel.playTrack(song, filteredSongs) },
                        onFavoriteToggle = { viewModel.requestToggleFavorite(song) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistDialog(song) },
                        onEditMetadata = { viewModel.openEditMetadataDialog(song) },
                        onDeleteSong = { viewModel.deleteSong(song, context) },
                        onViewArtist = { viewModel.updateSearchQuery(song.artist) },
                        onViewAlbum = { viewModel.updateSearchQuery(song.album) },
                        onNoRecommend = { viewModel.blacklistSong(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryState(onScanRequested: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sin canciones encontradas",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Haz clic abajo para buscar música local de tu dispositivo (.mp3, .m4a)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onScanRequested,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("scan_button_empty")
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear Almacenamiento")
            }
        }
    }
}

// PLAYLISTS PAGE
@Composable
fun PlaylistsPage(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val songsInSelected by viewModel.songsInSelectedPlaylist.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = selectedPlaylist != null,
        label = "PlaylistScreenTransition"
    ) { isPlaylistDetailsSelected ->
        if (isPlaylistDetailsSelected) {
            // PLAYLIST SONGS LIST CONTAINER
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.selectPlaylist(null) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = selectedPlaylist?.name ?: "Lista",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedPlaylist?.description?.isNotBlank() == true) {
                                Text(
                                    text = selectedPlaylist?.description ?: "",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${songsInSelected.size} canciones",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar Lista")
                    }
                }

                if (showEditDialog) {
                    CreateOrEditPlaylistDialog(
                        playlist = selectedPlaylist,
                        onDismiss = { showEditDialog = false },
                        onSave = { name, desc, coverPath ->
                            selectedPlaylist?.let {
                                viewModel.requestUpdatePlaylist(it, name, desc, it.coverColor, coverPath)
                            }
                            showEditDialog = false
                        }
                    )
                }

                if (songsInSelected.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Lista vacía. Añade canciones de tu biblioteca.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.playTrack(songsInSelected.first(), songsInSelected) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reproducir Lista Completa")
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(songsInSelected, key = { it.id }) { song ->
                            val isCurrent = currentSong?.id == song.id
                            SongListItem(
                                song = song,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                onPlayClicked = { viewModel.playTrack(song, songsInSelected) },
                                onFavoriteToggle = { viewModel.requestToggleFavorite(song) },
                                showRemove = true,
                                onRemoveRequested = { viewModel.requestRemoveSongFromPlaylist(selectedPlaylist!!.id, song.id) },
                                onEditMetadata = { viewModel.openEditMetadataDialog(song) },
                                onDeleteSong = { viewModel.deleteSong(song, context) },
                                onViewArtist = { viewModel.updateSearchQuery(song.artist) },
                                onViewAlbum = { viewModel.updateSearchQuery(song.album) },
                                onNoRecommend = { viewModel.blacklistSong(song) }
                            )
                        }
                    }
                }
            }
        } else {
            // PLAYLISTS GALLERY LIST
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mis Listas",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Colecciones de música offline",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crear")
                    }
                }
                
                OutlinedButton(
                    onClick = { viewModel.generateSmartPlaylist() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generar Flow Inteligente")
                }

                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No tienes listas todavía", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Crea una lista para agrupar tus temas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists, key = { it.id }) { p ->
                            PlaylistCard(
                                playlist = p,
                                onClick = { viewModel.selectPlaylist(p) },
                                onDelete = { viewModel.deletePlaylistEntity(p) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditPlaylistDialog(
            playlist = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, desc, coverPath ->
                viewModel.requestCreatePlaylist(name = name, desc = desc, coverPath = coverPath)
                showCreateDialog = false
            }
        )
    }
}

// EQUALIZER SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerPage(viewModel: MusicPlayerViewModel) {
    val gains by viewModel.equalizerGains.collectAsStateWithLifecycle()
    val isAdaptive by viewModel.isAdaptiveEQEnabled.collectAsStateWithLifecycle()
    val presetName by viewModel.currentPresetName.collectAsStateWithLifecycle()

    val freqs = viewModel.equalizerFrequencies
    val numBands = viewModel.equalizerNumBands

    val standardPresets = remember {
        listOf(
            "Plano" to List(5) { 0.0f },
            "Pop" to listOf(-1.0f, 2.0f, 4.5f, 1.0f, 2.0f),
            "Rock" to listOf(4.0f, 1.5f, -1.5f, 2.0f, 3.5f),
            "Classical" to listOf(3.5f, 2.0f, -1.0f, 1.5f, 3.0f),
            "Jazz" to listOf(2.5f, 1.0f, 1.2f, 2.5f, -1.5f),
            "Bass Booster" to listOf(8.0f, 4.0f, 0.0f, 0.0f, -2.0f),
            "Vocal Booster" to listOf(-3.0f, 1.5f, 5.0f, 3.5f, 0.0f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = "Ecualizador Adaptativo",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Frecuencias de audio física integradas",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Graphic Equalizer Interactive Screen
        InteractiveWaveVisualizer(isAdaptive = isAdaptive, gains = gains)

        Spacer(modifier = Modifier.height(16.dp))

        // Adaptive EQ Master Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAdaptive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (isAdaptive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ajuste Adaptativo Amilo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isAdaptive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAdaptive) "Modulador acústico en vivo activo" else "Ecualización autónoma desactivada",
                            fontSize = 11.sp,
                            color = if (isAdaptive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isAdaptive,
                    onCheckedChange = { viewModel.toggleAdaptiveEQ() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("adaptive_eq_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset List Chips Flow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            standardPresets.forEach { (name, presetGains) ->
                val isSelected = presetName == name
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.applyPreset(name, presetGains) },
                    label = { Text(name) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Individual Sliders
        Text(
            text = "Banda física de Hz",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        for (band in 0 until numBands) {
            val freq = if (band < freqs.size) freqs[band] else 1000
            val gain = if (band < gains.size) gains[band] else 0.0f
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // String label frequency format
                Text(
                    text = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(64.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Slider(
                    value = gain,
                    onValueChange = { viewModel.setEqualizerBandGain(band, it) },
                    valueRange = -15.0f..15.0f,
                    modifier = Modifier.weight(1f).testTag("slider_band_$band"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                Text(
                    text = "${String.format("%.1f", gain)} dB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// SYNTH AMBIENT PAGE
@Composable
fun SynthPage(viewModel: MusicPlayerViewModel) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val playingTrack by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val synthTracks = remember(songs) {
        songs.filter { it.isDemo }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = "Generadores Ambientales",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sintetizadores algorítmicos procedimentales en tiempo real",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (synthTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(synthTracks, key = { it.id }) { track ->
                    val isCurrent = playingTrack?.id == track.id
                    val active = isCurrent && isPlaying
                    
                    SynthTrackItem(
                        track = track,
                        isActive = active,
                        onPlayToggle = {
                            if (isCurrent) {
                                viewModel.togglePlay()
                            } else {
                                viewModel.playTrack(track, synthTracks)
                            }
                        }
                    )
                }
            }
        }
    }
}

// COMPONENTS & VISUALIZERS

@Composable
fun SynthTrackItem(
    track: SongEntity,
    isActive: Boolean,
    onPlayToggle: () -> Unit
) {
    val iconColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayToggle),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(20.dp),
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Custom synth Cover
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFE040FB),
                                        Color(0xFF00E5FF),
                                        Color(0xFF651FFF),
                                        Color(0xFFE040FB)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsInputAntenna,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = track.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.album,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledIconButton(
                    onClick = onPlayToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isActive) "Pausar" else "Reproducir",
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Beautiful Glowing Sound Wave animation inside card when active!
            if (isActive) {
                Spacer(modifier = Modifier.height(16.dp))
                CardComplexVisualizer()
            }
        }
    }
}

@Composable
fun CardComplexVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "CardVisualizer")
    
    val heightScaleList = List(25) { index ->
        infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = 42f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (500..1200).random(),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar_$index"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = size.width / 26
            val barWidth = spacing * 0.55f
            val midY = size.height / 2f

            for (i in 0 until 25) {
                val h = heightScaleList[i].value
                val x = spacing * i + spacing * 0.5f
                
                // Color spectrum flow
                val clr = Color.hsv(
                    hue = (i * 12 + (System.currentTimeMillis() / 24)) % 360f,
                    saturation = 0.82f,
                    value = 0.95f
                )

                drawRoundRect(
                    color = clr,
                    topLeft = Offset(x, midY - h / 2f),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun InteractiveWaveVisualizer(isAdaptive: Boolean, gains: List<Float>) {
    val infiniteTransition = rememberInfiniteTransition(label = "InteractiveVisualizer")
    
    val waveOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SineWaveOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
    ) {
        // Aesthetic Grid Background lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = size.height / 5f
            for (i in 1 until 5) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(0f, gridStep * i),
                    end = Offset(size.width, gridStep * i),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            
            val cols = 8
            val colStep = size.width / cols
            for (i in 1 until cols) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(colStep * i, 0f),
                    end = Offset(colStep * i, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val waveColor = if (isAdaptive) Color(0xFF00E5FF) else primaryColor

        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            val width = size.width
            val height = size.height
            
            // Build wave shape from Equalizer band gain modulations
            val maxBass = (gains.getOrNull(0) ?: 0.0f) + (gains.getOrNull(1) ?: 0.0f)
            val maxMids = gains.getOrNull(2) ?: 0.0f
            val maxHighs = (gains.getOrNull(3) ?: 0.0f) + (gains.getOrNull(4) ?: 0.0f)

            val bassAmp = 15f + maxBass * 2.5f
            val midAmp = 8f + maxMids * 1.5f
            val highAmp = 4f + maxHighs * 1.0f

            val p = androidx.compose.ui.graphics.Path()
            p.moveTo(0f, midY)

            for (x in 0 until width.toInt() step 3) {
                val dx = x.toFloat()
                
                // Polyphonic waves mix
                val bassPart = sin(0.005 * dx - waveOffset.value) * bassAmp
                val midPart = sin(0.02 * dx + waveOffset.value * 1.8) * midAmp
                val highPart = sin(0.08 * dx - waveOffset.value * 3) * highAmp
                
                val damping = sin(Math.PI * (dx / width)) // Envelope so edges are flat
                val totalY = midY + (bassPart + midPart + highPart) * damping
                
                p.lineTo(dx, totalY.toFloat())
            }

            drawPath(
                path = p,
                color = waveColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            // Draw a second glowing helper shadow wave
            val pShadow = androidx.compose.ui.graphics.Path()
            pShadow.moveTo(0f, midY)
            for (x in 0 until width.toInt() step 3) {
                val dx = x.toFloat()
                val bassPart = sin(0.005 * dx - waveOffset.value + 1.2) * (bassAmp * 0.7f)
                val midPart = sin(0.015 * dx - waveOffset.value * 1.2) * (midAmp * 0.8f)
                val damping = sin(Math.PI * (dx / width))
                val totalY = midY + (bassPart + midPart) * damping
                pShadow.lineTo(dx, totalY.toFloat())
            }

            drawPath(
                path = pShadow,
                color = tertiaryColor.copy(alpha = 0.35f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx()
                )
            )
        }

        // Adaptive HUD indicators overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isAdaptive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isAdaptive) "ANÁLISIS EN VIVO" else "ESPECTRO FISICO",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAdaptive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.sp
            )
        }
    }
}

// SONGS LIST ITEM COMPOSABLE
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: SongEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlayClicked: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    showRemove: Boolean = false,
    onRemoveRequested: () -> Unit = {},
    onEditMetadata: () -> Unit = {},
    onDeleteSong: () -> Unit = {},
    onViewArtist: () -> Unit = {},
    onViewAlbum: () -> Unit = {},
    onNoRecommend: () -> Unit = {}
) {
    val indicatorColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val heartColor = if (song.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onPlayClicked,
                onLongClick = { menuExpanded = true }
            ),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Procedural Cover Art Generator instead of raw gray square
            SongCoverImage(
                song = song,
                size = 48.dp,
                isPlaying = isPlaying,
                modifier = Modifier.testTag("song_cover_${song.id}")
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        modifier = Modifier.weight(1f, fill = false),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (song.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Favorito",
                            tint = Color.Red,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.isDemo) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "SYNTH",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Dropdown Menu Anchor
            Box {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), RoundedCornerShape(12.dp))
                ) {
                    if (showRemove) {
                        DropdownMenuItem(
                            text = { Text("Quitar de lista") },
                            onClick = {
                                menuExpanded = false
                                onRemoveRequested()
                            },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Añadir a lista") },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = { 
                            menuExpanded = false 
                            onDeleteSong()
                        },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Ver artista") },
                        onClick = { 
                            menuExpanded = false 
                            onViewArtist()
                        },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Ver álbum") },
                        onClick = { 
                            menuExpanded = false 
                            onViewAlbum()
                        },
                        leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("No recomendar") },
                        onClick = { 
                            menuExpanded = false 
                            onNoRecommend()
                        },
                        leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Editar info") },
                        onClick = { 
                            menuExpanded = false 
                            onEditMetadata()
                        },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
fun MiniGraphicEngineEqualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "MiniEqualizer")
    
    val height1 = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "Bar1"
    )
    val height2 = infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Bar2"
    )
    val height3 = infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar3"
    )

    Canvas(modifier = Modifier.size(20.dp)) {
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalWidth = barWidth * 3 + spacing * 2
        val startX = (size.width - totalWidth) / 2f
        
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)

        // Bar 1
        val h1 = size.height * height1.value
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(startX, size.height - h1),
            size = androidx.compose.ui.geometry.Size(barWidth, h1),
            cornerRadius = cornerRadius
        )

        // Bar 2
        val h2 = size.height * height2.value
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(startX + barWidth + spacing, size.height - h2),
            size = androidx.compose.ui.geometry.Size(barWidth, h2),
            cornerRadius = cornerRadius
        )

        // Bar 3
        val h3 = size.height * height3.value
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(startX + (barWidth + spacing) * 2, size.height - h3),
            size = androidx.compose.ui.geometry.Size(barWidth, h3),
            cornerRadius = cornerRadius
        )
    }
}

// PLAYLIST DETAIL CARD
@Composable
fun PlaylistCard(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(playlist.coverColor))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.coverImagePath != null) {
                val imageModel = if (playlist.coverImagePath!!.startsWith("content://")) {
                    android.net.Uri.parse(playlist.coverImagePath)
                } else {
                    java.io.File(playlist.coverImagePath)
                }
                coil.compose.AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 100f
                            )
                        )
                )
            }
            
            Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = playlist.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(1f, 1f), blurRadius = 3f)
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Lista local",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Borrar lista",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
}


@Composable
fun EditMetadataDialog(
    song: SongEntity,
    onDismiss: () -> Unit,
    onSave: (SongEntity) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var lyrics by remember { mutableStateOf(song.lyrics) }
    
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bytes = input?.readBytes()
                input?.close()
                if (bytes != null) {
                    val customFile = java.io.File(context.filesDir, "custom_cover_${song.id}.jpg")
                    customFile.writeBytes(bytes)
                    val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    rawBitmap?.asImageBitmap()?.let {
                        coverCache.put("${song.id}_small", it) 
                        coverCache.put("${song.id}_large", it) 
                    }
                    Toast.makeText(context, "Portada actualizada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar portada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Editar Metadatos") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Cambiar Portada", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar Portada")
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Álbum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Letras") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(song.copy(title = title, artist = artist, album = album, lyrics = lyrics))
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// EXPANDED NOW PLAYING WRAPPER
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: SongEntity,
    viewModel: MusicPlayerViewModel,
    onCollapse: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val gains by viewModel.equalizerGains.collectAsStateWithLifecycle()
    val isAdaptive by viewModel.isAdaptiveEQEnabled.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "BgAnimation")
    val bgAnimScale by infiniteTransition.animateFloat(
        initialValue = 1.3f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgScale"
    )
    val bgAnimRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgRotation"
    )

    var showLyrics by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080612))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // Animated background from cover art
        SongCoverImage(
            song = song,
            size = Dp.Unspecified,
            isPlaying = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = bgAnimScale
                    scaleY = bgAnimScale
                    rotationZ = bgAnimRotation
                }
        )
        // Dim overlay to ensure text is readable (dark transparent independent of light/dark mode)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Upper Action Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimizar", tint = Color.White)
                }
                
                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "REPRODUCIENDO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { showLyrics = !showLyrics },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (showLyrics) Icons.Rounded.Article else Icons.Outlined.Article,
                        contentDescription = "Letras",
                        tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.requestToggleFavorite(song) },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (song.isFavorite) Color(0xFFFF5252) else Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Large Premium Art Cover Album with animated breathing glow scale effect!
            val infiniteScale = rememberInfiniteTransition(label = "CoverBreathing")
            val scaleFactor by infiniteScale.animateFloat(
                initialValue = 0.98f,
                targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Breathing"
            )

            val pColor = if (song.isDemo) Color(0xFF651FFF) else Color(0xFFFF8A00)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    val scrollState = rememberScrollState()
                    var isSearching by remember { mutableStateOf(false) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp)
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        if (isSearching) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Buscando letras sincronizadas en la web...", color = Color.White)
                                
                                // Mock search result after delay
                                LaunchedEffect(Unit) {
                                    delay(2000)
                                    viewModel.openEditMetadataDialog(song) // let user edit
                                    isSearching = false
                                }
                            }
                        } else if (song.lyrics.isNotBlank()) {
                            Text(
                                text = song.lyrics,
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .verticalScroll(scrollState)
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Ninguna letra encontrada localmente.",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Buscar letras en la web", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    SongCoverImage(
                        song = song,
                        size = 320.dp,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .scale(if (isPlaying) scaleFactor else 1.0f)
                            .shadow(24.dp, shape = RoundedCornerShape(16.dp), ambientColor = pColor, spotColor = pColor)
                    )
                }
            }

            // Song Info Area
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Slider Area
            NowPlayingProgressSlider(viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controllers Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Adaptive EQ Toggle
                    IconButton(
                        onClick = { viewModel.toggleAdaptiveEQ() },
                        modifier = Modifier.size(48.dp).align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "EQ Adaptativo",
                            tint = if (isAdaptive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Centered Playback Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { viewModel.playPreviousTrack() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Primary play action button
                        FilledIconButton(
                            onClick = { viewModel.togglePlay() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color(0xFF080612),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = { viewModel.playNextTrack() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Shuffle Toggle
                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(48.dp).align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (isShuffle) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NowPlayingProgressSlider(viewModel: MusicPlayerViewModel) {
    val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val duration by viewModel.playbackDuration.collectAsStateWithLifecycle()
    val progressRatio = if (duration > 0) progress.toFloat() / duration else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val target = (offset.x / size.width).coerceIn(0f, 1f)
                        viewModel.seekTo((target * duration).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val target = (change.position.x / size.width).coerceIn(0f, 1f)
                        viewModel.seekTo((target * duration).toLong())
                    }
                },
            contentAlignment = androidx.compose.ui.Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.6f),
                                Color(0xFF00E5FF)
                            )
                        )
                    )
            )
            // Liquid thumb glow
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White)
                        .scale(1.2f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatProgress(progress), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            Text(text = formatProgress(duration), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

// MINI PLAY BAR COMPOSABLE
@Composable
fun MiniPlayerBar(
    song: SongEntity,
    viewModel: MusicPlayerViewModel,
    onBarClicked: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val playbackDuration by viewModel.playbackDuration.collectAsStateWithLifecycle()
    
    val progressRatio = if (playbackDuration > 0) playbackProgress.toFloat() / playbackDuration else 0f
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f), // Glassmorphism reflection
                shape = RoundedCornerShape(32.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .clickable { onBarClicked() }
            .testTag("mini_player_bar")
    ) {
        Column {
            // Linear Progress Track
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail Cover
                SongCoverImage(
                    song = song,
                    size = 40.dp,
                    isPlaying = isPlaying,
                    modifier = Modifier.testTag("mini_player_cover")
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.artist,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${formatProgress(playbackProgress)} / ${formatProgress(playbackDuration)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { viewModel.togglePlay() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

// ADD TO PLAYLIST DIALOG
@Composable
fun AddToPlaylistDialog(
    song: SongEntity,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit,
    onCreatePlaylistRequested: (String) -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Añadir a lista",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = song.title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showCreateField) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nombre de la lista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("playlist_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateField = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylistRequested(newPlaylistName)
                                    newPlaylistName = ""
                                    showCreateField = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Guardar")
                        }
                    }
                } else {
                    Button(
                        onClick = { showCreateField = true },
                        modifier = Modifier.fillMaxWidth().testTag("create_playlist_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear nueva lista")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "No tienes listas creadas",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(playlists) { p ->
                                TextButton(
                                    onClick = { onPlaylistSelected(p.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(p.coverColor))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(p.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}

// CREATE PLAYLIST SIMPLE DIALOG
@Composable
fun CreateOrEditPlaylistDialog(
    playlist: PlaylistEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, coverPath: String?) -> Unit
) {
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    var desc by remember { mutableStateOf(playlist?.description ?: "") }
    var coverPath by remember { mutableStateOf(playlist?.coverImagePath) }
    val context = LocalContext.current

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bytes = input?.readBytes()
                input?.close()
                if (bytes != null) {
                    val customFile = java.io.File(context.filesDir, "playlist_cover_${System.currentTimeMillis()}.jpg")
                    customFile.writeBytes(bytes)
                    coverPath = customFile.absolutePath
                }
            } catch (e: Exception) {
                // error copying
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    if (playlist == null) "Nueva Lista de Reproducción" else "Editar Lista",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descripción") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { launcher.launch("image/*") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (coverPath != null) "Cambiar Portada" else "Elegir Portada")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onSave(name, desc, coverPath) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

// HELPER TIME FORMATTER
private fun formatProgress(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / 60000) % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun PlaceholderCover(
    song: SongEntity,
    size: Dp,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 80.dp) 32.dp else if (size > 40.dp) 10.dp else 8.dp))
            .background(
                Brush.linearGradient(
                    colors = if (song.isDemo) {
                        listOf(Color(0xFF651FFF), Color(0xFF00E5FF))
                    } else {
                        val idFactor = song.title.length % 5
                        when (idFactor) {
                            0 -> listOf(Color(0xFFFF8A00), Color(0xFFDA1B60))
                            1 -> listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF))
                            2 -> listOf(Color(0xFFF15BB5), Color(0xFFFEE440))
                            3 -> listOf(Color(0xFF00BBB4), Color(0xFF1E3C72))
                            else -> listOf(Color(0xFF38EF7D), Color(0xFF11998E))
                        }
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying && size > 40.dp) {
            MiniGraphicEngineEqualizer()
        } else {
            val labelText = song.title.take(1).uppercase()
            Text(
                text = if (labelText.isNotBlank()) labelText else "M",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = if (size > 80.dp) 80.sp else if (size > 40.dp) 18.sp else 14.sp
            )
        }
    }
}

val maxMemoryKiloBytes = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemoryKiloBytes / 4 // Use 1/4th of the available memory for this memory cache.

val coverCache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(cacheSize) {
    override fun sizeOf(key: String, bitmap: androidx.compose.ui.graphics.ImageBitmap): Int {
        // The cache size will be measured in kilobytes rather than number of items.
        return (bitmap.width * bitmap.height * 4) / 1024
    }
}

@Composable
fun SongCoverImage(
    song: SongEntity,
    size: Dp,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLarge = size == Dp.Unspecified || size.value > 80f
    val cacheKey = if (isLarge) "${song.id}_large" else "${song.id}_small"
    var bitmap by remember(song.id, isLarge) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(coverCache.get(cacheKey)) }
    var loadedChecked by remember(song.id, isLarge) { mutableStateOf(coverCache.get(cacheKey) != null) }

    LaunchedEffect(song.id, song.filePath, isLarge) {
        if (!isLarge) kotlinx.coroutines.delay(100) // Debounce rapid scrolling only for small
        if (coverCache.get(cacheKey) == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var newBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
                val targetDim = if (isLarge) 800 else 150
                try {
                    val customFile = java.io.File(context.filesDir, "custom_cover_${song.id}.jpg")
                    if (customFile.exists()) {
                        val bytes = customFile.readBytes()
                        val opts = android.graphics.BitmapFactory.Options()
                        opts.inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        val maxDim = targetDim
                        var inSampleSize = 1
                        if (opts.outHeight > maxDim || opts.outWidth > maxDim) {
                            val halfHeight = opts.outHeight / 2
                            val halfWidth = opts.outWidth / 2
                            while (halfHeight / inSampleSize >= maxDim && halfWidth / inSampleSize >= maxDim) {
                                inSampleSize *= 2
                            }
                        }
                        opts.inJustDecodeBounds = false
                        opts.inSampleSize = inSampleSize
                        val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        newBitmap = rawBitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {}

                if (newBitmap == null && !song.isDemo && song.filePath.startsWith("content://")) {
                    try {
                        val uri = android.net.Uri.parse(song.filePath)
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(pfd.fileDescriptor)
                            val bytes = retriever.embeddedPicture
                            retriever.release()
                            if (bytes != null) {
                                val opts = android.graphics.BitmapFactory.Options()
                                opts.inJustDecodeBounds = true
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                
                                val maxDim = targetDim
                                var inSampleSize = 1
                                if (opts.outHeight > maxDim || opts.outWidth > maxDim) {
                                    val halfHeight = opts.outHeight / 2
                                    val halfWidth = opts.outWidth / 2
                                    while (halfHeight / inSampleSize >= maxDim && halfWidth / inSampleSize >= maxDim) {
                                        inSampleSize *= 2
                                    }
                                }
                                
                                opts.inJustDecodeBounds = false
                                opts.inSampleSize = inSampleSize
                                val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                newBitmap = rawBitmap?.asImageBitmap()
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
                
                if (newBitmap != null) {
                    coverCache.put(cacheKey, newBitmap)
                }
                bitmap = newBitmap
                loadedChecked = true
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "Cover for ${song.title}",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(if (size > 80.dp) 32.dp else if (size > 40.dp) 10.dp else 8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        PlaceholderCover(song, size, isPlaying, modifier)
    }
}

