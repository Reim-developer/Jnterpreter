package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.lexer.Token;
import org.junit.jupiter.api.Test;

// Token record equality and component accessors.

class TokenTest {

    @Test
    void recordEquality() {
        Span span = new Span("test", 0, 1, 1, 1);
        Token a = new Token("NUMBER", "1", span);
        Token b = new Token("NUMBER", "1", span);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Token("NUMBER", "2", span));
        assertNotEquals(a, new Token("IDENT", "1", span));
        assertNotEquals(a, new Token("NUMBER", "1", new Span("test", 0, 1, 1, 2)));
    }

    @Test
    void componentAccessors() {
        Span span = new Span("test", 3, 4, 1, 4);
        Token token = new Token("+", "+", span);
        assertEquals("+", token.kind());
        assertEquals("+", token.lexeme());
        assertEquals(span, token.span());
    }
}
