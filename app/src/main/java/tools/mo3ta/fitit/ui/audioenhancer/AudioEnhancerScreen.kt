package tools.mo3ta.fitit.ui.audioenhancer

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import tools.mo3ta.fitit.ui.common.KeepScreenOn
import java.util.Locale

private val Accent = Color(0xFF5E5CE6)
private val AccentDark = Color(0xFF3A38B5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEnhancerScreen(
    onBack: () -> Unit,
    viewModel: AudioEnhancerViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("audio_enhancer")
    }

    // Keep the screen awake while processing; released automatically when it ends.
    KeepScreenOn(viewModel.isProcessing)

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onAudioSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.audio_enhancer_title),
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
                    text = stringResource(R.string.audio_enhancer_feature_desc),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                PickAudioCard(
                    hasAudio = viewModel.selectedAudioUri != null,
                    displayName = viewModel.audioDisplayName,
                    fileSizeBytes = viewModel.audioFileSizeBytes,
                    pickLabel = stringResource(R.string.audio_enhancer_pick),
                    pickHint = stringResource(R.string.audio_enhancer_pick_hint),
                    tapToChangeLabel = stringResource(R.string.audio_enhancer_tap_to_change),
                    onPick = { audioPicker.launch("audio/*") }
                )
            }

            item {
                LevelSelector(
                    selected = viewModel.level,
                    label = stringResource(R.string.audio_enhancer_level_label),
                    enabled = !viewModel.isProcessing,
                    onSelect = viewModel::changeLevel
                )
            }

            item {
                AiDenoiseToggle(
                    checked = viewModel.useAiDenoise,
                    available = viewModel.isAiEngineAvailable,
                    enabled = !viewModel.isProcessing,
                    title = stringResource(R.string.audio_enhancer_ai_label),
                    description = stringResource(
                        if (viewModel.isAiEngineAvailable) R.string.audio_enhancer_ai_desc
                        else R.string.audio_enhancer_ai_unavailable,
                    ),
                    onCheckedChange = viewModel::changeAiDenoise,
                )
            }

            item {
                EnhanceButton(
                    onClick = { viewModel.enhance() },
                    enabled = viewModel.isEnhanceEnabled,
                    label = stringResource(R.string.audio_enhancer_process)
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

            if (viewModel.aiFellBack) {
                item {
                    Text(
                        text = stringResource(R.string.audio_enhancer_ai_fallback),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            viewModel.errorMessage?.let { msg ->
                item { ErrorCard(message = msg) }
            }

            if (viewModel.resultFile != null) {
                item {
                    ResultCard(
                        fileSizeBytes = viewModel.resultSizeBytes,
                        isSaved = viewModel.isSaved,
                        isPlaying = viewModel.isPreviewPlaying,
                        previewSource = viewModel.previewSource,
                        readyLabel = stringResource(R.string.audio_enhancer_done),
                        playLabel = stringResource(R.string.audio_enhancer_play),
                        pauseLabel = stringResource(R.string.audio_enhancer_pause),
                        compareLabel = stringResource(R.string.audio_enhancer_compare),
                        originalLabel = stringResource(R.string.audio_enhancer_original),
                        enhancedLabel = stringResource(R.string.audio_enhancer_enhanced),
                        saveLabel = stringResource(R.string.audio_enhancer_save),
                        savedLabel = stringResource(R.string.audio_enhancer_saved),
                        shareLabel = stringResource(R.string.audio_enhancer_share),
                        onSelectSource = { viewModel.selectPreviewSource(context, it) },
                        onPreview = { viewModel.togglePreview(context) },
                        onSave = { viewModel.saveResult(context) },
                        onShare = { viewModel.shareResult(context) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PickAudioCard(
    hasAudio: Boolean,
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
                    .background(Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Accent)
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!hasAudio) {
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
private fun LevelSelector(
    selected: AudioEnhancementLevel,
    label: String,
    enabled: Boolean,
    onSelect: (AudioEnhancementLevel) -> Unit
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
                AudioEnhancementLevel.entries.forEach { lvl ->
                    LevelChip(
                        title = stringResource(levelLabel(lvl)),
                        isSelected = lvl == selected,
                        enabled = enabled,
                        onClick = { onSelect(lvl) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelChip(
    title: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (isSelected) Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (isSelected) Accent else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EnhanceButton(onClick: () -> Unit, enabled: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(Accent, AccentDark))
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
    fileSizeBytes: Long,
    isSaved: Boolean,
    isPlaying: Boolean,
    previewSource: PreviewSource,
    readyLabel: String,
    playLabel: String,
    pauseLabel: String,
    compareLabel: String,
    originalLabel: String,
    enhancedLabel: String,
    saveLabel: String,
    savedLabel: String,
    shareLabel: String,
    onSelectSource: (PreviewSource) -> Unit,
    onPreview: () -> Unit,
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
                        .background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = readyLabel,
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

            // A/B compare: pick which track to hear, then play/pause it.
            Text(
                text = compareLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                CompareTab(
                    label = originalLabel,
                    selected = previewSource == PreviewSource.ORIGINAL,
                    onClick = { onSelectSource(PreviewSource.ORIGINAL) },
                    modifier = Modifier.weight(1f)
                )
                CompareTab(
                    label = enhancedLabel,
                    selected = previewSource == PreviewSource.ENHANCED,
                    onClick = { onSelectSource(PreviewSource.ENHANCED) },
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent.copy(alpha = 0.10f))
                    .clickable { onPreview() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (isPlaying) pauseLabel else playLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Accent
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(
                    targetState = isSaved,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                            (scaleOut() + fadeOut())
                    },
                    label = "audio_enhance_save",
                    modifier = Modifier.weight(1f)
                ) { saved ->
                    OutlinedButton(
                        onClick = { if (!saved) onSave() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (saved) Color(0xFF34C759) else Accent
                        ),
                        border = BorderStroke(1.5.dp, if (saved) Color(0xFF34C759) else Accent)
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    border = BorderStroke(1.5.dp, Accent)
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
private fun CompareTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            color = if (selected) Accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AiDenoiseToggle(
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

private fun levelLabel(level: AudioEnhancementLevel): Int = when (level) {
    AudioEnhancementLevel.LIGHT -> R.string.audio_enhancer_level_light
    AudioEnhancementLevel.STANDARD -> R.string.audio_enhancer_level_standard
    AudioEnhancementLevel.STRONG -> R.string.audio_enhancer_level_strong
}

private fun formatBytes(bytes: Long): String {
    return if (bytes < 1_048_576L) {
        String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }
}
