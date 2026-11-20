package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LrcParser
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnMintContainer
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TrackEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackEditorScreen(
    trackId: Long,
    viewModel: TrackEditorViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncPlaying by viewModel.isSyncPlaying.collectAsStateWithLifecycle()
    val syncPosMs by viewModel.syncPositionMs.collectAsStateWithLifecycle()

    LaunchedEffect(trackId) {
        if (trackId > 0L) {
            viewModel.loadExistingTrack(trackId)
        }
    }

    LaunchedEffect(uiState.savedTrackId) {
        val savedId = uiState.savedTrackId
        if (savedId != null) {
            onSaved(savedId)
        }
    }

    BackHandler(enabled = uiState.isTapSyncActive) {
        viewModel.exitTapSyncMode()
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onSongUriPicked(uri)
        }
    }

    Scaffold(
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            uiState.isTapSyncActive -> "Tap-to-Sync Lirik"
                            trackId > 0L -> "Edit Lagu & Lirik"
                            else -> "Impor Lagu Baru"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isTapSyncActive) viewModel.exitTapSyncMode() else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (uiState.isTapSyncActive) {
                TapSyncLyricsContent(
                    viewModel = viewModel,
                    isPlaying = isSyncPlaying,
                    positionMs = syncPosMs,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.editingTrackId == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioSurface),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StudioOutline, RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "1. File Audio Lagu (Musik Pengiring)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Mendukung MP3, WAV, M4A, AAC, OGG, dan FLAC. Audio akan didekode ke PCM 44.1kHz stereo agar sinkron tanpa lag.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.selectedUri != null) MintContainer else CoralPrimary,
                                        contentColor = if (uiState.selectedUri != null) OnMintContainer else Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pick_audio_button")
                                ) {
                                    Icon(Icons.Default.AudioFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (uiState.selectedUri != null)
                                            "Audio Terpilih (${LrcParser.formatDuration(uiState.durationMs)}) — Ganti File"
                                        else
                                            "Pilih File Lagu dari Perangkat"
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioOutline, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (uiState.editingTrackId == null) "2. Info & Lirik Lagu" else "Info & Lirik Lagu",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoralPrimary,
                                unfocusedBorderColor = StudioOutline,
                                focusedLabelColor = CoralPrimary,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                            OutlinedTextField(
                                value = uiState.title,
                                onValueChange = viewModel::updateTitle,
                                label = { Text("Judul Lagu") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_song_title")
                            )
                            OutlinedTextField(
                                value = uiState.artist,
                                onValueChange = viewModel::updateArtist,
                                label = { Text("Artis / Penyanyi Asli") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_song_artist")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lirik (Teks Biasa atau LRC [mm:ss.xx])",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary
                                )
                                TextButton(onClick = viewModel::insertSampleLyricsTemplate) {
                                    Text("Contoh LRC", color = AmberSecondary)
                                }
                            }

                            OutlinedTextField(
                                value = uiState.lyricsText,
                                onValueChange = viewModel::updateLyricsText,
                                placeholder = {
                                    Text(
                                        "Tempel lirik di sini...\nContoh LRC:\n[00:12.50] Baris pertama lagu\n[00:17.20] Baris kedua lagu\n\nAtau cukup tulis baris demi baris tanpa waktu.",
                                        color = TextMuted
                                    )
                                },
                                minLines = 7,
                                maxLines = 14,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_song_lyrics")
                            )

                            if (uiState.backingPcmPath != null) {
                                OutlinedButton(
                                    onClick = viewModel::enterTapSyncMode,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("tap_sync_mode_btn")
                                ) {
                                    Icon(
                                        Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = AmberSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Buka Mode Tap-to-Sync Lirik Interaktif", color = AmberSecondary)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    AnimatedVisibility(visible = uiState.isImporting) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Mendekode Audio ke Studio PCM 44.1kHz...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AmberSecondary
                                )
                                LinearProgressIndicator(
                                    progress = { uiState.importProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = CoralPrimary,
                                    trackColor = StudioSurfaceHighest
                                )
                                Text(
                                    text = "${(uiState.importProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = viewModel::saveTrack,
                        enabled = !uiState.isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_track_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.editingTrackId != null) "Simpan Perubahan" else "Simpan & Proses Lagu",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TapSyncLyricsContent(
    viewModel: TrackEditorViewModel,
    isPlaying: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lines = uiState.syncLines
    val cursor = uiState.currentSyncCursor

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Waktu Lagu", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = LrcParser.formatTimestamp(positionMs),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AmberSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = viewModel::resetTapSyncCursor) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ulang")
                    }
                    Button(
                        onClick = viewModel::toggleSyncPlayback,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) AmberSecondary else CoralPrimary,
                            contentColor = if (isPlaying) Color(0xFF1E1202) else Color.White
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPlaying) "Jeda" else "Putar")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(StudioSurface),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == cursor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) CoralContainer else StudioSurfaceElevated)
                        .border(
                            width = if (isCurrent) 1.5.dp else 0.dp,
                            color = if (isCurrent) CoralPrimary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = line.timeMs?.let { LrcParser.formatTimestamp(it) } ?: "[--:--.--]",
                        containerColor = if (line.timeMs != null) MintContainer else StudioSurfaceHighest,
                        contentColor = if (line.timeMs != null) OnMintContainer else TextMuted
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isCurrent) AmberSecondary else TextPrimary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = viewModel::stampCurrentLine,
                enabled = cursor < lines.size,
                colors = ButtonDefaults.buttonColors(containerColor = AmberSecondary, contentColor = Color(0xFF1F1302)),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("stamp_lyric_line_btn")
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (cursor < lines.size) "Tandai Baris #${cursor + 1}" else "Semua Baris Selesai",
                    fontWeight = FontWeight.Bold
                )
            }
            FilledTonalButton(
                onClick = viewModel::exitTapSyncMode,
                modifier = Modifier.height(54.dp)
            ) {
                Text("Terapkan")
            }
        }
    }
}
