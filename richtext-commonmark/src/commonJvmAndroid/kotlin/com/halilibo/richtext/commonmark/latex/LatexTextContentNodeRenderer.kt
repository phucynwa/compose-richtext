package com.halilibo.richtext.commonmark.latex

import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentWriter

public class LatexTextContentNodeRenderer(
  private val context: TextContentNodeRendererContext
) : NodeRenderer {

  private val textContent: TextContentWriter = context.writer

  override fun getNodeTypes(): MutableSet<Class<out Node?>?> {
    return mutableSetOf<Class<out Node?>?>(Latex::class.java)
  }

  override fun render(node: Node) {
    textContent.write('/')
    renderChildren(node)
    textContent.write('/')
  }

  private fun renderChildren(parent: Node) {
    var node = parent.getFirstChild()
    while (node != null) {
      val next = node.getNext()
      context.render(node)
      node = next
    }
  }
}
