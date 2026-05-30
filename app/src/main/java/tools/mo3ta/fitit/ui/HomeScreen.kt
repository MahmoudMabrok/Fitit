package tools.mo3ta.fitit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tools.mo3ta.fitit.analytics.AnalyticsManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.fitit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToOpenWa: () -> Unit,
    onNavigateToTextSplitter: () -> Unit,
    onNavigateToVideoSplitter: () -> Unit,
    onNavigateToVideoEnhancer: () -> Unit,
    onNavigateToMediaMerger: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Track screen view
    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("home")
    }

    // Launcher must be outside conditional — Compose rules prohibit composable calls inside if
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result ignored */ }

    // Request notification permission once after onboarding (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.home_title),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val textImageTitle = stringResource(R.string.tool_text_image)
        val textImageDesc = stringResource(R.string.tool_text_image_desc)
        val emptyTextTitle = stringResource(R.string.tool_empty_text)
        val emptyTextDesc = stringResource(R.string.tool_empty_text_desc)
        val openWaTitle = stringResource(R.string.open_wa_title)
        val openWaDesc = stringResource(R.string.direct_message_desc)
        val textSplitterTitle = stringResource(R.string.tool_text_splitter)
        val textSplitterDesc = stringResource(R.string.tool_text_splitter_desc)
        val videoSplitterTitle = stringResource(R.string.tool_video_splitter)
        val videoSplitterDesc = stringResource(R.string.tool_video_splitter_desc)
        val videoEnhancerTitle = stringResource(R.string.tool_video_enhancer)
        val videoEnhancerDesc = stringResource(R.string.tool_video_enhancer_desc)
        val mediaMergerTitle = stringResource(R.string.tool_media_merger)
        val mediaMergerDesc = stringResource(R.string.tool_media_merger_desc)

        val tools = remember(textImageTitle, emptyTextTitle, openWaTitle, textSplitterTitle, videoSplitterTitle, videoEnhancerTitle, mediaMergerTitle) {
            listOf(
                ToolItem(textImageTitle, textImageDesc, Icons.Default.AutoAwesome, Color(0xFF007AFF)),
                ToolItem(emptyTextTitle, emptyTextDesc, Icons.Default.VisibilityOff, Color(0xFF5856D6)),
                ToolItem(openWaTitle, openWaDesc, Icons.Default.Chat, Color(0xFF25D366)),
                ToolItem(textSplitterTitle, textSplitterDesc, Icons.Default.ContentCut, Color(0xFFFF9500)),
                ToolItem(videoSplitterTitle, videoSplitterDesc, Icons.Default.VideoLibrary, Color(0xFFFF3B30)),
                ToolItem(videoEnhancerTitle, videoEnhancerDesc, Icons.Default.AutoFixHigh, Color(0xFF8E44FF)),
                ToolItem(mediaMergerTitle, mediaMergerDesc, Icons.Default.MergeType, Color(0xFF00B5AD))
            )
        }

        val onClicks = remember(onNavigateToTextImage, onNavigateToEmptyText, onNavigateToOpenWa, onNavigateToTextSplitter, onNavigateToVideoSplitter, onNavigateToVideoEnhancer, onNavigateToMediaMerger) {
            listOf(onNavigateToTextImage, onNavigateToEmptyText, onNavigateToOpenWa, onNavigateToTextSplitter, onNavigateToVideoSplitter, onNavigateToVideoEnhancer, onNavigateToMediaMerger)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            items(tools.size) { index ->
                ToolCard(tool = tools[index], onClick = onClicks[index])
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(tool.color, tool.color.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = tool.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tool.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class ToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
)
