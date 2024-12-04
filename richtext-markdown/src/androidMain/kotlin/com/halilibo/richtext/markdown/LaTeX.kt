package com.halilibo.richtext.markdown

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
public actual fun LaTeX(laTeX: String, fontSize: TextUnit) {
  val textStyle = LocalTextStyle.current
  val color = LocalContentColor.current
  val density = LocalDensity.current
  val textSize = with(density) {
    (if (fontSize == TextUnit.Unspecified) textStyle.fontSize else fontSize).toPx()
  }
  val drawable = remember(laTeX, textSize, color) {
    runCatching {
      JLatexMathDrawable.builder(laTeX)
        .textSize(textSize)
        .padding(0)
        .color(color.toArgb())
        .background(Color.Transparent.toArgb())
        .align(JLatexMathDrawable.ALIGN_RIGHT)
        .build()
    }
  }.getOrNull()
  AsyncImage(
    model = drawable?.current,
    contentDescription = null
  )
}
