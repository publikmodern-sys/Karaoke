package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LrcParser
import com.example.ui.components.KaraokeLyricsStage
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioVuMeterBar
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
import com.example.ui.viewmodel.KaraokeRecordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeRecordScreen(
    trackId: Long,
    viewModel: KaraokeRecordViewModel,
    onBack: () -> Unit,
    onMixCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val vocalMeter by viewModel.vocalMeter.collectAsStateWithLifecycle()
    val musicMeter by viewModel.musicMeter.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(trackId) {
        viewModel.loadTrack(trackId)
    }

    LaunchedEffect(uiState.createdMixId) {
        val mixId = uiState.createdMixId
        if (mixId != null) {
            onMixCreated(mixId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                viewModel.stopAndFinishRecording()
            }
        }
    }

    BackHandler(enabled = isRecording) {
        viewModel.stopAndFinishRecording()
    }

    Scaffold(
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.track?.title ?: "Studio Rekaman",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.track?.artist ?: "Menyiapkan lagu...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isRecording) viewModel.stopAndFinishRecording() else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    StatusBadge(
                        text = if (isRecording) "ON AIR" else "SIAP",
                        containerColor = if (isRecording) CoralPrimary else StudioSurfaceHighest,
                        contentColor = if (isRecording) Color.White else TextSecondary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasMicPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CoralContainer),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Izin Mikrofon Diperlukan",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Aktifkan izin mikrofon agar VocaStage dapat merekam suaramu secara sinkron dengan musik pengiring.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                                modifier = Modifier.testTag("grant_mic_button")
                            ) {
                                Text("Izinkan Mikrofon")
                            }
                        }
                    }
                }

                // Mic acoustic mode & countdown selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.useHeadphoneMode,
                        onClick = { viewModel.setHeadphoneMode(true) },
                        enabled = !isRecording,
                        label = { Text("Headphone (Jernih)") },
                        leadingIcon = {
                            Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MintContainer,
                            selectedLabelColor = OnMintContainer,
                            selectedLeadingIconColor = MintTertiary
                        )
                    )
                    FilterChip(
                        selected = !uiState.useHeadphoneMode,
                        onClick = { viewModel.setHeadphoneMode(false) },
                        enabled = !isRecording,
                        label = { Text("Speaker (AEC)") },
                        leadingIcon = {
                            Icon(Icons.Default.Speaker, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberContainer,
                            selectedLabelColor = OnAmberContainer,
                            selectedLeadingIconColor = AmberSecondary
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = uiState.enableCountdown,
                        onClick = viewModel::toggleCountdownSetting,
                        enabled = !isRecording,
                        label = { Text("3-2-1") },
                        leadingIcon = {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                // Karaoke Lyrics Stage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    KaraokeLyricsStage(
                        lyrics = uiState.lyrics,
                        hasSyncedTimestamps = uiState.hasSyncedLyrics,
                        positionMs = positionMs,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (uiState.countdownRemaining > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(StudioBg.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${uiState.countdownRemaining}",
                                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp),
                                    color = CoralPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Siap bernyanyi...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    if (uiState.isSavingTake) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(StudioBg.copy(alpha = 0.88f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AmberSecondary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Menyiapkan Studio Mixing...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                // Meters & Timeline Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioOutline.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val totalDur = uiState.track?.durationMs ?: 1L
                        val progress = (positionMs.toFloat() / totalDur.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = LrcParser.formatDuration(positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = CoralPrimary
                            )
                            Text(
                                text = LrcParser.formatDuration(totalDur),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = CoralPrimary,
                            trackColor = StudioSurfaceHighest
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudioVuMeterBar(
                                label = "MIKROFON (VOKAL)",
                                level = vocalMeter,
                                accentColor = CoralPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            StudioVuMeterBar(
                                label = "MUSIK PENGIRING",
                                level = musicMeter,
                                accentColor = AmberSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vol. Monitor Musik",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Slider(
                                value = uiState.monitoringVolume,
                                onValueChange = viewModel::setMonitoringVolume,
                                valueRange = 0.1f..1.1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = AmberSecondary,
                                    activeTrackColor = AmberSecondary
                                )
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Transport Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecording) {
                        FilledTonalIconButton(
                            onClick = viewModel::togglePauseRecording,
                            modifier = Modifier
                                .size(56.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("pause_record_btn"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = StudioSurfaceElevated,
                                contentColor = AmberSecondary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Lanjutkan Rekaman" else "Jeda Rekaman",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Button(
                            onClick = viewModel::stopAndFinishRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            shape = CircleShape,
                            modifier = Modifier
                                .height(64.dp)
                                .testTag("stop_record_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Selesai & Mixing", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.startRecordingWithOptionalCountdown()
                                }
                            },
                            enabled = uiState.track != null && uiState.countdownRemaining == 0 && !uiState.isSavingTake,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            shape = CircleShape,
                            modifier = Modifier
                                .height(64.dp)
                                .testTag("start_record_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Mulai Rekam Vokal", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
