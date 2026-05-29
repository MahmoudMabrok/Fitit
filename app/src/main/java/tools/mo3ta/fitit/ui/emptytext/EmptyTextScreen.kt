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
            // Preview Box (Outlined Card)
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
                    // This space contains the invisible generated text
                    Text(
                        text = viewModel.generateText(),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )
                    
                    Spacer(Modifier.height(16.dp))

                    val caption = when (viewModel.selectedMode) {
                        TrickyContentType.INVISIBLE ->
                            "${viewModel.charCount.toInt()} ${stringResource(R.string.tricky_chars_unit)} · ${stringResource(R.string.tricky_mode_invisible)}"
                        TrickyContentType.VOICE_MESSAGE ->
                            "${stringResource(R.string.tricky_mode_voice)} · ${viewModel.formattedDuration()}"
                    }
                    Text(
                        text = caption,
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
                val modeLabels = mapOf(
                    TrickyContentType.INVISIBLE to stringResource(R.string.tricky_mode_invisible),
                    TrickyContentType.VOICE_MESSAGE to stringResource(R.string.tricky_mode_voice)
                )
                TrickyContentType.values().forEach { mode ->
                    val isSelected = viewModel.selectedMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedMode = mode },
                        label = { Text(modeLabels.getValue(mode)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Slider — length (invisible) or duration (voice message)
            val isInvisible = viewModel.selectedMode == TrickyContentType.INVISIBLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isInvisible) stringResource(R.string.length)
                    else stringResource(R.string.tricky_duration),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isInvisible) "${viewModel.charCount.toInt()}"
                    else viewModel.formattedDuration(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp
                )
            }

            if (isInvisible) {
                Slider(
                    value = viewModel.charCount,
                    onValueChange = { viewModel.charCount = it },
                    valueRange = 1f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    )
                )
            } else {
                Slider(
                    value = viewModel.durationSeconds,
                    onValueChange = { viewModel.durationSeconds = it },
                    valueRange = 1f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    )
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            // Copy Button
            Button(
                onClick = {
                    viewModel.copyToClipboard(context)
                    val length = if (viewModel.selectedMode == TrickyContentType.INVISIBLE)
                        viewModel.charCount.toInt() else viewModel.durationSeconds.toInt()
                    AnalyticsManager.trackEmptyTextCopied(viewModel.selectedMode.name, length)
                    // Showing a Toast as requested, but also have Snackbar host if needed
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
