package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.lexer.Token;
import com.reim.jnterpreter.parser.InfixParselet;
import com.reim.jnterpreter.parser.Parser;
import java.util.List;
import org.junit.jupiter.api.Test;

// Pratt parsing: the tests assert TREE SHAPE, not evaluated values.

class ParserTest {
    private static final Span SPAN = new Span("test", 0, 1, 1, 1);

    /** The test language's token kinds. */
    enum Kind { NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, LPAREN, RPAREN, EOF }

    /** A tiny test AST: numbers, variables, binary and unary nodes. */
    private sealed interface Expr permits Expr.Num, Expr.Var, Expr.Bin, Expr.Unary {
        record Num(double v) implements Expr {}
        record Var(String name) implements Expr {}
        record Bin(String op, Expr l, Expr r) implements Expr {}
        record Unary(String op, Expr e) implements Expr {}
    }

    private static Token<Kind> tok(Kind kind, String lexeme) {
        return new Token<>(kind, lexeme, SPAN);
    }

    private static Parser<Expr, Kind> parser(List<Token<Kind>> tokens) {
        Parser<Expr, Kind> p = new Parser<>(tokens, Kind.EOF);
        p.prefix(Kind.NUMBER, (pp, t) -> new Expr.Num(Double.parseDouble(t.lexeme())));
        p.prefix(Kind.IDENT, (pp, t) -> new Expr.Var(t.lexeme()));
        p.prefix(Kind.LPAREN, (pp, t) -> {
            Expr e = pp.parseExpression();
            pp.expect(Kind.RPAREN, "')'");
            return e;
        });
        p.prefix(Kind.MINUS, (pp, t) -> new Expr.Unary("-", pp.parseExpression(3)));
        binary(p, Kind.PLUS, "+", 1);
        binary(p, Kind.MINUS, "-", 1);
        binary(p, Kind.STAR, "*", 2);
        binary(p, Kind.SLASH, "/", 2);
        return p;
    }

    private static void binary(Parser<Expr, Kind> p, Kind kind, String op, int prec) {
        p.infix(kind, new InfixParselet<Expr, Kind>() {
            @Override public int precedence() { return prec; }
            @Override public Expr parse(Parser<Expr, Kind> pp, Expr left, Token<Kind> t) {
                return new Expr.Bin(op, left, pp.parseExpression(prec + 1));
            }
        });
    }

    @Test
    void precedenceShapesTree() {
        Parser<Expr, Kind> p = parser(List.of(
            tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"), tok(Kind.NUMBER, "2"),
            tok(Kind.STAR, "*"), tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("+", new Expr.Num(1),
            new Expr.Bin("*", new Expr.Num(2), new Expr.Num(3))),
            p.parseExpression());
    }

    @Test
    void leftAssociativity() {
        Parser<Expr, Kind> p = parser(List.of(
            tok(Kind.NUMBER, "10"), tok(Kind.MINUS, "-"), tok(Kind.NUMBER, "4"),
            tok(Kind.MINUS, "-"), tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("-", new Expr.Bin("-", new Expr.Num(10), new Expr.Num(4)),
            new Expr.Num(3)), p.parseExpression());
    }

    @Test
    void parenthesesGroup() {
        Parser<Expr, Kind> p = parser(List.of(
            tok(Kind.LPAREN, "("), tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"), tok(Kind.NUMBER, "2"),
            tok(Kind.RPAREN, ")"), tok(Kind.STAR, "*"), tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("*",
            new Expr.Bin("+", new Expr.Num(1), new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void unaryBindsTighter() {
        Parser<Expr, Kind> p = parser(List.of(
            tok(Kind.MINUS, "-"), tok(Kind.NUMBER, "2"), tok(Kind.STAR, "*"),
            tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("*", new Expr.Unary("-", new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void missingPrefixParseletThrows() {
        Parser<Expr, Kind> p = parser(List.of(tok(Kind.RPAREN, ")"), tok(Kind.EOF, "")));
        LangError error = assertThrows(LangError.class, p::parseExpression);
        assertTrue(error.diag.message().contains("unexpected token"));
        assertEquals(SPAN, error.diag.span());
    }

    @Test
    void expectMismatchThrows() {
        Parser<Expr, Kind> p = parser(List.of(tok(Kind.PLUS, "+"), tok(Kind.EOF, "")));
        LangError error = assertThrows(LangError.class, () -> p.expect(Kind.RPAREN, "')'"));
        assertEquals("expected ')', got '+'", error.diag.message());
        assertEquals(SPAN, error.diag.span());
    }

    @Test
    void eofHandling() {
        Parser<Expr, Kind> p = parser(List.of(tok(Kind.EOF, "")));
        assertEquals(Kind.EOF, p.peek().kind());
        assertEquals(Kind.EOF, p.peek().kind());
        assertEquals(Kind.EOF, p.advance().kind());
        assertEquals(Kind.EOF, p.peek().kind());
        assertTrue(p.eof());
        assertThrows(LangError.class, p::parseExpression);
    }

    @Test
    void matchConsumesOnKind() {
        Parser<Expr, Kind> p = parser(List.of(tok(Kind.PLUS, "+"), tok(Kind.NUMBER, "1"), tok(Kind.EOF, "")));
        assertTrue(p.match(Kind.PLUS));
        assertEquals(Kind.NUMBER, p.peek().kind());
        assertFalse(p.match(Kind.PLUS));
        assertEquals(Kind.NUMBER, p.peek().kind());
    }
}
