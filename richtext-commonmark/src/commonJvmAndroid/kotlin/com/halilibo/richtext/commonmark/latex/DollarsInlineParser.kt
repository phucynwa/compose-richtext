package com.halilibo.richtext.commonmark.latex

import org.commonmark.internal.inline.InlineContentParser
import org.commonmark.internal.inline.InlineParserState
import org.commonmark.internal.inline.ParsedInline
import org.commonmark.internal.util.Parsing
import org.commonmark.node.Text

public class DollarsInlineParser : InlineContentParser {
  override fun tryParse(inlineParserState: InlineParserState): ParsedInline {
    val scanner = inlineParserState.scanner()
    val start = scanner.position()
    val openingTicks = scanner.matchMultiple('$')
    val afterOpening = scanner.position()

    while (scanner.find('$') > 0) {
      val beforeClosing = scanner.position()
      val count = scanner.matchMultiple('$')
      if (count == openingTicks) {
        val node = Latex()

        var content = scanner.getSource(afterOpening, beforeClosing).getContent()
        content = content.replace('\n', ' ')

        // spec: If the resulting string both begins and ends with a space character, but does not consist
        // entirely of space characters, a single space character is removed from the front and back.
        if (content.length >= 3 && content.get(0) == ' ' && content.get(content.length - 1) == ' ' &&
          Parsing.hasNonSpace(content)
        ) {
          content = content.substring(1, content.length - 1)
        }

        node.literal = content
        return ParsedInline.of(node, scanner.position())
      }
    }

    // If we got here, we didn't find a matching closing backtick sequence.
    val source = scanner.getSource(start, afterOpening)
    val text = Text(source.getContent())
    return ParsedInline.of(text, afterOpening)
  }
}
