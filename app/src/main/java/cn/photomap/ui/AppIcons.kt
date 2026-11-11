package cn.photomap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppIconType {
    LAYERS,
    DATABASE,
    COMPASS,
    HISTORY,
    CROSSHAIR,
    CAMERA_PLUS,
    ARROW_LEFT,
    INFO,
    SEARCH,
    CLOSE,
    CHECK,
    SPARKLES,
    TRASH,
    EDIT
}

/**
 * Clean, consistent 1.8dp-stroke vector icons following ui-ux-pro-max guidelines:
 * - Vector-only assets (no emoji or raster icons)
 * - Consistent stroke width & rounded caps/joins
 * - Decorative beside visible labels (no duplicate accessibility text)
 */
@Composable
fun AppIcon(
    type: AppIconType,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (type) {
            AppIconType.LAYERS -> {
                val top = Path().apply {
                    moveTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.88f, h * 0.36f)
                    lineTo(w * 0.5f, h * 0.56f)
                    lineTo(w * 0.12f, h * 0.36f)
                    close()
                }
                drawPath(top, tint, style = stroke)
                val mid = Path().apply {
                    moveTo(w * 0.14f, h * 0.55f)
                    lineTo(w * 0.5f, h * 0.73f)
                    lineTo(w * 0.86f, h * 0.55f)
                }
                drawPath(mid, tint, style = stroke)
                val bot = Path().apply {
                    moveTo(w * 0.14f, h * 0.71f)
                    lineTo(w * 0.5f, h * 0.88f)
                    lineTo(w * 0.86f, h * 0.71f)
                }
                drawPath(bot, tint, style = stroke)
            }
            AppIconType.DATABASE -> {
                drawOval(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.14f),
                    size = Size(w * 0.64f, h * 0.24f),
                    style = stroke
                )
                val body = Path().apply {
                    moveTo(w * 0.18f, h * 0.26f)
                    lineTo(w * 0.18f, h * 0.74f)
                    cubicTo(w * 0.18f, h * 0.88f, w * 0.82f, h * 0.88f, w * 0.82f, h * 0.74f)
                    lineTo(w * 0.82f, h * 0.26f)
                }
                drawPath(body, tint, style = stroke)
                val midCurve = Path().apply {
                    moveTo(w * 0.18f, h * 0.50f)
                    cubicTo(w * 0.18f, h * 0.64f, w * 0.82f, h * 0.64f, w * 0.82f, h * 0.50f)
                }
                drawPath(midCurve, tint, style = stroke)
            }
            AppIconType.COMPASS -> {
                drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                val diamond = Path().apply {
                    moveTo(w * 0.64f, h * 0.36f)
                    lineTo(w * 0.55f, h * 0.55f)
                    lineTo(w * 0.36f, h * 0.64f)
                    lineTo(w * 0.45f, h * 0.45f)
                    close()
                }
                drawPath(diamond, tint, style = stroke)
            }
            AppIconType.HISTORY -> {
                drawCircle(tint, radius = w * 0.37f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                val hands = Path().apply {
                    moveTo(w * 0.5f, h * 0.30f)
                    lineTo(w * 0.5f, h * 0.50f)
                    lineTo(w * 0.65f, h * 0.59f)
                }
                drawPath(hands, tint, style = stroke)
            }
            AppIconType.CROSSHAIR -> {
                drawCircle(tint, radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.5f))
                drawLine(tint, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.76f), Offset(w * 0.5f, h * 0.92f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.24f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIconType.CAMERA_PLUS -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.26f),
                    size = Size(w * 0.72f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.16f), Offset(w * 0.66f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.41f), Offset(w * 0.5f, h * 0.67f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.37f, h * 0.54f), Offset(w * 0.63f, h * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIconType.ARROW_LEFT -> {
                val arrow = Path().apply {
                    moveTo(w * 0.58f, h * 0.24f)
                    lineTo(w * 0.32f, h * 0.50f)
                    lineTo(w * 0.58f, h * 0.76f)
                }
                drawPath(arrow, tint, style = stroke)
            }
            AppIconType.INFO -> {
                drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.46f), Offset(w * 0.5f, h * 0.69f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = stroke.width * 0.6f, center = Offset(w * 0.5f, h * 0.32f))
            }
            AppIconType.SEARCH -> {
                drawCircle(tint, radius = w * 0.28f, center = Offset(w * 0.44f, h * 0.44f), style = stroke)
                drawLine(tint, Offset(w * 0.65f, h * 0.65f), Offset(w * 0.85f, h * 0.85f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIconType.CLOSE -> {
                drawLine(tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIconType.CHECK -> {
                val check = Path().apply {
                    moveTo(w * 0.22f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.72f)
                    lineTo(w * 0.78f, h * 0.30f)
                }
                drawPath(check, tint, style = stroke)
            }
            AppIconType.SPARKLES -> {
                val star = Path().apply {
                    moveTo(w * 0.5f, h * 0.15f)
                    quadraticTo(w * 0.5f, h * 0.5f, w * 0.85f, h * 0.5f)
                    quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.85f)
                    quadraticTo(w * 0.5f, h * 0.5f, w * 0.15f, h * 0.5f)
                    quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.15f)
                    close()
                }
                drawPath(star, tint, style = stroke)
            }
            AppIconType.TRASH -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.26f), Offset(w * 0.80f, h * 0.26f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.26f, h * 0.26f), Size(w * 0.48f, h * 0.58f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawLine(tint, Offset(w * 0.40f, h * 0.16f), Offset(w * 0.60f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIconType.EDIT -> {
                val pen = Path().apply {
                    moveTo(w * 0.66f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.34f)
                    lineTo(w * 0.36f, h * 0.80f)
                    lineTo(w * 0.18f, h * 0.82f)
                    lineTo(w * 0.20f, h * 0.64f)
                    close()
                }
                drawPath(pen, tint, style = stroke)
            }
        }
    }
}
