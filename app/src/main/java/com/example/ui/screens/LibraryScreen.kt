package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.KaraokeTrackEntity
import com.example.data.local.StudioPreferences
import com.example.data.local.VocalMixWithTrack
import com.example.data.model.LrcParser
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnMintContainer
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onImportNewSong: () -> Unit,
    onEditTrack: (Long) -> Unit,
    onRecordTrack: (Long) -> Unit,
    onOpenMixStudio: (Long) -> Unit
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val mixes by viewModel.mixes.collectAsStateWithLifecycle()
    val prefs by viewModel.studioPrefs.collectAsStateWithLifecycle()
    val playingMixId by viewModel.playingMixId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayerPlaying.collectAsStateWithLifecycle()
    val playerPos by viewModel.playerPositionMs.collectAsStateWithLifecycle()
    val playerDur by viewModel.playerDurationMs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showStudioSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onImportNewSong,
                    containerColor = CoralPrimary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Impor Lagu", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.testTag("fab_import_song")
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = StudioSurface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Daftar Lagu") },
                    label = { Text("Daftar Lagu (${tracks.size})") },
                    modifier = Modifier.testTag("nav_tab_tracks"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CoralPrimary,
                        selectedTextColor = CoralPrimary,
                        indicatorColor = CoralContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Equalizer, contentDescription = "Hasil Mix MP3") },
                    label = { Text("Hasil Mix (${mixes.size})") },
                    modifier = Modifier.testTag("nav_tab_mixes"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AmberSecondary,
                        selectedTextColor = AmberSecondary,
                        indicatorColor = AmberContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    StudioHeroBanner(
                        trackCount = tracks.size,
                        mixCount = mixes.size,
                        onOpenSettings = { showStudioSettings = true },
                        onImportClick = onImportNewSong
                    )
                }

                if (selectedTab == 0) {
                    if (tracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Belum Ada Lagu Karaoke",
                                description = "Impor file lagu (MP3, M4A, WAV, OGG) dari HP kamu, tambahkan lirik, lalu langsung rekam suaramu dengan kualitas studio.",
                                actionLabel = "Pilih Lagu dari Perangkat",
                                onAction = onImportNewSong
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "KOLEKSI LAGU PENGIRING",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        items(tracks, key = { it.id }) { track ->
                            TrackLibraryCard(
                                track = track,
                                onRecord = { onRecordTrack(track.id) },
                                onEdit = { onEditTrack(track.id) },
                                onDelete = { viewModel.deleteTrack(track) }
                            )
                        }
                    }
                } else {
                    if (mixes.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Belum Ada Hasil Mixing",
                                description = "Rekam suaramu pada salah satu lagu di tab Daftar Lagu. Hasilnya bisa langsung di-mixing dengan EQ, Reverb, dan diunduh ke MP3.",
                                actionLabel = "Ke Daftar Lagu",
                                onAction = { selectedTab = 0 }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "REKAMAN VOKAL & EKSPOR MP3",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        items(mixes, key = { it.id }) { mix ->
                            val isCurrent = playingMixId == mix.id && isPlaying
                            val progress = if (playingMixId == mix.id && playerDur > 0) {
                                (playerPos.toFloat() / playerDur.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            MixHistoryCard(
                                mix = mix,
                                isPlaying = isCurrent,
                                playbackProgress = progress,
                                onTogglePlay = { viewModel.togglePlayExportedMix(mix) },
                                onOpenMixer = {
                                    viewModel.stopPlayback()
                                    onOpenMixStudio(mix.id)
                                },
                                onShareMp3 = {
                                    val path = mix.exportedMp3Path ?: return@MixHistoryCard
                                    val intent = viewModel.buildShareMp3Intent(path, mix.mixName)
                                    if (intent != null) {
                                        context.startActivity(
                                            android.content.Intent.createChooser(intent, "Bagikan MP3 VocaStage")
                                        )
                                    }
                                },
                                onDelete = { viewModel.deleteMix(mix.id) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(76.dp)) }
            }
        }
    }

    if (showStudioSettings) {
        StudioCalibrationDialog(
            prefs = prefs,
            onSyncOffsetChange = viewModel::updateDefaultSyncOffset,
            onHeadphoneModeChange = viewModel::updateHeadphoneMode,
            onBitrateChange = viewModel::updateDefaultBitrate,
            onCountdownChange = viewModel::updateCountdownEnabled,
            onDismiss = { showStudioSettings = false }
        )
    }
}

@Composable
private fun StudioHeroBanner(
    trackCount: Int,
    mixCount: Int,
    onOpenSettings: () -> Unit,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, StudioOutline, RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = "Latar studio rekaman VocaStage",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x660B0D14),
                            Color(0xE60B0D14)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = "44.1kHz STUDIO DSP",
                    containerColor = CoralContainer.copy(alpha = 0.9f),
                    contentColor = OnCoralContainer
                )
                FilledTonalIconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("studio_settings_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = StudioSurfaceElevated.copy(alpha = 0.85f),
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Kalibrasi Audio Studio")
                }
            }

            Column {
                Text(
                    text = "VocaStage Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$trackCount lagu siap nyanyi • $mixCount rekaman vokal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("hero_import_button")
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Input Lagu & Lirik")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackLibraryCard(
    track: KaraokeTrackEntity,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val parsedLyrics = remember(track.lyricsRaw) { LrcParser.parse(track.lyricsRaw) }
    val hasSynced = remember(parsedLyrics) { LrcParser.hasTimestamps(parsedLyrics) }
    val lyricBadgeText = when {
        parsedLyrics.isEmpty() -> "Tanpa Lirik"
        hasSynced -> "Lirik Sinkron (${parsedLyrics.size})"
        else -> "Lirik Teks (${parsedLyrics.size})"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioOutline.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .testTag("track_card_${track.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vinyl_cover),
                    contentDescription = "Sampul lagu ${track.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            text = LrcParser.formatDuration(track.durationMs),
                            containerColor = StudioSurfaceHighest,
                            contentColor = AmberSecondary
                        )
                        StatusBadge(
                            text = lyricBadgeText,
                            containerColor = if (hasSynced) MintContainer else StudioSurfaceHighest,
                            contentColor = if (hasSynced) OnMintContainer else TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus lagu ${track.title}",
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_track_btn_${track.id}"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StudioSurfaceElevated,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit & Sync Lirik")
                }
                Button(
                    onClick = onRecord,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("record_track_btn_${track.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rekam Karaoke")
                }
            }
        }
    }
}

