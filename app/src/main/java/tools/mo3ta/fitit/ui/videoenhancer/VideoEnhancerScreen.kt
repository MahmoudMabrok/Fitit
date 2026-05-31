package tools.mo3ta.fitit.ui.videoenhancer

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import tools.mo3ta.fitit.ui.common.KeepScreenOn

private val Accent = Color(0xFF8E44FF)
private val AccentDark = Color(0xFF6C2BD9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEnhancerScreen(
    onBack: () -> Unit,
    viewModel: VideoEnhancerViewModel = viewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("video_enhancer")
    }

    // Keep the screen awake while processing; released automatically when it ends.
    KeepScreenOn(viewModel.isProcessing)

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION,
            )?.toLongOrNull() ?: 0L
            viewModel.onVideoSelected(uri, durationMs)
        } finally {
            retriever.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.video_enhancer_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.video_enhancer_feature_desc),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                PickVideoCard(
                    hasVideo = viewModel.selectedVideoUri != null,
                    durationMs = viewModel.videoDurationMs,
                    isDurationValid = viewModel.isDurationValid,
                    videoFileSizeBytes = viewModel.videoFileSizeBytes,
                    pickVideoLabel = stringResource(R.string.video_enhancer_pick_video),
                    durationErrorLabel = stringResource(R.string.video_enhancer_duration_error),
                    maxDurationLabel = stringResource(R.string.video_enhancer_max_duration),
                    tapToChangeLabel = stringResource(R.string.video_enhancer_tap_to_change),
                    enabled = !viewModel.isProcessing,
                    onPick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                )
            }

            item {
                LevelSelector(
                    selected = viewModel.level,
                    enabled = !viewModel.isProcessing,
                    label = stringResource(R.string.video_enhancer_level_label),
                    onSelect = viewModel::changeLevel,
                )
            }

            item {
                AiEngineToggle(
                    checked = viewModel.useAiUpscale,
                    available = viewModel.isAiEngineAvailable,
                    enabled = !viewModel.isProcessing,
                    title = stringResource(R.string.video_enhancer_ai_label),
                    description = stringResource(
                        if (viewModel.isAiEngineAvailable) R.string.video_enhancer_ai_desc
                        else R.string.video_enhancer_ai_unavailable,
                    ),
                    onCheckedChange = viewModel::changeAiUpscale,
                )
            }

            if (viewModel.useAiUpscale && viewModel.isAiEngineAvailable) {
                item {
                    SpeedModeSelector(
                        selected = viewModel.speedMode,
                        enabled = !viewModel.isProcessing,
                        label = stringResource(R.string.video_enhancer_speed_label),
                        onSelect = viewModel::changeSpeedMode,
                        fastCapPx = viewModel.fastCapPx,
                        fastCapOptions = FAST_CAP_OPTIONS,
                        fastCapLabel = stringResource(R.string.video_enhancer_speed_cap_label),
                        onSelectFastCap = viewModel::changeFastCap,
                    )
                }
            }

            item {
                EnhanceButton(
                    onClick = { viewModel.enhance() },
                    enabled = viewModel.isEnhanceEnabled,
                    label = stringResource(R.string.video_enhancer_enhance_button),
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
                            color = Accent,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        Text(
                            text = stringResource(
                                R.string.video_enhancer_processing,
                                (viewModel.progress * 100).toInt(),
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.video_enhancer_background_hint),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Accent,
                        )
                        OutlinedButton(
                            onClick = { viewModel.cancelProcessing() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.video_enhancer_cancel),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            viewModel.errorMessage?.let { msg ->
                item {
                    ErrorCard(message = msg.ifBlank { stringResource(R.string.video_enhancer_error_generic) })
                }
            }

            if (viewModel.aiFellBackToGl) {
                item {
                    Text(
                        text = stringResource(R.string.video_enhancer_ai_fallback),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            viewModel.enhancedFile?.let {
                item {
                    ResultCard(
                        originalSizeBytes = viewModel.videoFileSizeBytes,
                        enhancedSizeBytes = viewModel.enhancedFileSizeBytes,
                        isSaved = viewModel.isSaved,
                        resultTitle = stringResource(R.string.video_enhancer_result_title),
                        originalLabel = stringResource(R.string.video_enhancer_original),
                        enhancedLabel = stringResource(R.string.video_enhancer_enhanced),
                        previewLabel = stringResource(R.string.video_enhancer_preview),
                        saveLabel = stringResource(R.string.video_enhancer_save),
                        savedLabel = stringResource(R.string.video_enhancer_saved),
                        shareLabel = stringResource(R.string.video_enhancer_share),
                        onPreview = { viewModel.previewEnhanced(context) },
                        onSave = { viewModel.saveEnhanced(context) },
                        onShare = { viewModel.shareEnhanced(context) },
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
    durationMs: Long,
    isDurationValid: Boolean,
    videoFileSizeBytes: Long,
    pickVideoLabel: String,
    durationErrorLabel: String,
    maxDurationLabel: String,
    tapToChangeLabel: String,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onPick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Accent)
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!hasVideo) {
                    Text(
                        text = pickVideoLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = maxDurationLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = durationMs.toTimeLabel(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isDurationValid) Accent else MaterialTheme.colorScheme.error,
                    )
                    if (!isDurationValid) {
                        Text(
                            text = durationErrorLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = tapToChangeLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (videoFileSizeBytes > 0L) {
                            Text(
                                text = formatFileSize(videoFileSizeBytes),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelSelector(
    selected: EnhancementLevel,
    enabled: Boolean,
    label: String,
    onSelect: (EnhancementLevel) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnhancementLevel.entries.forEach { lvl ->
                    val isSelected = lvl == selected
                    val labelRes = when (lvl) {
                        EnhancementLevel.LIGHT -> R.string.video_enhancer_level_light
                        EnhancementLevel.STANDARD -> R.string.video_enhancer_level_standard
                        EnhancementLevel.MAX -> R.string.video_enhancer_level_max
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Accent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                            )
                            .clickable(enabled = enabled) { onSelect(lvl) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${stringResource(labelRes)}\n${lvl.targetShortSidePx}p",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedModeSelector(
    selected: MlSpeedMode,
    enabled: Boolean,
    label: String,
    onSelect: (MlSpeedMode) -> Unit,
    fastCapPx: Int,
    fastCapOptions: List<Int>,
    fastCapLabel: String,
    onSelectFastCap: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MlSpeedMode.entries.forEach { mode ->
                    val isSelected = mode == selected
                    val labelRes = when (mode) {
                        MlSpeedMode.FAST -> R.string.video_enhancer_speed_fast
                        MlSpeedMode.BALANCED -> R.string.video_enhancer_speed_balanced
                        MlSpeedMode.QUALITY -> R.string.video_enhancer_speed_quality
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Accent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                            )
                            .clickable(enabled = enabled) { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Fast mode lets the user pick the exact input-resolution cap (lower = faster, blockier).
            if (selected == MlSpeedMode.FAST) {
                FastCapDropdown(
                    selected = fastCapPx,
                    options = fastCapOptions,
                    label = fastCapLabel,
                    enabled = enabled,
                    onSelect = onSelectFastCap,
                )
            }
        }
    }
}

@Composable
private fun FastCapDropdown(
    selected: Int,
    options: List<Int>,
    label: String,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$selected px",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { px ->
                    DropdownMenuItem(
                        text = { Text("$px px") },
                        onClick = {
                            onSelect(px)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiEngineToggle(
    checked: Boolean,
    available: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked && available,
                onCheckedChange = onCheckedChange,
                enabled = enabled && available,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                ),
            )
        }
    }
}

@Composable
private fun EnhanceButton(onClick: () -> Unit, enabled: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(Accent, AccentDark))
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        ),
                    )
                },
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun ResultCard(
    originalSizeBytes: Long,
    enhancedSizeBytes: Long,
    isSaved: Boolean,
    resultTitle: String,
    originalLabel: String,
    enhancedLabel: String,
    previewLabel: String,
    saveLabel: String,
    savedLabel: String,
    shareLabel: String,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = resultTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SizeStat(originalLabel, originalSizeBytes, Modifier.weight(1f))
                SizeStat(enhancedLabel, enhancedSizeBytes, Modifier.weight(1f), highlight = true)
            }

            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                border = BorderStroke(1.5.dp, Accent),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(previewLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { if (!isSaved) onSave() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSaved) Color(0xFF34C759) else Accent,
                    ),
                    border = BorderStroke(1.5.dp, if (isSaved) Color(0xFF34C759) else Accent),
                ) {
                    Icon(
                        if (isSaved) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isSaved) savedLabel else saveLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    border = BorderStroke(1.5.dp, Accent),
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
private fun SizeStat(label: String, sizeBytes: Long, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) Accent.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            )
            .padding(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (sizeBytes > 0L) formatFileSize(sizeBytes) else "—",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Accent else MaterialTheme.colorScheme.onSurface,
        )
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
    }
}

private fun Long.toTimeLabel(): String {
    val totalSec = this / 1000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
