package com.halilibo.richtext.commonmark.latex

import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlWriter

public class LatexHtmlNodeRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
  private val html: HtmlWriter = context.writer

  override fun getNodeTypes(): MutableSet<Class<out Node?>?> {
    return mutableSetOf<Class<out Node?>?>(Latex::class.java)
  }

  override fun render(node: Node) {
    val attributes = context.extendAttributes(node, "del", mutableMapOf<String?, String?>())
    html.tag("del", attributes)
    renderChildren(node)
    html.tag("/del")
  }

  private fun renderChildren(parent: Node) {
    var node = parent.firstChild
    while (node != null) {
      val next = node.next
      context.render(node)
      node = next
    }
  }
}
