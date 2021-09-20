package com.halilibo.richtext.ui.string

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import com.halilibo.richtext.ui.ClickableText
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.currentContentColor
import com.halilibo.richtext.ui.currentRichTextStyle
import com.halilibo.richtext.ui.string.RichTextString.Format

/**
 * Renders a [RichTextString] as created with [richTextString].
 *
 * @sample com.halilibo.richtext.ui.previews.TextPreview
 */
@Composable
public fun RichTextScope.Text(
  text: RichTextString,
  modifier: Modifier = Modifier,
  onTextLayout: (TextLayoutResult) -> Unit = {},
  softWrap: Boolean = true,
  overflow: TextOverflow = TextOverflow.Clip,
  maxLines: Int = Int.MAX_VALUE
) {
  val style = currentRichTextStyle.stringStyle
  val contentColor = currentContentColor
  val annotated = remember(text, style, contentColor) {
    val resolvedStyle = (style ?: RichTextStringStyle.Default).resolveDefaults()
    text.toAnnotatedString(resolvedStyle, contentColor)
  }

  val inlineContents = remember(text) { text.getInlineContents() }

  BoxWithConstraints(modifier = modifier) {
    val inlineTextContents = manageInlineTextContents(
      inlineContents = inlineContents,
      textConstraints = constraints
    )

    ClickableText(
      text = annotated,
      onTextLayout = onTextLayout,
      inlineContent = inlineTextContents,
      softWrap = softWrap,
      overflow = overflow,
      maxLines = maxLines,
      onClick = { offset ->
        annotated.getStringAnnotations(Format.FormatAnnotationScope, offset, offset)
          .asSequence()
          .mapNotNull {
            Format.findTag(
              it.item,
              text.formatObjects
            ) as? Format.Link
          }
          .firstOrNull()
          ?.let { link -> link.onClick() }
      }
    )
  }
}