package io.github.reimdeveloper.jnterpreter;

import java.io.Serializable;

/** A half-open range in a named source. Line/col are 1-based, stamped by
    the lexer. Spans are dumb data, never recomputed. */
public record Span(String source, int start, int end, int line, int col)
    implements Serializable {}
