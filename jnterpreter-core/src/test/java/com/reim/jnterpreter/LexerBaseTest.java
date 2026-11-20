package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.lexer.LexerBase;
import com.reim.jnterpreter.lexer.Token;
import java.util.List;
import org.junit.jupiter.api.Test;

// Position tracking, span creation, and error building in LexerBase,
// plus Token record behavior.

class LexerBaseTest {

    /** A minimal lexer: numbers and '+' only. */
    static final class PlusLexer extends LexerBase {
        PlusLexer(String source, String text) { super(source, text); }

        @Override public Token nextToken() {
            while (!eof() && (peek() == ' ' || peek() == '\n')) advance();
            int startPos = pos, startLine = line, startCol = col;
            if (eof()) return new Token("EOF", "", spanFrom(startPos, startLine, startCol));
            char c = advance();
            if (Character.isDigit(c)) {
                while (Character.isDigit(peek())) advance();
                return new Token("NUMBER", text.substring(startPos, pos),
                    spanFrom(startPos, startLine, startCol));
            }
            if (c == '+') {
                return new Token("+", "+", spanFrom(startPos, startLine, startCol));
            }
            throw error("unexpected character '" + c + "'",
                spanFrom(startPos, startLine, startCol));
        }

        /** Exposes the protected lookahead for the test. */
        char peekAtPublic(int off) { return peekAt(off); }
    }

    @Test
    void tracksLineAndColumn() {
        List<Token> tokens = new PlusLexer("test", "1\n2").tokenize();
        assertEquals(new Span("test", 0, 1, 1, 1), tokens.get(0).span());
        assertEquals(new Span("test", 2, 3, 2, 1), tokens.get(1).span());
        List<Token> spaced = new PlusLexer("test", "1 + 2").tokenize();
        assertEquals(3, spaced.get(1).span().col());
        assertEquals(5, spaced.get(2).span().col());
    }

    @Test
    void spanFromCoversToken() {
        List<Token> tokens = new PlusLexer("test", "12 + 34").tokenize();
        assertEquals(new Span("test", 0, 2, 1, 1), tokens.get(0).span());
        assertEquals(new Span("test", 3, 4, 1, 4), tokens.get(1).span());
        assertEquals(new Span("test", 5, 7, 1, 6), tokens.get(2).span());
    }

    @Test
    void tokenizeEndsWithEof() {
        List<Token> tokens = new PlusLexer("test", "1 + 2").tokenize();
        assertEquals(4, tokens.size());
        assertEquals("EOF", tokens.get(3).kind());
        assertEquals("", tokens.get(3).lexeme());
    }

    @Test
    void errorBuildsLangError() {
        PlusLexer lexer = new PlusLexer("test", "@");
        LangError error = assertThrows(LangError.class, lexer::nextToken);
        assertEquals("unexpected character '@'", error.diag.message());
        assertEquals(new Span("test", 0, 1, 1, 1), error.diag.span());
    }

    @Test
    void peekAtPastEndReturnsNul() {
        PlusLexer lexer = new PlusLexer("test", "1");
        assertEquals('1', lexer.peekAtPublic(0));
        assertEquals('\0', lexer.peekAtPublic(1));
        assertEquals('\0', lexer.peekAtPublic(99));
    }
}
