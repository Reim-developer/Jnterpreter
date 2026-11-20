package com.reim.jnterpreter.diagnostic;

import java.io.Serializable;

/** A half-open range of characters in a named source, with the 1-based
 * line and column where it starts. The lexer stamps a span on every
 * token it produces; the span then travels with the token through
 * parsing and evaluation so errors can point at the exact source text.
 *
 * @param source the name of the source the range belongs to
 * @param start  the 0-based offset of the first character in the range
 * @param end    the 0-based offset just past the last character in the range
 * @param line   the 1-based line of the first character
 * @param col    the 1-based column of the first character
 */
public record Span(String source, int start, int end, int line, int col)
    implements Serializable {}
