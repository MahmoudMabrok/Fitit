package tools.mo3ta.fitit.ui.textimage

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel

class TextImageViewModel : ViewModel() {
    var text by mutableStateOf("")
    var backgroundColor by mutableStateOf(Color(0xFF121212))
    var textColor by mutableStateOf(Color.White)
    var padding by mutableStateOf(24f) // in dp
    var aspectRatio by mutableStateOf(1f) // 1:1, 4:5, etc.
    var textAlign by mutableStateOf(TextAlign.Center)
    var fontWeight by mutableStateOf(FontWeight.Normal)
    var backgroundImageUri by mutableStateOf<Uri?>(null)

    val aspectRatios = listOf(
        AspectRatioOption("1:1", 1f),
        AspectRatioOption("4:5", 0.8f),
        AspectRatioOption("9:16", 0.5625f),
        AspectRatioOption("16:9", 1.777f)
    )

    val fontWeights = listOf(
        FontWeightOption("Regular", FontWeight.Normal),
        FontWeightOption("Medium", FontWeight.Medium),
        FontWeightOption("Bold", FontWeight.Bold),
        FontWeightOption("ExtraBold", FontWeight.ExtraBold)
    )

    val textAlignments = listOf(
        TextAlignOption("Left", TextAlign.Left),
        TextAlignOption("Center", TextAlign.Center),
        TextAlignOption("Right", TextAlign.Right)
    )

    fun reset() {
        text = ""
        backgroundColor = Color(0xFF121212)
        textColor = Color.White
        padding = 24f
        aspectRatio = 1f
        textAlign = TextAlign.Center
        fontWeight = FontWeight.Normal
        backgroundImageUri = null
    }
}

data class AspectRatioOption(val name: String, val ratio: Float)
data class FontWeightOption(val name: String, val weight: FontWeight)
data class TextAlignOption(val name: String, val align: TextAlign)
