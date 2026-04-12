package tools.mo3ta.fitit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToOpenWa: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Fit It", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text("Content Creator Toolkit", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        val tools = listOf(
            ToolItem(
                title = "Text in Image",
                description = "Fit any text into a HQ image with custom styles",
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFF007AFF),
                onClick = onNavigateToTextImage
            ),
            ToolItem(
                title = "Invisible Text",
                description = "Generate invisible text for social apps",
                icon = Icons.Default.VisibilityOff,
                color = Color(0xFF5856D6),
                onClick = onNavigateToEmptyText
            ),
            ToolItem(
                title = "Open in WA",
                description = "Direct message any unsaved WhatsApp number",
                icon = Icons.Default.Chat,
                color = Color(0xFF25D366),
                onClick = onNavigateToOpenWa
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            items(tools) { tool ->
                ToolCard(tool)
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { tool.onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
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
                Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(tool.description, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

data class ToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
