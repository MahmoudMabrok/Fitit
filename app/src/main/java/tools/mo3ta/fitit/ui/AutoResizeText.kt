package tools.mo3ta.fitit.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.floor

@Composable
fun AutoResizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = TextStyle.Default,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        val fontSize = remember(text, maxWidthPx, maxHeightPx, style) {
            var low = 10f
            var high = 500f
            var bestSize = low

            if (text.isEmpty()) return@remember bestSize

            // Binary search for the perfect font size
            repeat(15) { // 15 iterations is enough for precision
                val mid = (low + high) / 2
                val result = textMeasurer.measure(
                    text = text,
                    style = style.copy(fontSize = mid.sp),
                    constraints = Constraints(maxWidth = maxWidthPx.toInt())
                )

                if (result.size.height <= maxHeightPx) {
                    bestSize = mid
                    low = mid
                } else {
                    high = mid
                }
            }
            floor(bestSize)
        }

        Text(
            text = text,
            color = color,
            textAlign = textAlign,
            fontSize = fontSize.sp,
            style = style,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
