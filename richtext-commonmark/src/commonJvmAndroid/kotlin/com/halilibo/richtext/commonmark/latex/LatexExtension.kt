package com.halilibo.richtext.commonmark.latex

import org.commonmark.Extension
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.InlineParserFactory
import org.commonmark.parser.Parser
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlNodeRendererFactory
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.html.HtmlRenderer.HtmlRendererExtension
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentNodeRendererFactory
import org.commonmark.renderer.text.TextContentRenderer
import org.commonmark.renderer.text.TextContentRenderer.TextContentRendererExtension

public class LatexExtension private constructor() : ParserExtension,
  HtmlRendererExtension,
  TextContentRendererExtension {

  override fun extend(parserBuilder: Parser.Builder) {
    parserBuilder.inlineParserFactory(object : InlineParserFactory {
      override fun create(inlineParserContext: InlineParserContext): InlineParser {
        return InlineParserImpl(inlineParserContext)
      }
    })
  }

  override fun extend(rendererBuilder: HtmlRenderer.Builder) {
    rendererBuilder.nodeRendererFactory(object : HtmlNodeRendererFactory {
      override fun create(context: HtmlNodeRendererContext): NodeRenderer {
        return LatexHtmlNodeRenderer(context)
      }
    })
  }

  override fun extend(rendererBuilder: TextContentRenderer.Builder) {
    rendererBuilder.nodeRendererFactory(object : TextContentNodeRendererFactory {
      override fun create(context: TextContentNodeRendererContext): NodeRenderer {
        return LatexTextContentNodeRenderer(context)
      }
    })
  }

  public class Builder {

    /**
     * @return a configured extension
     */
    public fun build(): Extension {
      return LatexExtension()
    }
  }

  public companion object {
    /**
     * @return the extension with default options
     */
    public fun create(): Extension {
      return builder().build()
    }

    /**
     * @return a builder to configure the behavior of the extension
     */
    public fun builder(): Builder {
      return Builder()
    }
  }
}
