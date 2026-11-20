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

    /** A tiny test AST: numbers, variables, binary and unary nodes. */
    private sealed interface Expr permits Expr.Num, Expr.Var, Expr.Bin, Expr.Unary {
        record Num(double v) implements Expr {}
        record Var(String name) implements Expr {}
        record Bin(String op, Expr l, Expr r) implements Expr {}
        record Unary(String op, Expr e) implements Expr {}
    }

    private static Token tok(String kind, String lexeme) {
        return new Token(kind, lexeme, SPAN);
    }

    private static Parser<Expr> parser(List<Token> tokens) {
        Parser<Expr> p = new Parser<>(tokens);
        p.prefix("NUMBER", (pp, t) -> new Expr.Num(Double.parseDouble(t.lexeme())));
        p.prefix("IDENT", (pp, t) -> new Expr.Var(t.lexeme()));
        p.prefix("(", (pp, t) -> {
            Expr e = pp.parseExpression();
            pp.expect(")", "')'");
            return e;
        });
        p.prefix("-", (pp, t) -> new Expr.Unary("-", pp.parseExpression(3)));
        binary(p, "+", 1);
        binary(p, "-", 1);
        binary(p, "*", 2);
        binary(p, "/", 2);
        return p;
    }

    private static void binary(Parser<Expr> p, String op, int prec) {
        p.infix(op, new InfixParselet<Expr>() {
            @Override public int precedence() { return prec; }
            @Override public Expr parse(Parser<Expr> pp, Expr left, Token t) {
                return new Expr.Bin(op, left, pp.parseExpression(prec + 1));
            }
        });
    }

    @Test
    void precedenceShapesTree() {
        Parser<Expr> p = parser(List.of(
            tok("NUMBER", "1"), tok("+", "+"), tok("NUMBER", "2"),
            tok("*", "*"), tok("NUMBER", "3"), tok("EOF", "")));
        assertEquals(new Expr.Bin("+", new Expr.Num(1),
            new Expr.Bin("*", new Expr.Num(2), new Expr.Num(3))),
            p.parseExpression());
    }

    @Test
    void leftAssociativity() {
        Parser<Expr> p = parser(List.of(
            tok("NUMBER", "10"), tok("-", "-"), tok("NUMBER", "4"),
            tok("-", "-"), tok("NUMBER", "3"), tok("EOF", "")));
        assertEquals(new Expr.Bin("-", new Expr.Bin("-", new Expr.Num(10), new Expr.Num(4)),
            new Expr.Num(3)), p.parseExpression());
    }

    @Test
    void parenthesesGroup() {
        Parser<Expr> p = parser(List.of(
            tok("(", "("), tok("NUMBER", "1"), tok("+", "+"), tok("NUMBER", "2"),
            tok(")", ")"), tok("*", "*"), tok("NUMBER", "3"), tok("EOF", "")));
        assertEquals(new Expr.Bin("*",
            new Expr.Bin("+", new Expr.Num(1), new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void unaryBindsTighter() {
        Parser<Expr> p = parser(List.of(
            tok("-", "-"), tok("NUMBER", "2"), tok("*", "*"),
            tok("NUMBER", "3"), tok("EOF", "")));
        assertEquals(new Expr.Bin("*", new Expr.Unary("-", new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void missingPrefixParseletThrows() {
        Parser<Expr> p = parser(List.of(tok(")", ")"), tok("EOF", "")));
        LangError error = assertThrows(LangError.class, p::parseExpression);
        assertTrue(error.diag.message().contains("unexpected token"));
        assertEquals(SPAN, error.diag.span());
    }

    @Test
    void expectMismatchThrows() {
        Parser<Expr> p = parser(List.of(tok("+", "+"), tok("EOF", "")));
        LangError error = assertThrows(LangError.class, () -> p.expect(")", "')'"));
        assertEquals("expected ')', got '+'", error.diag.message());
        assertEquals(SPAN, error.diag.span());
    }

    @Test
    void eofHandling() {
        Parser<Expr> p = parser(List.of(tok("EOF", "")));
        assertEquals("EOF", p.peek().kind());
        assertEquals("EOF", p.peek().kind());
        assertEquals("EOF", p.advance().kind());
        assertEquals("EOF", p.peek().kind());
        assertTrue(p.eof());
        assertThrows(LangError.class, p::parseExpression);
    }

    @Test
    void matchConsumesOnKind() {
        Parser<Expr> p = parser(List.of(tok("+", "+"), tok("NUMBER", "1"), tok("EOF", "")));
        assertTrue(p.match("+"));
        assertEquals("NUMBER", p.peek().kind());
        assertFalse(p.match("+"));
        assertEquals("NUMBER", p.peek().kind());
    }
}
