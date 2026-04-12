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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EmptyTextScreen(
    onBack: () -> Unit,
    viewModel: EmptyTextViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Invisible Text", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color(0xFF0F0F0F)
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
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // This space contains the invisible generated text
                    Text(
                        text = viewModel.generateText(),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .background(Color(0xFF007AFF).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF007AFF).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "${viewModel.charCount.toInt()} chars · ${viewModel.selectedType.displayName.lowercase()}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Character Type Selection (Chips)
            Text(
                "Character Type",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InvisibleCharType.values().forEach { type ->
                    val isSelected = viewModel.selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedType = type },
                        label = { Text(type.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF007AFF),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Slider & Length Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Length",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    "${viewModel.charCount.toInt()}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF007AFF),
                    fontSize = 16.sp
                )
            }
            
            Slider(
                value = viewModel.charCount,
                onValueChange = { viewModel.charCount = it },
                valueRange = 1f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF007AFF),
                    activeTrackColor = Color(0xFF007AFF)
                )
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            // Copy Button
            Button(
                onClick = {
                    viewModel.copyToClipboard(context)
                    // Showing a Toast as requested, but also have Snackbar host if needed
                    Toast.makeText(context, "Copied! Paste it anywhere", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy to Clipboard", fontWeight = FontWeight.Bold)
            }
        }
    }
}
