package com.halilibo.richtext.commonmark.latex

import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited

public class Latex : CustomNode(), Delimited {
  public var literal: String = ""

  override fun getOpeningDelimiter(): String {
    return DELIMITER
  }

  override fun getClosingDelimiter(): String {
    return DELIMITER
  }

  public companion object {
    public const val DELIMITER: String = "$$"
  }
}
