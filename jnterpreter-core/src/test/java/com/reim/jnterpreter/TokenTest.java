package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.lexer.Token;
import org.junit.jupiter.api.Test;

// Token record equality and component accessors, with an enum kind.

class TokenTest {

    /** The test language's token kinds. */
    enum Kind { NUMBER, IDENT, PLUS, EOF }

    @Test
    void recordEquality() {
        Span span = new Span("test", 0, 1, 1, 1);
        Token<Kind> a = new Token<>(Kind.NUMBER, "1", span);
        Token<Kind> b = new Token<>(Kind.NUMBER, "1", span);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Token<>(Kind.NUMBER, "2", span));
        assertNotEquals(a, new Token<>(Kind.IDENT, "1", span));
        assertNotEquals(a, new Token<>(Kind.NUMBER, "1", new Span("test", 0, 1, 1, 2)));
    }

    @Test
    void componentAccessors() {
        Span span = new Span("test", 3, 4, 1, 4);
        Token<Kind> token = new Token<>(Kind.PLUS, "+", span);
        assertEquals(Kind.PLUS, token.kind());
        assertEquals("+", token.lexeme());
        assertEquals(span, token.span());
    }
}
