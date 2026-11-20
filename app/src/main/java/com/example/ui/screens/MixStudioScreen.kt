package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.ReverbPreset
import com.example.audio.StudioMixPreset
import com.example.data.model.LrcParser
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudioSliderRow
import com.example.ui.components.StudioVuMeterBar
import com.example.ui.components.WaveformScrubber
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
import com.example.ui.viewmodel.MixStudioViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixStudioScreen(
    mixId: Long,
    viewModel: MixStudioViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val levelMeter by viewModel.levelMeter.collectAsStateWithLifecycle()

    LaunchedEffect(mixId) {
        viewModel.loadMix(mixId)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { destUri ->
        if (destUri != null) {
            viewModel.saveMp3CopyToUri(destUri)
        }
    }

    Scaffold(
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Studio Mixing & MP3",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.track?.title ?: "Memuat trek...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::saveSettingsOnly,
                        modifier = Modifier.testTag("save_mix_settings_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan Preset Mixing", tint = AmberSecondary)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live DSP Preview & Waveform Card
                val totalDur = (uiState.track?.durationMs ?: durationMs).coerceAtLeast(1L)
                val progress = (positionMs.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioOutline, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(
                                text = "PREVIEW DSP REAL-TIME",
                                containerColor = CoralContainer,
                                contentColor = OnCoralContainer
                            )
                            Text(
                                text = "${LrcParser.formatDuration(positionMs)} / ${LrcParser.formatDuration(totalDur)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberSecondary
                            )
                        }

                        WaveformScrubber(
                            peaks = uiState.waveformPeaks,
                            progress = progress,
                            onSeekFraction = viewModel::seekToFraction,
                            modifier = Modifier.testTag("mix_waveform_scrubber")
                        )

                        StudioVuMeterBar(
                            label = "LEVEL OUTPUT MASTER",
                            level = levelMeter,
                            accentColor = MintTertiary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = viewModel::togglePreviewPlayback,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlaying) AmberSecondary else CoralPrimary,
                                    contentColor = if (isPlaying) Color(0xFF1F1302) else Color.White
                                ),
                                modifier = Modifier.testTag("preview_mix_play_btn")
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPlaying) "Jeda Preview" else "Dengarkan Hasil Mix")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Solo Vokal",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (uiState.settings.soloVocal) MintTertiary else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = uiState.settings.soloVocal,
                                    onCheckedChange = { solo ->
                                        viewModel.updateSettings { it.copy(soloVocal = solo) }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MintTertiary
                                    ),
                                    modifier = Modifier.testTag("solo_vocal_switch")
                                )
                            }
                        }
                    }
                }

                // 2. Quick Studio Presets
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PRESET STUDIO CEPAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudioMixPreset.entries.forEach { preset ->
                            Card(
                                onClick = { viewModel.applyPreset(preset) },
                                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .border(1.dp, StudioOutline, RoundedCornerShape(16.dp))
                                    .testTag("preset_${preset.name}")
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AmberSecondary
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Balance & Latency Sync Card
                val settings = uiState.settings
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioOutline, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Keseimbangan & Sinkronisasi",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )

                        StudioSliderRow(
                            title = "Volume Vokal",
                            valueText = formatDb(settings.vocalGainDb),
                            value = settings.vocalGainDb,
                            valueRange = -12f..12f,
                            accentColor = CoralPrimary,
                            onValueChange = { v -> viewModel.updateSettings { it.copy(vocalGainDb = v) } },
                            testTagPrefix = "vocal_gain"
                        )

                        StudioSliderRow(
                            title = "Volume Musik Pengiring",
                            valueText = formatDb(settings.musicGainDb),
                            value = settings.musicGainDb,
                            valueRange = -18f..6f,
                            accentColor = AmberSecondary,
                            onValueChange = { v -> viewModel.updateSettings { it.copy(musicGainDb = v) } },
                            testTagPrefix = "music_gain"
                        )

                        HorizontalDivider(color = StudioOutline, modifier = Modifier.padding(vertical = 4.dp))

                        StudioSliderRow(
                            title = "Sinkronisasi Vokal (Offset Latensi)",
                            subtitle = "Geser jika suara terasa sedikit terlambat atau terlalu cepat dari ketukan musik",
                            valueText = "${settings.syncOffsetMs} ms",
                            value = settings.syncOffsetMs.toFloat(),
                            valueRange = -300f..300f,
                            stepAmount = 10f,
                            accentColor = MintTertiary,
                            onValueChange = { v ->
                                viewModel.updateSettings { it.copy(syncOffsetMs = v.roundToInt()) }
                            },
                            testTagPrefix = "sync_offset"
                        )
                    }
                }

                // 4. Studio Vocal DSP Card (Clarity, Noise Gate, Compressor, Reverb)
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioOutline, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MintTertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kejernihan Vokal & Efek Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }

                        StudioSliderRow(
                            title = "Kejernihan & Presence (3.2kHz + Air)",
                            subtitle = "Menonjolkan artikulasi vokal agar jernih di atas musik",
                            valueText = "${(settings.clarityBoost * 100).roundToInt()}%",
                            value = settings.clarityBoost,
                            valueRange = 0f..1f,
                            accentColor = MintTertiary,
                            onValueChange = { v -> viewModel.updateSettings { it.copy(clarityBoost = v) } },
                            testTagPrefix = "clarity"
                        )

                        StudioSliderRow(
                            title = "Kehangatan Vokal (220Hz)",
                            valueText = formatDb(settings.warmthGain),
                            value = settings.warmthGain,
                            valueRange = -4f..6f,
                            accentColor = AmberSecondary,
                            onValueChange = { v -> viewModel.updateSettings { it.copy(warmthGain = v) } },
                            testTagPrefix = "warmth"
                        )

                        StudioSliderRow(
                            title = "Noise Gate (Redam Desis Ruang)",
                            subtitle = "Meredam suara kipas/ruangan saat jeda bernyanyi",
                            valueText = "${settings.noiseGateThresholdDb.roundToInt()} dB",
                            value = settings.noiseGateThresholdDb,
                            valueRange = -60f..-26f,
                            accentColor = MintTertiary,
                            onValueChange = { v ->
                                viewModel.updateSettings { it.copy(noiseGateThresholdDb = v) }
                            },
                            testTagPrefix = "noise_gate"
                        )

                        StudioSliderRow(
                            title = "Kompresor Vokal",
                            subtitle = "Menjaga dinamika vokal tetap stabil & tidak pecah",
                            valueText = "${(settings.compressorAmount * 100).roundToInt()}%",
                            value = settings.compressorAmount,
                            valueRange = 0f..1f,
                            accentColor = CoralPrimary,
                            onValueChange = { v ->
                                viewModel.updateSettings { it.copy(compressorAmount = v) }
                            },
                            testTagPrefix = "compressor"
                        )

                        HorizontalDivider(color = StudioOutline, modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "Ruang Gema (Stereo Reverb)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReverbPreset.entries.forEach { rp ->
                                val selected = settings.reverbPreset == rp
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSettings { it.copy(reverbPreset = rp) } },
                                    label = { Text(rp.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CoralContainer,
                                        selectedLabelColor = OnCoralContainer
                                    )
                                )
                            }
                        }

                        StudioSliderRow(
                            title = "Intensitas Reverb",
                            valueText = "${(settings.reverbWet * 100).roundToInt()}%",
                            value = settings.reverbWet,
                            valueRange = 0f..0.60f,
                            accentColor = CoralPrimary,
                            onValueChange = { v -> viewModel.updateSettings { it.copy(reverbWet = v) } },
                            testTagPrefix = "reverb_wet"
                        )
                    }
                }

                // 5. MP3 Render & Download Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AmberSecondary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ekspor & Download Format MP3",
                            style = MaterialTheme.typography.titleLarge,
                            color = AmberSecondary
                        )

                        OutlinedTextField(
                            value = uiState.mixName,
                            onValueChange = viewModel::updateMixName,
                            label = { Text("Nama File / Mix") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberSecondary,
                                unfocusedBorderColor = StudioOutline,
                                focusedLabelColor = AmberSecondary,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mix_name_input")
                        )

                        Text(
                            text = "Kualitas Bitrate MP3 (LAME Stereo 44.1kHz)",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(128, 192, 256, 320).forEach { kbps ->
                                val selected = uiState.bitrateKbps == kbps
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateBitrate(kbps) },
                                    label = { Text("${kbps} kbps") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberContainer,
                                        selectedLabelColor = OnAmberContainer
                                    ),
                                    modifier = Modifier.testTag("bitrate_${kbps}")
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.isExporting) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { uiState.exportProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = MintTertiary,
                                    trackColor = StudioSurfaceHighest
                                )
                                Text(
                                    text = "Merender DSP & mengkodekan MP3... ${(uiState.exportProgress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MintTertiary
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.statusMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintContainer)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = uiState.statusMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnMintContainer
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.errorMessage != null) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = viewModel::exportToMp3,
                            enabled = !uiState.isExporting && uiState.mix != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberSecondary,
                                contentColor = Color(0xFF1D1202)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("export_mp3_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.exportedMp3Path != null) "Render Ulang & Download MP3" else "Render & Download MP3",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (uiState.exportedMp3Path != null) {
                            val sizeMb = String.format(
                                Locale.US,
                                "%.2f MB",
                                uiState.exportedFileSizeBytes / (1024f * 1024f)
                            )
                            Text(
                                text = "File MP3 siap ($sizeMb)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MintTertiary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = viewModel::playExportedMp3,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("play_exported_mp3_btn"),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MintContainer,
                                        contentColor = OnMintContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Putar MP3")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val cleanName = uiState.mixName
                                            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                                            .ifBlank { "VocaStage_Mix" }
                                        createDocumentLauncher.launch("${cleanName}.mp3")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_mp3_as_btn")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simpan ke...")
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        viewModel.buildShareMp3Intent()?.let { intent ->
                                            context.startActivity(
                                                Intent.createChooser(intent, "Bagikan MP3 VocaStage")
                                            )
                                        }
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = StudioSurfaceHighest,
                                        contentColor = TextPrimary
                                    ),
                                    modifier = Modifier.testTag("share_exported_mp3_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Bagikan MP3")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun formatDb(db: Float): String {
    val sign = if (db >= 0f) "+" else ""
    return String.format(Locale.US, "%s%.1f dB", sign, db)
}
