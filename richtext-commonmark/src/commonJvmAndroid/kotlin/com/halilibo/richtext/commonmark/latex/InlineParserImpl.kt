package com.halilibo.richtext.commonmark.latex

import org.commonmark.internal.Bracket
import org.commonmark.internal.Delimiter
import org.commonmark.internal.inline.AsteriskDelimiterProcessor
import org.commonmark.internal.inline.AutolinkInlineParser
import org.commonmark.internal.inline.BackslashInlineParser
import org.commonmark.internal.inline.BackticksInlineParser
import org.commonmark.internal.inline.EntityInlineParser
import org.commonmark.internal.inline.HtmlInlineParser
import org.commonmark.internal.inline.InlineContentParser
import org.commonmark.internal.inline.InlineParserState
import org.commonmark.internal.inline.ParsedInlineImpl
import org.commonmark.internal.inline.Scanner
import org.commonmark.internal.inline.UnderscoreDelimiterProcessor
import org.commonmark.internal.util.Escaping
import org.commonmark.internal.util.LinkScanner
import org.commonmark.internal.util.Parsing
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.SourceSpans
import org.commonmark.node.Text
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.SourceLines
import org.commonmark.parser.delimiter.DelimiterProcessor
import java.util.Arrays
import java.util.BitSet

public class InlineParserImpl(private val context: InlineParserContext) : InlineParser,
  InlineParserState {
  private val specialCharacters: BitSet
  private val delimiterProcessors: MutableMap<Char, DelimiterProcessor?>
  private val inlineParsers: MutableMap<Char, MutableList<InlineContentParser>?>

  private var scanner: Scanner? = null
  private var includeSourceSpans = false
  private var trailingSpaces = 0

  /**
   * Top delimiter (emphasis, strong emphasis or custom emphasis). (Brackets are on a separate stack, different
   * from the algorithm described in the spec.)
   */
  private var lastDelimiter: Delimiter? = null

  /**
   * Top opening bracket (`[` or `![)`).
   */
  private var lastBracket: Bracket? = null

  init {
    this.delimiterProcessors = calculateDelimiterProcessors(
      context.getCustomDelimiterProcessors()
    )

    this.inlineParsers = HashMap<Char, MutableList<InlineContentParser>?>()
    this.inlineParsers.put('\\', mutableListOf(BackslashInlineParser()))
    this.inlineParsers.put('`', mutableListOf(BackticksInlineParser()))
    this.inlineParsers.put('&', mutableListOf(EntityInlineParser()))
    this.inlineParsers.put('<', mutableListOf(AutolinkInlineParser(), HtmlInlineParser()))
    this.inlineParsers.put('$', mutableListOf(DollarsInlineParser()))

    this.specialCharacters =
      Companion.calculateSpecialCharacters(this.delimiterProcessors.keys, inlineParsers.keys)
  }

  override fun scanner(): Scanner {
    return scanner!!
  }

  /**
   * Parse content in block into inline children, appending them to the block node.
   */
  override fun parse(lines: SourceLines, block: Node) {
    reset(lines)

    while (true) {
      val nodes = parseInline()
      if (nodes != null) {
        for (node in nodes) {
          block.appendChild(node)
        }
      } else {
        break
      }
    }

    processDelimiters(null)
    mergeChildTextNodes(block)
  }

  public fun reset(lines: SourceLines) {
    this.scanner = Scanner.of(lines)
    this.includeSourceSpans = !lines.getSourceSpans().isEmpty()
    this.trailingSpaces = 0
    this.lastDelimiter = null
    this.lastBracket = null
  }

  private fun text(sourceLines: SourceLines): Text {
    val text = Text(sourceLines.getContent())
    text.setSourceSpans(sourceLines.getSourceSpans())
    return text
  }

  /**
   * Parse the next inline element in subject, advancing our position.
   * On success, return the new inline node.
   * On failure, return null.
   */
  private fun parseInline(): MutableList<out Node?>? {
    val c = scanner!!.peek()

    when (c) {
      '[' -> return mutableListOf<Node?>(parseOpenBracket())
      '!' -> return mutableListOf<Node?>(parseBang())
      ']' -> return mutableListOf<Node?>(parseCloseBracket())
      '\n' -> return mutableListOf<Node?>(parseLineBreak())
      Scanner.END -> return null
    }

    // No inline parser, delimiter or other special handling.
    if (!specialCharacters.get(c.code)) {
      return mutableListOf<Node?>(parseText())
    }

    val inlineParsers = this.inlineParsers.get(c)
    if (inlineParsers != null) {
      val position = scanner!!.position()
      for (inlineParser in inlineParsers) {
        val parsedInline = inlineParser.tryParse(this)
        if (parsedInline is ParsedInlineImpl) {
          val parsedInlineImpl = parsedInline
          val node = parsedInlineImpl.getNode()
          scanner!!.setPosition(parsedInlineImpl.getPosition())
          if (includeSourceSpans && node.getSourceSpans().isEmpty()) {
            node.setSourceSpans(
              scanner!!.getSource(position, scanner!!.position()).getSourceSpans()
            )
          }
          return mutableListOf<Node?>(node)
        } else {
          // Reset position
          scanner!!.setPosition(position)
        }
      }
    }

    val delimiterProcessor = delimiterProcessors.get(c)
    if (delimiterProcessor != null) {
      val nodes = parseDelimiters(delimiterProcessor, c)
      if (nodes != null) {
        return nodes
      }
    }

    // If we get here, even for a special/delimiter character, we will just treat it as text.
    return mutableListOf<Node?>(parseText())
  }

  /**
   * Attempt to parse delimiters like emphasis, strong emphasis or custom delimiters.
   */
  private fun parseDelimiters(
    delimiterProcessor: DelimiterProcessor,
    delimiterChar: Char
  ): MutableList<out Node?>? {
    val res = scanDelimiters(delimiterProcessor, delimiterChar)
    if (res == null) {
      return null
    }

    val characters = res.characters

    // Add entry to stack for this opener
    lastDelimiter =
      Delimiter(characters, delimiterChar, res.canOpen, res.canClose, lastDelimiter)
    if (lastDelimiter!!.previous != null) {
      lastDelimiter!!.previous.next = lastDelimiter
    }

    return characters
  }

  /**
   * Add open bracket to delimiter stack and add a text node to block's children.
   */
  private fun parseOpenBracket(): Node {
    val start = scanner!!.position()
    scanner!!.next()
    val contentPosition = scanner!!.position()

    val node = text(scanner!!.getSource(start, contentPosition))

    // Add entry to stack for this opener
    addBracket(Bracket.link(node, start, contentPosition, lastBracket, lastDelimiter))

    return node
  }

  /**
   * If next character is [, and ! delimiter to delimiter stack and add a text node to block's children.
   * Otherwise just add a text node.
   */
  private fun parseBang(): Node {
    val start = scanner!!.position()
    scanner!!.next()
    if (scanner!!.next('[')) {
      val contentPosition = scanner!!.position()
      val node = text(scanner!!.getSource(start, contentPosition))

      // Add entry to stack for this opener
      addBracket(Bracket.image(node, start, contentPosition, lastBracket, lastDelimiter))
      return node
    } else {
      return text(scanner!!.getSource(start, scanner!!.position()))
    }
  }

  /**
   * Try to match close bracket against an opening in the delimiter stack. Return either a link or image, or a
   * plain [ character. If there is a matching delimiter, remove it from the delimiter stack.
   */
  private fun parseCloseBracket(): Node {
    val beforeClose = scanner!!.position()
    scanner!!.next()
    val afterClose = scanner!!.position()

    // Get previous `[` or `![`
    val opener = lastBracket
    if (opener == null) {
      // No matching opener, just return a literal.
      return text(scanner!!.getSource(beforeClose, afterClose))
    }

    if (!opener.allowed) {
      // Matching opener but it's not allowed, just return a literal.
      removeLastBracket()
      return text(scanner!!.getSource(beforeClose, afterClose))
    }

    // Check to see if we have a link/image
    var dest: String? = null
    var title: String? = null

    // Maybe a inline link like `[foo](/uri "title")`
    if (scanner!!.next('(')) {
      scanner!!.whitespace()
      dest = parseLinkDestination(scanner!!)
      if (dest == null) {
        scanner!!.setPosition(afterClose)
      } else {
        val whitespace = scanner!!.whitespace()
        // title needs a whitespace before
        if (whitespace >= 1) {
          title = parseLinkTitle(scanner!!)
          scanner!!.whitespace()
        }
        if (!scanner!!.next(')')) {
          // Don't have a closing `)`, so it's not a destination and title -> reset.
          // Note that something like `[foo](` could be valid, `(` will just be text.
          scanner!!.setPosition(afterClose)
          dest = null
          title = null
        }
      }
    }

    // Maybe a reference link like `[foo][bar]`, `[foo][]` or `[foo]`.
    // Note that even `[foo](` could be a valid link if there's a reference, which is why this is not just an `else`
    // here.
    if (dest == null) {
      // See if there's a link label like `[bar]` or `[]`
      var ref = parseLinkLabel(scanner!!)
      if (ref == null) {
        scanner!!.setPosition(afterClose)
      }
      if ((ref == null || ref.isEmpty()) && !opener.bracketAfter) {
        // If the second label is empty `[foo][]` or missing `[foo]`, then the first label is the reference.
        // But it can only be a reference when there's no (unescaped) bracket in it.
        // If there is, we don't even need to try to look up the reference. This is an optimization.
        ref = scanner!!.getSource(opener.contentPosition, beforeClose).getContent()
      }

      if (ref != null) {
        val definition = context.getLinkReferenceDefinition(ref)
        if (definition != null) {
          dest = definition.getDestination()
          title = definition.getTitle()
        }
      }
    }

    if (dest != null) {
      // If we got here, we have a link or image
      val linkOrImage = if (opener.image) Image(dest, title) else Link(dest, title)

      // Add all nodes between the opening bracket and now (closing bracket) as child nodes of the link
      var node = opener.node.getNext()
      while (node != null) {
        val next = node.getNext()
        linkOrImage.appendChild(node)
        node = next
      }

      if (includeSourceSpans) {
        linkOrImage.setSourceSpans(
          scanner!!.getSource(
            opener.markerPosition,
            scanner!!.position()
          ).getSourceSpans()
        )
      }

      // Process delimiters such as emphasis inside link/image
      processDelimiters(opener.previousDelimiter)
      mergeChildTextNodes(linkOrImage)
      // We don't need the corresponding text node anymore, we turned it into a link/image node
      opener.node.unlink()
      removeLastBracket()

      // Links within links are not allowed. We found this link, so there can be no other link around it.
      if (!opener.image) {
        var bracket = lastBracket
        while (bracket != null) {
          if (!bracket.image) {
            // Disallow link opener. It will still get matched, but will not result in a link.
            bracket.allowed = false
          }
          bracket = bracket.previous
        }
      }

      return linkOrImage
    } else {
      // No link or image, parse just the bracket as text and continue
      removeLastBracket()

      scanner!!.setPosition(afterClose)
      return text(scanner!!.getSource(beforeClose, afterClose))
    }
  }

  private fun addBracket(bracket: Bracket?) {
    if (lastBracket != null) {
      lastBracket!!.bracketAfter = true
    }
    lastBracket = bracket
  }

  private fun removeLastBracket() {
    lastBracket = lastBracket!!.previous
  }

  /**
   * Attempt to parse link destination, returning the string or null if no match.
   */
  private fun parseLinkDestination(scanner: Scanner): String? {
    val delimiter = scanner.peek()
    val start = scanner.position()
    if (!LinkScanner.scanLinkDestination(scanner)) {
      return null
    }

    val dest: String?
    if (delimiter == '<') {
      // chop off surrounding <..>:
      val rawDestination = scanner.getSource(start, scanner.position()).getContent()
      dest = rawDestination.substring(1, rawDestination.length - 1)
    } else {
      dest = scanner.getSource(start, scanner.position()).getContent()
    }

    return Escaping.unescapeString(dest)
  }

  /**
   * Attempt to parse link title (sans quotes), returning the string or null if no match.
   */
  private fun parseLinkTitle(scanner: Scanner): String? {
    val start = scanner.position()
    if (!LinkScanner.scanLinkTitle(scanner)) {
      return null
    }

    // chop off ', " or parens
    val rawTitle = scanner.getSource(start, scanner.position()).getContent()
    val title = rawTitle.substring(1, rawTitle.length - 1)
    return Escaping.unescapeString(title)
  }

  /**
   * Attempt to parse a link label, returning the label between the brackets or null.
   */
  public fun parseLinkLabel(scanner: Scanner): String? {
    if (!scanner.next('[')) {
      return null
    }

    val start = scanner.position()
    if (!LinkScanner.scanLinkLabelContent(scanner)) {
      return null
    }
    val end = scanner.position()

    if (!scanner.next(']')) {
      return null
    }

    val content = scanner.getSource(start, end).getContent()
    // spec: A link label can have at most 999 characters inside the square brackets.
    if (content.length > 999) {
      return null
    }

    return content
  }

  private fun parseLineBreak(): Node {
    scanner!!.next()

    if (trailingSpaces >= 2) {
      return HardLineBreak()
    } else {
      return SoftLineBreak()
    }
  }

  /**
   * Parse the next character as plain text, and possibly more if the following characters are non-special.
   */
  private fun parseText(): Node {
    val start = scanner!!.position()
    scanner!!.next()
    var c: Char
    while (true) {
      c = scanner!!.peek()
      if (c == Scanner.END || specialCharacters.get(c.code)) {
        break
      }
      scanner!!.next()
    }

    val source = scanner!!.getSource(start, scanner!!.position())
    var content = source.getContent()

    if (c == '\n') {
      // We parsed until the end of the line. Trim any trailing spaces and remember them (for hard line breaks).
      val end = Parsing.skipBackwards(' ', content, content.length - 1, 0) + 1
      trailingSpaces = content.length - end
      content = content.substring(0, end)
    } else if (c == Scanner.END) {
      // For the last line, both tabs and spaces are trimmed for some reason (checked with commonmark.js).
      val end = Parsing.skipSpaceTabBackwards(content, content.length - 1, 0) + 1
      content = content.substring(0, end)
    }

    val text = Text(content)
    text.setSourceSpans(source.getSourceSpans())
    return text
  }

  /**
   * Scan a sequence of characters with code delimiterChar, and return information about the number of delimiters
   * and whether they are positioned such that they can open and/or close emphasis or strong emphasis.
   *
   * @return information about delimiter run, or `null`
   */
  private fun scanDelimiters(
    delimiterProcessor: DelimiterProcessor,
    delimiterChar: Char
  ): DelimiterData? {
    val before = scanner!!.peekPreviousCodePoint()
    val start = scanner!!.position()

    // Quick check to see if we have enough delimiters.
    val delimiterCount = scanner!!.matchMultiple(delimiterChar)
    if (delimiterCount < delimiterProcessor.getMinLength()) {
      scanner!!.setPosition(start)
      return null
    }

    // We do have enough, extract a text node for each delimiter character.
    val delimiters: MutableList<Text?> = ArrayList<Text?>()
    scanner!!.setPosition(start)
    var positionBefore = start
    while (scanner!!.next(delimiterChar)) {
      delimiters.add(text(scanner!!.getSource(positionBefore, scanner!!.position())))
      positionBefore = scanner!!.position()
    }

    val after = scanner!!.peekCodePoint()

    // We could be more lazy here, in most cases we don't need to do every match case.
    val beforeIsPunctuation =
      before == Scanner.END.code || Parsing.isPunctuationCodePoint(before)
    val beforeIsWhitespace = before == Scanner.END.code || Parsing.isWhitespaceCodePoint(before)
    val afterIsPunctuation = after == Scanner.END.code || Parsing.isPunctuationCodePoint(after)
    val afterIsWhitespace = after == Scanner.END.code || Parsing.isWhitespaceCodePoint(after)

    val leftFlanking = !afterIsWhitespace &&
        (!afterIsPunctuation || beforeIsWhitespace || beforeIsPunctuation)
    val rightFlanking = !beforeIsWhitespace &&
        (!beforeIsPunctuation || afterIsWhitespace || afterIsPunctuation)
    val canOpen: Boolean
    val canClose: Boolean
    if (delimiterChar == '_') {
      canOpen = leftFlanking && (!rightFlanking || beforeIsPunctuation)
      canClose = rightFlanking && (!leftFlanking || afterIsPunctuation)
    } else {
      canOpen = leftFlanking && delimiterChar == delimiterProcessor.getOpeningCharacter()
      canClose = rightFlanking && delimiterChar == delimiterProcessor.getClosingCharacter()
    }

    return DelimiterData(delimiters, canOpen, canClose)
  }

  private fun processDelimiters(stackBottom: Delimiter?) {
    val openersBottom: MutableMap<Char?, Delimiter?> = HashMap<Char?, Delimiter?>()

    // find first closer above stackBottom:
    var closer = lastDelimiter
    while (closer != null && closer.previous !== stackBottom) {
      closer = closer.previous
    }
    // move forward, looking for closers, and handling each
    while (closer != null) {
      val delimiterChar = closer.delimiterChar

      val delimiterProcessor = delimiterProcessors.get(delimiterChar)
      if (!closer.canClose() || delimiterProcessor == null) {
        closer = closer.next
        continue
      }

      val openingDelimiterChar = delimiterProcessor.getOpeningCharacter()

      // Found delimiter closer. Now look back for first matching opener.
      var usedDelims = 0
      var openerFound = false
      var potentialOpenerFound = false
      var opener = closer.previous
      while (opener != null && opener !== stackBottom && opener !== openersBottom.get(
          delimiterChar
        )
      ) {
        if (opener.canOpen() && opener.delimiterChar == openingDelimiterChar) {
          potentialOpenerFound = true
          usedDelims = delimiterProcessor.process(opener, closer)
          if (usedDelims > 0) {
            openerFound = true
            break
          }
        }
        opener = opener.previous
      }

      if (!openerFound) {
        if (!potentialOpenerFound) {
          // Set lower bound for future searches for openers.
          // Only do this when we didn't even have a potential
          // opener (one that matches the character and can open).
          // If an opener was rejected because of the number of
          // delimiters (e.g. because of the "multiple of 3" rule),
          // we want to consider it next time because the number
          // of delimiters can change as we continue processing.
          openersBottom.put(delimiterChar, closer.previous)
          if (!closer.canOpen()) {
            // We can remove a closer that can't be an opener,
            // once we've seen there's no matching opener:
            removeDelimiterKeepNode(closer)
          }
        }
        closer = closer.next
        continue
      }

      // Remove number of used delimiters nodes.
      for (i in 0..<usedDelims) {
        val delimiter = opener!!.characters.removeAt(opener.characters.size - 1)
        delimiter.unlink()
      }
      for (i in 0..<usedDelims) {
        val delimiter = closer.characters.removeAt(0)
        delimiter.unlink()
      }

      removeDelimitersBetween(opener, closer)

      // No delimiter characters left to process, so we can remove delimiter and the now empty node.
      if (opener!!.length() == 0) {
        removeDelimiterAndNodes(opener)
      }

      if (closer.length() == 0) {
        val next = closer.next
        removeDelimiterAndNodes(closer)
        closer = next
      }
    }

    // remove all delimiters
    while (lastDelimiter != null && lastDelimiter !== stackBottom) {
      removeDelimiterKeepNode(lastDelimiter!!)
    }
  }

  private fun removeDelimitersBetween(opener: Delimiter?, closer: Delimiter) {
    var delimiter = closer.previous
    while (delimiter != null && delimiter !== opener) {
      val previousDelimiter = delimiter.previous
      removeDelimiterKeepNode(delimiter)
      delimiter = previousDelimiter
    }
  }

  /**
   * Remove the delimiter and the corresponding text node. For used delimiters, e.g. `*` in `*foo*`.
   */
  private fun removeDelimiterAndNodes(delim: Delimiter) {
    removeDelimiter(delim)
  }

  /**
   * Remove the delimiter but keep the corresponding node as text. For unused delimiters such as `_` in `foo_bar`.
   */
  private fun removeDelimiterKeepNode(delim: Delimiter) {
    removeDelimiter(delim)
  }

  private fun removeDelimiter(delim: Delimiter) {
    if (delim.previous != null) {
      delim.previous.next = delim.next
    }
    if (delim.next == null) {
      // top of stack
      lastDelimiter = delim.previous
    } else {
      delim.next.previous = delim.previous
    }
  }

  private fun mergeChildTextNodes(node: Node) {
    // No children, no need for merging
    if (node.getFirstChild() == null) {
      return
    }

    mergeTextNodesInclusive(node.getFirstChild(), node.getLastChild())
  }

  private fun mergeTextNodesInclusive(fromNode: Node?, toNode: Node?) {
    var first: Text? = null
    var last: Text? = null
    var length = 0

    var node = fromNode
    while (node != null) {
      if (node is Text) {
        val text = node
        if (first == null) {
          first = text
        }
        length += text.getLiteral().length
        last = text
      } else {
        mergeIfNeeded(first, last, length)
        first = null
        last = null
        length = 0

        mergeChildTextNodes(node)
      }
      if (node === toNode) {
        break
      }
      node = node.getNext()
    }

    mergeIfNeeded(first, last, length)
  }

  private fun mergeIfNeeded(first: Text?, last: Text?, textLength: Int) {
    if (first != null && last != null && first !== last) {
      val sb = StringBuilder(textLength)
      sb.append(first.getLiteral())
      var sourceSpans: SourceSpans? = null
      if (includeSourceSpans) {
        sourceSpans = SourceSpans()
        sourceSpans.addAll(first.getSourceSpans())
      }
      var node = first.getNext()
      val stop = last.getNext()
      while (node !== stop) {
        sb.append((node as Text).getLiteral())
        if (sourceSpans != null) {
          sourceSpans.addAll(node.getSourceSpans())
        }

        val unlink: Node = node
        node = node.getNext()
        unlink.unlink()
      }
      val literal = sb.toString()
      first.setLiteral(literal)
      if (sourceSpans != null) {
        first.setSourceSpans(sourceSpans.getSourceSpans())
      }
    }
  }

  private class DelimiterData(
    val characters: MutableList<Text?>,
    val canOpen: Boolean,
    val canClose: Boolean
  )

  public companion object {
    public fun calculateSpecialCharacters(
      delimiterCharacters: MutableSet<Char>,
      characters: MutableSet<Char>
    ): BitSet {
      val bitSet = BitSet()
      for (c in delimiterCharacters) {
        bitSet.set(c.code)
      }
      for (c in characters) {
        bitSet.set(c.code)
      }
      bitSet.set('['.code)
      bitSet.set(']'.code)
      bitSet.set('!'.code)
      bitSet.set('\n'.code)
      return bitSet
    }

    public fun calculateDelimiterProcessors(delimiterProcessors: MutableList<DelimiterProcessor>): MutableMap<Char, DelimiterProcessor?> {
      val map: MutableMap<Char, DelimiterProcessor?> = HashMap<Char, DelimiterProcessor?>()
      addDelimiterProcessors(
        Arrays.asList<DelimiterProcessor?>(
          AsteriskDelimiterProcessor(),
          UnderscoreDelimiterProcessor()
        ), map
      )
      addDelimiterProcessors(delimiterProcessors, map)
      return map
    }

    private fun addDelimiterProcessors(
      delimiterProcessors: Iterable<DelimiterProcessor>,
      map: MutableMap<Char, DelimiterProcessor?>
    ) {
      for (delimiterProcessor in delimiterProcessors) {
        val opening = delimiterProcessor.getOpeningCharacter()
        val closing = delimiterProcessor.getClosingCharacter()
        if (opening == closing) {
          val old = map.get(opening)
          if (old != null && old.getOpeningCharacter() == old.getClosingCharacter()) {
            val s: StaggeredDelimiterProcessor?
            if (old is StaggeredDelimiterProcessor) {
              s = old
            } else {
              s = StaggeredDelimiterProcessor(opening)
              s.add(old)
            }
            s.add(delimiterProcessor)
            map.put(opening, s)
          } else {
            addDelimiterProcessorForChar(opening, delimiterProcessor, map)
          }
        } else {
          addDelimiterProcessorForChar(opening, delimiterProcessor, map)
          addDelimiterProcessorForChar(closing, delimiterProcessor, map)
        }
      }
    }

    private fun addDelimiterProcessorForChar(
      delimiterChar: Char,
      toAdd: DelimiterProcessor?,
      delimiterProcessors: MutableMap<Char, DelimiterProcessor?>
    ) {
      val existing = delimiterProcessors.put(delimiterChar, toAdd)
      require(existing == null) { "Delimiter processor conflict with delimiter char '" + delimiterChar + "'" }
    }
  }
}
