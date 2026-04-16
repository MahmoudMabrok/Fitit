package tools.mo3ta.fitit.ui.videosplitter

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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

private val RedAccent = Color(0xFFFF3B30)
private val RedDark   = Color(0xFFCC2222)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSplitterScreen(
    onBack: () -> Unit,
    viewModel: VideoSplitterViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("video_splitter")
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
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
                        text = stringResource(R.string.video_splitter_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = RedAccent
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
                    text = stringResource(R.string.video_splitter_feature_desc),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                val maxDurationLabel = stringResource(R.string.video_splitter_max_duration)
                val tapToChangeLabel = stringResource(R.string.video_splitter_tap_to_change)
                PickVideoCard(
                    hasVideo = viewModel.selectedVideoUri != null,
                    durationMs = viewModel.videoDurationMs,
                    isDurationValid = viewModel.isDurationValid,
                    pickVideoLabel = stringResource(R.string.video_splitter_pick_video),
                    durationErrorLabel = stringResource(R.string.video_splitter_duration_error),
                    maxDurationLabel = maxDurationLabel,
                    tapToChangeLabel = tapToChangeLabel,
                    onPick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }

            item {
                SplitButton(
                    onClick = { viewModel.split() },
                    enabled = viewModel.isSplitEnabled,
                    label = stringResource(R.string.video_splitter_split_button)
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
                            color = RedAccent,
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
                        message = msg.ifBlank { stringResource(R.string.video_splitter_error_generic) }
                    )
                }
            }

            if (viewModel.chunks.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.video_splitter_chunks_count, viewModel.chunks.size),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                itemsIndexed(viewModel.chunks, key = { _, chunk -> chunk.index }) { _, chunk ->
                    VideoChunkCard(
                        chunk = chunk,
                        isSaved = chunk.index in viewModel.savedChunkIndices,
                        saveLabel = stringResource(R.string.video_splitter_save),
                        savedLabel = stringResource(R.string.video_splitter_saved),
                        shareLabel = stringResource(R.string.video_splitter_share),
                        chunkLabel = stringResource(R.string.video_splitter_chunk_label),
                        onSave = { viewModel.saveChunk(context, chunk) },
                        onShare = { viewModel.shareChunk(context, chunk) }
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
    pickVideoLabel: String,
    durationErrorLabel: String,
    maxDurationLabel: String,
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
                    .background(RedAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = RedAccent)
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!hasVideo) {
                    Text(
                        text = pickVideoLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = maxDurationLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = durationMs.toTimeLabel(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isDurationValid) RedAccent else MaterialTheme.colorScheme.error
                    )
                    if (!isDurationValid) {
                        Text(
                            text = durationErrorLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = tapToChangeLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitButton(onClick: () -> Unit, enabled: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(RedAccent, RedDark))
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

@Composable
private fun VideoChunkCard(
    chunk: VideoChunk,
    isSaved: Boolean,
    saveLabel: String,
    savedLabel: String,
    shareLabel: String,
    chunkLabel: String,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val animatedFill by animateFloatAsState(
        targetValue = (chunk.endMs - chunk.startMs).toFloat() / CHUNK_DURATION_MS.toFloat(),
        animationSpec = tween(600),
        label = "fill_${chunk.index}"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(RedAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${chunk.index}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "$chunkLabel ${chunk.index}  ·  ${chunk.startMs.toTimeLabel()} – ${chunk.endMs.toTimeLabel()}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedFill },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = RedAccent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedContent(
                    targetState = isSaved,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                        (scaleOut() + fadeOut())
                    },
                    label = "save_${chunk.index}",
                    modifier = Modifier.weight(1f)
                ) { saved ->
                    OutlinedButton(
                        onClick = { if (!saved) onSave() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (saved) Color(0xFF34C759) else RedAccent
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (saved) Color(0xFF34C759) else RedAccent
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent),
                    border = BorderStroke(1.5.dp, RedAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(shareLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun Long.toTimeLabel(): String {
    val totalSec = this / 1000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
