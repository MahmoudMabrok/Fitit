package tools.mo3ta.fitit.ui.audioextractor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import tools.mo3ta.fitit.ui.common.KeepScreenOn
import java.util.Locale

private val TealAccent = Color(0xFFEC407A)
private val TealDark = Color(0xFFC2185B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioExtractorScreen(
    onBack: () -> Unit,
    viewModel: AudioExtractorViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("audio_extractor")
    }

    // Keep the screen awake while processing; released automatically when it ends.
    KeepScreenOn(viewModel.isProcessing)

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.audio_extractor_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.audio_extractor_feature_desc),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                PickVideoCard(
                    hasVideo = viewModel.selectedVideoUri != null,
                    displayName = viewModel.videoDisplayName,
                    fileSizeBytes = viewModel.videoFileSizeBytes,
                    pickLabel = stringResource(R.string.audio_extractor_pick_video),
                    pickHint = stringResource(R.string.audio_extractor_pick_hint),
                    tapToChangeLabel = stringResource(R.string.audio_extractor_tap_to_change),
                    onPick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            }

            item {
                FormatSelector(
                    selected = viewModel.selectedFormat,
                    label = stringResource(R.string.audio_extractor_format_label),
                    wavHint = stringResource(R.string.audio_extractor_wav_hint),
                    m4aHint = stringResource(R.string.audio_extractor_m4a_hint),
                    onSelect = viewModel::setFormat
                )
            }

            item {
                ExtractButton(
                    onClick = { viewModel.extract() },
                    enabled = viewModel.isExtractEnabled,
                    label = stringResource(R.string.audio_extractor_extract_button)
                )
            }

            if (viewModel.isProcessing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { viewModel.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = TealAccent,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        Text(
                            text = "${(viewModel.progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            viewModel.errorMessage?.let { msg ->
                item {
                    ErrorCard(
                        message = msg.ifBlank { stringResource(R.string.audio_extractor_error_generic) }
                    )
                }
            }

            viewModel.result?.let { audio ->
                item {
                    ResultCard(
                        formatLabel = audio.format.extension.uppercase(Locale.US),
                        fileSizeBytes = audio.fileSizeBytes,
                        isSaved = viewModel.isSaved,
                        readyLabel = stringResource(R.string.audio_extractor_ready),
                        saveLabel = stringResource(R.string.audio_extractor_save),
                        savedLabel = stringResource(R.string.audio_extractor_saved),
                        shareLabel = stringResource(R.string.audio_extractor_share),
                        onSave = { viewModel.saveResult(context) },
                        onShare = { viewModel.shareResult(context) }
                    )
                }

                item {
                    AudioPreviewCard(
                        isPlaying = viewModel.isPlaying,
                        positionMs = viewModel.playbackPositionMs,
                        durationMs = viewModel.playbackDurationMs,
                        previewLabel = stringResource(R.string.audio_extractor_preview),
                        playLabel = stringResource(R.string.audio_extractor_play),
                        pauseLabel = stringResource(R.string.audio_extractor_pause),
                        onTogglePlayback = { viewModel.togglePlayback() },
                        onSeek = { viewModel.seekTo(it) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PickVideoCard(
    hasVideo: Boolean,
    displayName: String?,
    fileSizeBytes: Long,
    pickLabel: String,
    pickHint: String,
    tapToChangeLabel: String,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TealAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = TealAccent)
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!hasVideo) {
                    Text(
                        text = pickLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pickHint,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = displayName ?: pickLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tapToChangeLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fileSizeBytes > 0L) {
                        Text(
                            text = formatBytes(fileSizeBytes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatSelector(
    selected: AudioFormat,
    label: String,
    wavHint: String,
    m4aHint: String,
    onSelect: (AudioFormat) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormatChip(
                    title = "M4A",
                    subtitle = m4aHint,
                    icon = Icons.Default.MusicNote,
                    isSelected = selected == AudioFormat.M4A,
                    onClick = { onSelect(AudioFormat.M4A) },
                    modifier = Modifier.weight(1f)
                )
                FormatChip(
                    title = "WAV",
                    subtitle = wavHint,
                    icon = Icons.Default.GraphicEq,
                    isSelected = selected == AudioFormat.WAV,
                    onClick = { onSelect(AudioFormat.WAV) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FormatChip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (isSelected) TealAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) TealAccent.copy(alpha = 0.10f) else Color.Transparent
            )
            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isSelected) TealAccent else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExtractButton(onClick: () -> Unit, enabled: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(TealAccent, TealDark))
                else
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (enabled) Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun ResultCard(
    formatLabel: String,
    fileSizeBytes: Long,
    isSaved: Boolean,
    readyLabel: String,
    saveLabel: String,
    savedLabel: String,
    shareLabel: String,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$readyLabel  ·  $formatLabel",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (fileSizeBytes > 0L) {
                        Text(
                            text = formatBytes(fileSizeBytes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(
                    targetState = isSaved,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                            (scaleOut() + fadeOut())
                    },
                    label = "audio_save",
                    modifier = Modifier.weight(1f)
                ) { saved ->
                    OutlinedButton(
                        onClick = { if (!saved) onSave() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (saved) Color(0xFF34C759) else TealAccent
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (saved) Color(0xFF34C759) else TealAccent
                        )
                    ) {
                        Icon(
                            if (saved) Icons.Default.Check else Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (saved) savedLabel else saveLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealAccent),
                    border = BorderStroke(1.5.dp, TealAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(shareLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AudioPreviewCard(
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    previewLabel: String,
    playLabel: String,
    pauseLabel: String,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = previewLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(TealAccent)
                        .clickable { onTogglePlayback() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) pauseLabel else playLabel,
                        tint = Color.White
                    )
                }

                // Avoid an empty range before the player has prepared (duration == 0).
                val safeDuration = durationMs.coerceAtLeast(1)
                Slider(
                    value = positionMs.coerceIn(0, safeDuration).toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    enabled = durationMs > 0,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = TealAccent,
                        activeTrackColor = TealAccent
                    )
                )

                Text(
                    text = formatDuration(positionMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    return if (bytes < 1_048_576L) {
        String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }
}
