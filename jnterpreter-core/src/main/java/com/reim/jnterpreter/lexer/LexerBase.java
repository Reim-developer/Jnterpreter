package com.reim.jnterpreter.lexer;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;

import java.util.ArrayList;
import java.util.List;

/** Base class for lexers: it tracks the current position (offset,
 * line, column) in the input, creates spans, and builds span errors. A
 * language subclasses it and implements {@link #nextToken()} with its
 * own token rules. The kind type {@code K} is defined by the
 * language, typically an enum; the language's kind type must include
 * an EOF kind, passed to the constructor as {@code eofKind}.
 *
 * @param <K> the language's token kind type
 */
public abstract class LexerBase<K> {
    protected final String source;
    protected final String text;
    /** The kind that means end of input, for the EOF token. */
    protected final K eofKind;
    protected int pos = 0;
    protected int line = 1;
    protected int col = 1;

    protected LexerBase(String source, String text, K eofKind) {
        this.source = source;
        this.text = text;
        this.eofKind = eofKind;
    }

    /** Whether the input is fully consumed. */
    protected boolean eof() { return pos >= text.length(); }

    /** The next character, or {@code '\0'} at end of input. */
    protected char peek() { return eof() ? '\0' : text.charAt(pos); }

    /** The character {@code off} positions ahead, or {@code '\0'} past the end. */
    protected char peekAt(int off) {
        return pos + off >= text.length() ? '\0' : text.charAt(pos + off);
    }

    /** Consumes and returns the next character, tracking line and column. */
    protected char advance() {
        char c = text.charAt(pos++);
        if (c == '\n') { line++; col = 1; } else { col++; }
        return c;
    }
    /**
     * Creates a span from a recorded start position to the current one.
     *
     * @param startPos  the offset where the token started
     * @param startLine the 1-based line where the token started
     * @param startCol  the 1-based column where the token started
     * @return the span covering the consumed characters
     */
    protected Span spanFrom(int startPos, int startLine, int startCol) {
        return new Span(source, startPos, pos, startLine, startCol);
    }
    /**
     * Builds a language error for a span.
     *
     * @param message the error message
     * @param span    the source range the error points at
     * @return the error, to be thrown by the caller
     */
    protected LangError error(String message, Span span) {
        return new LangError(message, span);
    }

    /** Scans and returns the next token, or an EOF token at end of input. */
    public abstract Token<K> nextToken();

    /**
     * Tokenizes the whole input.
     *
     * @return the tokens, including a trailing EOF token
     */
    public List<Token<K>> tokenize() {
        List<Token<K>> out = new ArrayList<>();
        Token<K> t;
        do { t = nextToken(); out.add(t); } while (!t.kind().equals(eofKind));
        return out;
    }
}
