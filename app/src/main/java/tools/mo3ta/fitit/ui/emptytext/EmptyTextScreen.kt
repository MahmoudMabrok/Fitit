package tools.mo3ta.fitit.ui.emptytext

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EmptyTextScreen(
    onBack: () -> Unit,
    viewModel: EmptyTextViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("empty_text")
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tricky_content_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview Box (Outlined Card) — shows the generated tricky content.
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = viewModel.generateText(),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = modeLabel(viewModel.selectedMode),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Tricky content type selection (Chips)
            Text(
                text = stringResource(R.string.tricky_type),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrickyContentType.values().forEach { mode ->
                    val isSelected = viewModel.selectedMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onModeSelected(mode) },
                        label = { Text(modeLabel(mode)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Per-mode control: a text field, a slider, and/or a voice/video toggle.
            val mode = viewModel.selectedMode

            if (mode.usesText) {
                OutlinedTextField(
                    value = viewModel.textInput,
                    onValueChange = { viewModel.textInput = it },
                    label = { Text(textFieldLabel(mode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            if (mode.usesVideoToggle) {
                val labels = toggleLabels(mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !viewModel.toggleVideo,
                        onClick = { viewModel.toggleVideo = false },
                        label = { Text(labels.first) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    FilterChip(
                        selected = viewModel.toggleVideo,
                        onClick = { viewModel.toggleVideo = true },
                        label = { Text(labels.second) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (mode.usesSlider) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sliderTitle(mode.sliderKind),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = viewModel.sliderDisplay(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                }
                Slider(
                    value = viewModel.sliderValue,
                    onValueChange = { viewModel.sliderValue = it },
                    valueRange = mode.sliderRange,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    )
                )
            }

            Spacer(Modifier.height(32.dp))

            // Copy Button
            Button(
                onClick = {
                    viewModel.copyToClipboard(context)
                    val length = if (viewModel.selectedMode.usesSlider)
                        viewModel.sliderValue.toInt() else 0
                    AnalyticsManager.trackEmptyTextCopied(viewModel.selectedMode.name, length)
                    Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.copy_to_clipboard), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Localized chip/caption label for each mode. */
@Composable
private fun modeLabel(mode: TrickyContentType): String = stringResource(
    when (mode) {
        TrickyContentType.INVISIBLE -> R.string.tricky_mode_invisible
        TrickyContentType.VOICE_MESSAGE -> R.string.tricky_mode_voice
        TrickyContentType.TYPING -> R.string.tricky_mode_typing
        TrickyContentType.RECORDING -> R.string.tricky_mode_recording
        TrickyContentType.MISSED_CALL -> R.string.tricky_mode_missed_call
        TrickyContentType.PHOTO -> R.string.tricky_mode_photo
        TrickyContentType.VIEW_ONCE -> R.string.tricky_mode_view_once
        TrickyContentType.DOCUMENT -> R.string.tricky_mode_document
        TrickyContentType.LOCATION -> R.string.tricky_mode_location
        TrickyContentType.CONTACT -> R.string.tricky_mode_contact
        TrickyContentType.DOWNLOAD -> R.string.tricky_mode_download
        TrickyContentType.SPINNER -> R.string.tricky_mode_spinner
        TrickyContentType.POLL -> R.string.tricky_mode_poll
        TrickyContentType.QUOTE -> R.string.tricky_mode_quote
    }
)

/** Localized label for the text input of a text-based mode. */
@Composable
private fun textFieldLabel(mode: TrickyContentType): String = stringResource(
    when (mode) {
        TrickyContentType.DOCUMENT -> R.string.tricky_input_file
        TrickyContentType.POLL -> R.string.tricky_input_poll
        TrickyContentType.QUOTE -> R.string.tricky_input_quote
        else -> R.string.tricky_input_name
    }
)

/** Localized slider title for each numeric kind. */
@Composable
private fun sliderTitle(kind: SliderKind): String = stringResource(
    when (kind) {
        SliderKind.COUNT -> R.string.length
        SliderKind.PERCENT -> R.string.tricky_progress
        else -> R.string.tricky_duration
    }
)

/** The two voice/video (or photo/video) toggle labels for a mode. */
@Composable
private fun toggleLabels(mode: TrickyContentType): Pair<String, String> =
    if (mode == TrickyContentType.VIEW_ONCE) {
        stringResource(R.string.tricky_media_photo) to stringResource(R.string.tricky_media_video)
    } else {
        stringResource(R.string.tricky_call_voice) to stringResource(R.string.tricky_call_video)
    }
