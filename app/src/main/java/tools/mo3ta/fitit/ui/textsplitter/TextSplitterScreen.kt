package tools.mo3ta.fitit.ui.textsplitter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager

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
                        fontWeight = FontWeight.Bold
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preset chips
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplitPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = viewModel.selectedPreset == preset,
                            onClick = { viewModel.selectedPreset = preset },
                            label = {
                                Text(
                                    text = when (preset) {
                                        SplitPreset.WHATSAPP -> stringResource(R.string.text_splitter_preset_whatsapp)
                                        SplitPreset.TWITTER -> stringResource(R.string.text_splitter_preset_twitter)
                                        SplitPreset.CUSTOM -> stringResource(R.string.text_splitter_preset_custom)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Custom size field — only shown for CUSTOM preset
            if (viewModel.selectedPreset == SplitPreset.CUSTOM) {
                item {
                    OutlinedTextField(
                        value = viewModel.customSizeInput,
                        onValueChange = { viewModel.customSizeInput = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.text_splitter_custom_size_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Text input
            item {
                OutlinedTextField(
                    value = viewModel.inputText,
                    onValueChange = { viewModel.inputText = it },
                    label = { Text(stringResource(R.string.text_splitter_input_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    maxLines = 12
                )
            }

            // Split button
            item {
                Button(
                    onClick = { viewModel.split() },
                    enabled = viewModel.isSplitEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.text_splitter_split_button))
                }
            }

            // Error state
            viewModel.errorMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Copy All button + chunks
            if (viewModel.chunks.isNotEmpty()) {
                item {
                    Button(
                        onClick = { viewModel.copyAll(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.text_splitter_copy_all))
                    }
                }

                itemsIndexed(viewModel.chunks) { index, chunk ->
                    ChunkCard(
                        index = index + 1,
                        chunk = chunk,
                        onCopy = { viewModel.copyChunk(context, chunk) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChunkCard(index: Int, chunk: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index.toArabicNumeral()} — ${chunk.length.toArabicNumeral()} ${stringResource(R.string.text_splitter_chars)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = chunk,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun Int.toArabicNumeral(): String = this.toString().map { c ->
    when (c) {
        '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
        '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
        else -> c
    }
}.joinToString("")
