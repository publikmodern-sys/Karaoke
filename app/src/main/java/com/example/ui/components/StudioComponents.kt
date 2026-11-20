package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LrcParser
import com.example.data.model.LyricLine
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WaveformScrubber(
    peaks: List<Float>,
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val displayPeaks = if (peaks.isEmpty()) List(48) { 0.25f } else peaks

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSurfaceElevated)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        val count = displayPeaks.size
        val slotW = size.width / count.toFloat()
        val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())
        val midY = size.height / 2f

        for (i in 0 until count) {
            val barFrac = i.toFloat() / count.toFloat()
            val amp = displayPeaks[i].coerceIn(0.08f, 1f)
            val barH = (size.height * amp).coerceAtLeast(4.dp.toPx())
            val x = i * slotW + (slotW - barW) / 2f
            val y = midY - barH / 2f

            val isPlayed = barFrac <= clampedProgress
            val color = if (isPlayed) {
                Brush.verticalGradient(listOf(AmberSecondary, CoralPrimary))
            } else {
                Brush.verticalGradient(listOf(StudioOutline, StudioOutline.copy(alpha = 0.7f)))
            }

            drawRoundRect(
                brush = color,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }

        // Playhead indicator
        val needleX = (clampedProgress * size.width).coerceIn(0f, size.width)
        drawLine(
            color = Color.White,
            start = Offset(needleX, 0f),
            end = Offset(needleX, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun StudioVuMeterBar(
    label: String,
    level: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "vu_level"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "${(animatedLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (animatedLevel > 0.85f) CoralPrimary else accentColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(StudioSurfaceHighest)
        ) {
            val segments = 24
            val gap = 3.dp.toPx()
            val segW = (size.width - gap * (segments - 1)) / segments
            val activeSegments = (animatedLevel * segments).toInt()
            for (i in 0 until segments) {
                val x = i * (segW + gap)
                val segColor = when {
                    i >= activeSegments -> StudioOutline.copy(alpha = 0.35f)
                    i >= segments * 0.82 -> CoralPrimary
                    i >= segments * 0.58 -> AmberSecondary
                    else -> MintTertiary
                }
                drawRoundRect(
                    color = segColor,
                    topLeft = Offset(x, 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun StudioSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = CoralPrimary,
    stepAmount: Float? = null,
    testTagPrefix: String = "slider"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            Surface(
                color = StudioSurfaceHighest,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stepAmount != null) {
                FilledTonalIconButton(
                    onClick = {
                        onValueChange((value - stepAmount).coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_dec"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = StudioSurfaceHighest,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurangi $title",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .weight(1f)
                    .testTag(testTagPrefix),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = StudioOutline
                )
            )

            if (stepAmount != null) {
                Spacer(modifier = Modifier.width(6.dp))
                FilledTonalIconButton(
                    onClick = {
                        onValueChange((value + stepAmount).coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_inc"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = StudioSurfaceHighest,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah $title",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun KaraokeLyricsStage(
    lyrics: List<LyricLine>,
    hasSyncedTimestamps: Boolean,
    positionMs: Long,
    onLineClick: ((LyricLine) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = if (hasSyncedTimestamps) {
        LrcParser.findActiveIndex(lyrics, positionMs)
    } else -1

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.size) {
            val target = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(StudioSurfaceElevated.copy(alpha = 0.7f))
                .border(1.dp, StudioOutlineVariantCompat, RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mode Instrumental Bebas",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Belum ada lirik yang dimasukkan untuk lagu ini. Kamu tetap bisa bernyanyi mengikuti alunan musik!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            StudioSurfaceElevated.copy(alpha = 0.92f),
                            StudioSurfaceElevated.copy(alpha = 0.55f)
                        )
                    )
                )
                .border(1.dp, StudioOutlineVariantCompat, RoundedCornerShape(20.dp)),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lyrics, key = { idx, item -> "${idx}_${item.id}" }) { index, line ->
                val isActive = index == activeIndex
                val isPast = hasSyncedTimestamps && activeIndex > index
                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> AmberSecondary
                        isPast -> TextMuted
                        !hasSyncedTimestamps -> TextPrimary
                        else -> TextSecondary
                    },
                    label = "lyric_color"
                )
                val bgAlpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    label = "lyric_bg"
                )
                val paddingV by animateDpAsState(
                    targetValue = if (isActive) 12.dp else 4.dp,
                    label = "lyric_pad"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CoralPrimary.copy(alpha = 0.14f * bgAlpha))
                        .then(
                            if (onLineClick != null && line.timeMs != null) {
                                Modifier.clickable { onLineClick(line) }
                            } else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = paddingV)
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (isActive) 22.sp else 17.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = if (isActive) 30.sp else 24.sp
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private val StudioOutlineVariantCompat = Color(0xFF282E4A)

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
