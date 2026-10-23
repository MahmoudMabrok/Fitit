package tools.mo3ta.fitit.ui.bluesecret

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager

private val XBlue = Color(0xFF1D9BF0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlueSecretScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("blue_secret")
    }

    val context = LocalContext.current
    var mode by remember { mutableStateOf(BlueSecretMode.COLUMN) }
    var input by remember { mutableStateOf("") }
    var intensity by remember { mutableFloatStateOf(36f) }
    var seed by remember { mutableIntStateOf(1) }
    var paste by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf<String?>(null) }
    var triedReveal by remember { mutableStateOf(false) }

    val needsText = mode != BlueSecretMode.COLUMN
    val source = input.trim()
    val output = remember(mode, source, intensity, seed) {
        if (needsText && source.isEmpty()) ""
        else BlueSecret.generate(mode, source.take(80), intensity.toInt(), seed)
    }
    val range = when (mode) {
        BlueSecretMode.COLUMN -> 12f..72f
        BlueSecretMode.ZALGO -> 1f..8f
        BlueSecretMode.SECRET -> 6f..48f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.blue_secret_title),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.blue_secret_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = output.ifBlank { stringResource(R.string.blue_secret_empty) },
                    color = if (output.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else XBlue,
                    fontSize = 18.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.blue_secret_type),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BlueSecretMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = {
                            mode = item
                            intensity = when (item) {
                                BlueSecretMode.COLUMN -> 36f
                                BlueSecretMode.ZALGO -> 3f
                                BlueSecretMode.SECRET -> 18f
                            }
                        },
                        label = { Text(modeLabel(item)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (needsText) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(80) },
                    label = { Text(stringResource(R.string.blue_secret_input)) },
                    placeholder = { Text(stringResource(R.string.blue_secret_input_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.blue_secret_intensity),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = intensity.toInt().toString(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = intensity.coerceIn(range.start, range.endInclusive),
                onValueChange = { intensity = it },
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { seed += 1 },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.blue_secret_reshuffle))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    copyText(context, output)
                    AnalyticsManager.trackBlueSecretCopied(mode.name)
                    Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                },
                enabled = output.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy_to_clipboard), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.blue_secret_reveal_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = paste,
                onValueChange = {
                    paste = it
                    triedReveal = false
                },
                placeholder = { Text(stringResource(R.string.blue_secret_reveal_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    revealed = BlueSecret.reveal(paste)
                    triedReveal = true
                    AnalyticsManager.trackBlueSecretRevealed(revealed != null)
                },
                enabled = paste.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.blue_secret_reveal))
            }
            if (triedReveal) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = revealed?.let { stringResource(R.string.blue_secret_found, it) }
                        ?: stringResource(R.string.blue_secret_none),
                    color = if (revealed == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun modeLabel(mode: BlueSecretMode): String = stringResource(
    when (mode) {
        BlueSecretMode.COLUMN -> R.string.blue_secret_mode_column
        BlueSecretMode.ZALGO -> R.string.blue_secret_mode_zalgo
        BlueSecretMode.SECRET -> R.string.blue_secret_mode_secret
    }
)

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("zaki-blue", text))
}
