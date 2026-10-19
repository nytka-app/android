package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shape that tells a speaker apart from the others, by shape and not by hue: disc, square, triangle, diamond,
 * ring, plus, bar for [index] 0 to 6. Decorative; the name beside it is the label.
 */
@Composable
fun SpeakerMark(
    index: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    size: Dp = 12.dp,
) {
    Canvas(modifier.size(size)) { drawMark(index, color) }
}

private fun DrawScope.drawMark(
    index: Int,
    color: Color,
) {
    val s = size.minDimension
    val inset = s * 0.08f
    val box = s - 2 * inset
    val middle = s / 2
    when (index.mod(MARKS)) {
        0 -> drawCircle(color, radius = box / 2)
        1 -> drawRect(color, Offset(inset, inset), Size(box, box))
        2 ->
            drawPath(
                Path().apply {
                    moveTo(middle, inset)
                    lineTo(s - inset, s - inset)
                    lineTo(inset, s - inset)
                    close()
                },
                color,
            )

        3 ->
            drawPath(
                Path().apply {
                    moveTo(middle, inset)
                    lineTo(s - inset, middle)
                    lineTo(middle, s - inset)
                    lineTo(inset, middle)
                    close()
                },
                color,
            )

        4 -> drawCircle(color, radius = box / 2 - s * 0.08f, style = Stroke(width = s * 0.16f))
        5 -> {
            val width = s * 0.2f
            drawLine(color, Offset(inset, middle), Offset(s - inset, middle), width, StrokeCap.Butt)
            drawLine(color, Offset(middle, inset), Offset(middle, s - inset), width, StrokeCap.Butt)
        }

        else -> drawRoundRect(color, Offset(middle - s * 0.15f, inset), Size(s * 0.3f, box), CornerRadius(s * 0.1f))
    }
}

private const val MARKS = 7