@Composable
private fun MixHistoryCard(
    mix: VocalMixWithTrack,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlay: () -> Unit,
    onOpenMixer: () -> Unit,
    onShareMp3: () -> Unit,
    onDelete: () -> Unit
) {
    val isExported = !mix.exportedMp3Path.isNullOrBlank()
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioOutline.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .testTag("mix_card_${mix.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isExported) MintContainer else AmberContainer)
                        .clickable(enabled = isExported, onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            !isExported -> Icons.Default.Tune
                            isPlaying -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) "Jeda MP3" else "Putar MP3",
                        tint = if (isExported) MintTertiary else AmberSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mix.mixName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${mix.trackTitle} • ${LrcParser.formatDuration(mix.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = if (isExported) "MP3 ${mix.mp3BitrateKbps} kbps" else "Belum Diekspor",
                            containerColor = if (isExported) MintContainer else AmberContainer,
                            contentColor = if (isExported) OnMintContainer else OnAmberContainer
                        )
                        StatusBadge(
                            text = "Sync ${mix.syncOffsetMs}ms",
                            containerColor = StudioSurfaceHighest,
                            contentColor = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus mix ${mix.mixName}",
                        tint = TextMuted
                    )
                }
            }

            AnimatedVisibility(visible = isPlaying) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    LinearProgressIndicator(
                        progress = { playbackProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = MintTertiary,
                        trackColor = StudioSurfaceHighest
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenMixer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_mixer_btn_${mix.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberSecondary,
                        contentColor = Color(0xFF1F1302)
                    )
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Studio Mixing & MP3", fontWeight = FontWeight.Bold)
                }
                if (isExported) {
                    FilledTonalIconButton(
                        onClick = onShareMp3,
                        modifier = Modifier
                            .size(44.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("share_mp3_btn_${mix.id}"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = StudioSurfaceElevated,
                            contentColor = MintTertiary
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Bagikan MP3")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioOutline.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CoralContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun StudioCalibrationDialog(
    prefs: StudioPreferences,
    onSyncOffsetChange: (Int) -> Unit,
    onHeadphoneModeChange: (Boolean) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onCountdownChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Text("Kalibrasi Studio Audio", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mode Headphone (Studio)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Kualitas penuh tanpa pemotongan frekuensi suara. Matikan jika memakai speaker HP agar anti-feedback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = prefs.useHeadphoneMode,
                        onCheckedChange = onHeadphoneModeChange
                    )
                }

                HorizontalDivider(color = StudioOutline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hitung Mundur 3-2-1", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Beri jeda persiapan sebelum musik mulai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = prefs.enableCountdown,
                        onCheckedChange = onCountdownChange
                    )
                }

                HorizontalDivider(color = StudioOutline)

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Offset Latensi Default", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${prefs.defaultSyncOffsetMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberSecondary
                        )
                    }
                    Slider(
                        value = prefs.defaultSyncOffsetMs.toFloat(),
                        onValueChange = { onSyncOffsetChange(it.toInt()) },
                        valueRange = -250f..250f,
                        colors = SliderDefaults.colors(
                            thumbColor = AmberSecondary,
                            activeTrackColor = AmberSecondary
                        )
                    )
                }

                Column {
                    Text("Kualitas MP3 Default", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(192, 256, 320).forEach { br ->
                            val selected = prefs.defaultBitrateKbps == br
                            FilledTonalButton(
                                onClick = { onBitrateChange(br) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) CoralPrimary else StudioSurfaceHighest,
                                    contentColor = if (selected) Color.White else TextSecondary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("${br}k")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Selesai", color = CoralPrimary)
            }
        }
    )
}
