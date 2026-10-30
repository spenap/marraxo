package com.simonpena.mako

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The prototype's icons (docs/mockups/prototype.html), same 24×24 SVG paths. */
object GlowIcons {
    val Play = filled("M8 5v14l11-7z")
    val Pause = filled("M7 5h3.5v14H7zM13.5 5H17v14h-3.5z")
    val Previous = filled("M6 6h2v12H6zM9.5 12L18 6v12z")
    val Next = filled("M16 6h2v12h-2zM6 6l8.5 6L6 18z")
    val Music = filled("M9 18.5a3 3 0 1 1-2-2.83V5l12-2v13.5a3 3 0 1 1-2-2.83V7.2l-8 1.4z")
    val Down = stroked("M6 9l6 6 6-6")
    val Back = stroked("M19 12H5M11 6l-6 6 6 6", width = 1.9f)
    val Shuffle = stroked("M3 7h3.5l10 10H20M3 17h3.5l3-3M13.5 10l3-3H20M17.5 4.5L20 7l-2.5 2.5M17.5 14.5L20 17l-2.5 2.5")
    val Repeat = stroked("M4 11V9.5A2.5 2.5 0 0 1 6.5 7H19M16 4l3 3-3 3M20 13v1.5a2.5 2.5 0 0 1-2.5 2.5H5M8 20l-3-3 3-3")
    val Phone = stroked("M9 2.5h6a2 2 0 0 1 2 2v15a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-15a2 2 0 0 1 2-2zM11 18.5h2")
    val Speaker = stroked("M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM15.2 14a3.2 3.2 0 1 1-6.4 0a3.2 3.2 0 1 1 6.4 0zM12.9 7.5a.9 .9 0 1 1-1.8 0a.9 .9 0 1 1 1.8 0z")
    val Tv = stroked("M4 4.5h16a1.5 1.5 0 0 1 1.5 1.5v9.5a1.5 1.5 0 0 1-1.5 1.5H4a1.5 1.5 0 0 1-1.5-1.5V6A1.5 1.5 0 0 1 4 4.5zM8 20.5h8")
    val More = filled("M12 4.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2zM12 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2zM12 16.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2z")
    val Check = stroked("M5 12.5l4.5 4.5L19 7", width = 2.2f)
}

private fun builder() = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
)

private fun filled(d: String): ImageVector = builder()
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
    .build()

private fun stroked(d: String, width: Float = 1.8f): ImageVector = builder()
    .addPath(
        PathParser().parsePathString(d).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()
