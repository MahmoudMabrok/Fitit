package tools.mo3ta.fitit.ui.textsplitter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager

private val OrangeAccent = Color(0xFFFF9500)
private val OrangeDark  = Color(0xFFE08000)
private val GreenCheck  = Color(0xFF34C759)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSplitterScreen(
    onBack: () -> Unit,
    viewModel: TextSplitterViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("text_splitter")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.text_splitter_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = OrangeAccent
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
            // Feature description + selectable preset pills
            item {
                FeatureDescription(
                    selected = viewModel.selectedPreset,
                    onSelect = { viewModel.selectedPreset = it }
                )
            }

            // Custom size field
            if (viewModel.selectedPreset == SplitPreset.CUSTOM) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = expandVertically() + fadeIn()
                    ) {
                        OutlinedTextField(
                            value = viewModel.customSizeInput,
                            onValueChange = { viewModel.customSizeInput = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.text_splitter_custom_size_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }

            // Text input with live char counter
            item {
                TextInputCard(
                    text = viewModel.inputText,
                    onTextChange = { viewModel.inputText = it },
                    chunkSize = viewModel.chunkSize,
                    hint = stringResource(R.string.text_splitter_input_hint)
                )
            }

            // Split button — gradient
            item {
                SplitButton(
                    onClick = { viewModel.split() },
                    enabled = viewModel.isSplitEnabled,
                    label = stringResource(R.string.text_splitter_split_button)
                )
            }

            // Error state
            viewModel.errorMessage?.let { msg ->
                item {
                    ErrorCard(message = msg)
                }
            }

            // Results header + chunk cards
            if (viewModel.chunks.isNotEmpty()) {
                item {
                    ResultsHeader(
                        count = viewModel.chunks.size,
                        onCopyAll = { viewModel.copyAll(context) },
                        copyAllLabel = stringResource(R.string.text_splitter_copy_all)
                    )
                }

                itemsIndexed(viewModel.chunks, key = { idx, _ -> idx }) { index, chunk ->
                    ChunkCard(
                        index = index + 1,
                        chunk = chunk,
                        chunkSize = viewModel.chunkSize,
                        chunkLabel = stringResource(R.string.text_splitter_chunk_label),
                        charsLabel = stringResource(R.string.text_splitter_chars),
                        onCopy = { viewModel.copyChunk(context, chunk) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ─── Feature description ─────────────────────────────────────────────────────

@Composable
private fun FeatureDescription(
    selected: SplitPreset,
    onSelect: (SplitPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // One-line feature summary
        Text(
            text = stringResource(R.string.text_splitter_feature_desc),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal
        )

        // Selectable preset pills
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.text_splitter_preset_section_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetInfoPill(
                    name     = stringResource(R.string.text_splitter_preset_whatsapp),
                    limit    = SplitPreset.WHATSAPP.fixedSize,
                    desc     = stringResource(R.string.text_splitter_preset_whatsapp_desc),
                    selected = selected == SplitPreset.WHATSAPP,
                    onClick  = { onSelect(SplitPreset.WHATSAPP) },
                    modifier = Modifier.weight(1f)
                )
                PresetInfoPill(
                    name     = stringResource(R.string.text_splitter_preset_twitter),
                    limit    = SplitPreset.TWITTER.fixedSize,
                    desc     = stringResource(R.string.text_splitter_preset_twitter_desc),
                    selected = selected == SplitPreset.TWITTER,
                    onClick  = { onSelect(SplitPreset.TWITTER) },
                    modifier = Modifier.weight(1f)
                )
                PresetInfoPill(
                    name     = stringResource(R.string.text_splitter_preset_custom),
                    limit    = null,
                    desc     = stringResource(R.string.text_splitter_preset_custom_desc),
                    selected = selected == SplitPreset.CUSTOM,
                    onClick  = { onSelect(SplitPreset.CUSTOM) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Subtle divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        )
    }
}

@Composable
private fun PresetInfoPill(
    name: String,
    limit: Int?,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) OrangeAccent.copy(alpha = 0.12f)
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "pill_bg_$name"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OrangeAccent else Color.Transparent,
        animationSpec = tween(200),
        label = "pill_border_$name"
    )
    val nameColor by animateColorAsState(
        targetValue = if (selected) OrangeAccent else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "pill_name_$name"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(
                if (selected) Modifier.drawBehind {
                    // 2dp border drawn manually to animate cleanly
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = OrangeAccent,
                        size = androidx.compose.ui.geometry.Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = nameColor
        )
        if (limit != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = limit!!.toArabicNumeral(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeAccent
                )
                Text(
                    text = stringResource(R.string.text_splitter_chars_unit),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = OrangeAccent.copy(alpha = 0.7f)
                )
            }
        } else {
            Text("✎", fontSize = 15.sp, color = OrangeAccent)
        }
        Text(
            text = desc,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ─── Text input card with progress ───────────────────────────────────────────

@Composable
private fun TextInputCard(
    text: String,
    onTextChange: (String) -> Unit,
    chunkSize: Int,
    hint: String
) {
    val charCount  = text.length
    val fillRatio  = if (chunkSize > 0) (charCount.toFloat() / chunkSize).coerceIn(0f, 1f) else 0f
    val overLimit  = chunkSize > 0 && charCount > chunkSize

    val borderColor by animateColorAsState(
        targetValue = when {
            overLimit       -> MaterialTheme.colorScheme.error
            fillRatio > 0.85f -> OrangeAccent
            else            -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        label = "input_border"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                maxLines = 12,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor  = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { fillRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (overLimit) MaterialTheme.colorScheme.error else OrangeAccent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (chunkSize > 0)
                               "${charCount.toArabicNumeral()} / ${chunkSize.toArabicNumeral()}"
                           else charCount.toArabicNumeral(),
                    fontSize = 11.sp,
                    color = if (overLimit) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

// ─── Gradient split button ────────────────────────────────────────────────────

@Composable
private fun SplitButton(onClick: () -> Unit, enabled: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(OrangeAccent, OrangeDark))
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ContentCut,
                contentDescription = null,
                tint = if (enabled) Color.White
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (enabled) Color.White
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

// ─── Error card ───────────────────────────────────────────────────────────────

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

// ─── Results header ───────────────────────────────────────────────────────────

@Composable
private fun ResultsHeader(count: Int, onCopyAll: () -> Unit, copyAllLabel: String) {
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
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OrangeAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toArabicNumeral(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeAccent
                )
            }
            Text(
                text = "أجزاء",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        OutlinedButton(
            onClick = onCopyAll,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent),
            border = BorderStroke(1.5.dp, OrangeAccent),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(copyAllLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

// ─── Chunk card ───────────────────────────────────────────────────────────────

@Composable
private fun ChunkCard(
    index: Int,
    chunk: String,
    chunkSize: Int,
    chunkLabel: String,
    charsLabel: String,
    onCopy: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }

    val fillRatio = if (chunkSize > 0) (chunk.length.toFloat() / chunkSize).coerceIn(0f, 1f) else 1f
    val animatedFill by animateFloatAsState(
        targetValue = fillRatio,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "fill_$index"
    )

    val accentColor = OrangeAccent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Start-side accent stripe (RTL: start = right side of canvas)
                drawRect(
                    color = accentColor,
                    topLeft = Offset(size.width - 4.dp.toPx(), 0f),
                    size = Size(4.dp.toPx(), size.height)
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            // Watermark ordinal — large translucent number
            Text(
                text = index.toArabicNumeral(),
                fontSize = 96.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )

            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                // Header: badge + char count + copy button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Index badge
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(OrangeAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = index.toArabicNumeral(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "$chunkLabel ${index.toArabicNumeral()} · ${chunk.length.toArabicNumeral()} $charsLabel",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Copy → Check icon with animated swap
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                            (scaleOut() + fadeOut())
                        },
                        label = "copy_icon_$index"
                    ) { isCopied ->
                        IconButton(onClick = { onCopy(); copied = true }) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (isCopied) GreenCheck else OrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Chunk text
                Text(
                    text = chunk,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp
                )

                Spacer(Modifier.height(12.dp))

                // Fill progress bar
                LinearProgressIndicator(
                    progress = { animatedFill },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = OrangeAccent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }
        }
    }
}

// ─── Arabic numeral extension ─────────────────────────────────────────────────

private fun Int.toArabicNumeral(): String = this.toString().map { c ->
    when (c) {
        '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
        '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
        else -> c
    }
}.joinToString("")
