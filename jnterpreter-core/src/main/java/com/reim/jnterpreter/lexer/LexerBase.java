package com.reim.jnterpreter.lexer;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;

import java.util.ArrayList;
import java.util.List;

/** Lexer base: position tracking + span creation + error helpers. The
    language subclasses it and implements nextToken(). */
public abstract class LexerBase {
    protected final String source;
    protected final String text;
    protected int pos = 0;
    protected int line = 1;
    protected int col = 1;

    protected LexerBase(String source, String text) {
        this.source = source;
        this.text = text;
    }

    protected boolean eof() { return pos >= text.length(); }
    protected char peek() { return eof() ? '\0' : text.charAt(pos); }
    protected char peekAt(int off) {
        return pos + off >= text.length() ? '\0' : text.charAt(pos + off);
    }
    protected char advance() {
        char c = text.charAt(pos++);
        if (c == '\n') { line++; col = 1; } else { col++; }
        return c;
    }
    protected Span spanFrom(int startPos, int startLine, int startCol) {
        return new Span(source, startPos, pos, startLine, startCol);
    }
    protected LangError error(String message, Span span) {
        return new LangError(message, span);
    }

    public abstract Token nextToken();

    /** Tokenize the whole input (EOF token included). */
    public List<Token> tokenize() {
        List<Token> out = new ArrayList<>();
        Token t;
        do { t = nextToken(); out.add(t); } while (!t.kind().equals("EOF"));
        return out;
    }
}
