package com.halilibo.richtext.commonmark.latex

import org.commonmark.internal.inline.EmphasisDelimiterProcessor
import org.commonmark.node.Node
import org.commonmark.node.Nodes
import org.commonmark.node.SourceSpans
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun

public class LatexDelimiterProcessor  : EmphasisDelimiterProcessor('$')
