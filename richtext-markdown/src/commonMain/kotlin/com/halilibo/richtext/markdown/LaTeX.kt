package com.halilibo.richtext.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit

@Composable
internal expect fun LaTeX(
  laTeX: String,
  fontSize: TextUnit = TextUnit.Unspecified
)
