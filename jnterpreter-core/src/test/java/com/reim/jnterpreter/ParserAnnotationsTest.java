package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.lexer.Token;
import com.reim.jnterpreter.parser.Infix;
import com.reim.jnterpreter.parser.InfixParselet;
import com.reim.jnterpreter.parser.Parser;
import com.reim.jnterpreter.parser.Prefix;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

// Parselet annotations: an annotated grammar parses like the same
// grammar registered explicitly, and every malformed parselets class
// fails loud at registration. The tests assert TREE SHAPE, not
// evaluated values.

class ParserAnnotationsTest {
    private static final Span SPAN = new Span("test", 0, 1, 1, 1);

    /** The test language's token kinds. */
    enum Kind { NUMBER, IDENT, PLUS, MINUS, STAR, LPAREN, RPAREN, EOF }

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

    /** The arithmetic grammar, declared with annotations. */
    public static class ArithmeticParselets {
        @Prefix("NUMBER")
        public Expr number(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(Double.parseDouble(t.lexeme()));
        }

        @Prefix("IDENT")
        public Expr ident(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Var(t.lexeme());
        }

        @Prefix("LPAREN")
        public Expr paren(Parser<Expr, Kind> p, Token<Kind> t) {
            Expr e = p.parseExpression();
            p.expect(Kind.RPAREN, "')'");
            return e;
        }

        @Prefix("MINUS")
        public Expr negate(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Unary("-", p.parseExpression(3));
        }

        @Infix(value = "PLUS", precedence = 1)
        public Expr plus(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
            return new Expr.Bin("+", left, p.parseExpression(2));
        }

        @Infix(value = "MINUS", precedence = 1)
        public Expr minus(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
            return new Expr.Bin("-", left, p.parseExpression(2));
        }

        @Infix(value = "STAR", precedence = 2)
        public Expr star(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
            return new Expr.Bin("*", left, p.parseExpression(3));
        }
    }

    /** Numbers and plus only: the rest is registered by hand. */
    public static class MinimalParselets {
        @Prefix("NUMBER")
        public Expr number(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(Double.parseDouble(t.lexeme()));
        }

        @Infix(value = "PLUS", precedence = 1)
        public Expr plus(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
            return new Expr.Bin("+", left, p.parseExpression(2));
        }
    }

    /** A valid parselets class with no annotated methods. */
    public static class EmptyParselets {
        public Expr helper() {
            return new Expr.Num(0);
        }
    }

    private static Parser<Expr, Kind> annotated(List<Token<Kind>> tokens) {
        return Parser.from(ArithmeticParselets.class, Kind::valueOf, tokens, Kind.EOF);
    }

    private static Parser<Expr, Kind> from(Class<?> parseletsClass) {
        return Parser.from(parseletsClass, Kind::valueOf,
            List.of(tok(Kind.EOF, "")), Kind.EOF);
    }

    private static Parser<Expr, Kind> explicit(List<Token<Kind>> tokens) {
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
    void precedenceRespectedThroughAnnotations() {
        Parser<Expr, Kind> p = annotated(List.of(
            tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"), tok(Kind.NUMBER, "2"),
            tok(Kind.STAR, "*"), tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("+", new Expr.Num(1),
            new Expr.Bin("*", new Expr.Num(2), new Expr.Num(3))),
            p.parseExpression());
    }

    @Test
    void parenthesesGroupThroughAnnotations() {
        Parser<Expr, Kind> p = annotated(List.of(
            tok(Kind.LPAREN, "("), tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"), tok(Kind.NUMBER, "2"),
            tok(Kind.RPAREN, ")"), tok(Kind.STAR, "*"), tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("*",
            new Expr.Bin("+", new Expr.Num(1), new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void unaryMinusThroughAnnotations() {
        // MINUS is both a prefix parselet (negation) and an infix
        // parselet (subtraction): the same kind in both positions
        // is legal, only duplicates within one position fail.
        Parser<Expr, Kind> p = annotated(List.of(
            tok(Kind.MINUS, "-"), tok(Kind.NUMBER, "2"), tok(Kind.STAR, "*"),
            tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")));
        assertEquals(new Expr.Bin("*", new Expr.Unary("-", new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void annotatedMatchesExplicitRegistration() {
        List<List<Token<Kind>>> inputs = List.of(
            List.of(tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"), tok(Kind.NUMBER, "2"),
                tok(Kind.STAR, "*"), tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")),
            List.of(tok(Kind.LPAREN, "("), tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"),
                tok(Kind.NUMBER, "2"), tok(Kind.RPAREN, ")"), tok(Kind.EOF, "")),
            List.of(tok(Kind.MINUS, "-"), tok(Kind.IDENT, "x"), tok(Kind.EOF, "")));
        for (List<Token<Kind>> tokens : inputs) {
            assertEquals(explicit(tokens).parseExpression(),
                annotated(tokens).parseExpression());
        }
    }

    @Test
    void mixedRegistrationWorks() {
        Parser<Expr, Kind> p = Parser.from(MinimalParselets.class, Kind::valueOf,
            List.of(tok(Kind.LPAREN, "("), tok(Kind.NUMBER, "1"), tok(Kind.PLUS, "+"),
                tok(Kind.NUMBER, "2"), tok(Kind.RPAREN, ")"), tok(Kind.STAR, "*"),
                tok(Kind.NUMBER, "3"), tok(Kind.EOF, "")), Kind.EOF);
        p.prefix(Kind.LPAREN, (pp, t) -> {
            Expr e = pp.parseExpression();
            pp.expect(Kind.RPAREN, "')'");
            return e;
        });
        binary(p, Kind.STAR, "*", 2);
        assertEquals(new Expr.Bin("*",
            new Expr.Bin("+", new Expr.Num(1), new Expr.Num(2)), new Expr.Num(3)),
            p.parseExpression());
    }

    @Test
    void noAnnotationsYieldsEmptyParser() {
        Parser<Expr, Kind> p = from(EmptyParselets.class);
        LangError error = assertThrows(LangError.class, p::parseExpression);
        assertTrue(error.diag.message().contains("unexpected token"));
    }

    /** A prefix parselet for a kind name the resolver cannot resolve. */
    public static class UnknownKindParselets {
        @Prefix("NOPE")
        public Expr nope(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void unknownKindNameFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(UnknownKindParselets.class));
        assertTrue(error.getMessage().contains("NOPE"));
        assertTrue(error.getMessage().contains("nope"));
    }

    /** Two prefix parselets for the same kind. */
    public static class DuplicatePrefixParselets {
        @Prefix("NUMBER")
        public Expr first(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }

        @Prefix("NUMBER")
        public Expr second(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(1);
        }
    }

    @Test
    void duplicatePrefixKindFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(DuplicatePrefixParselets.class));
        assertTrue(error.getMessage().contains("duplicate"));
        assertTrue(error.getMessage().contains("NUMBER"));
    }

    /** Two infix parselets for the same kind. */
    public static class DuplicateInfixParselets {
        @Infix(value = "PLUS", precedence = 1)
        public Expr first(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
            return new Expr.Num(0);
        }

        @Infix(value = "PLUS", precedence = 2)
        public Expr second(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
            return new Expr.Num(1);
        }
    }

    @Test
    void duplicateInfixKindFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(DuplicateInfixParselets.class));
        assertTrue(error.getMessage().contains("duplicate"));
        assertTrue(error.getMessage().contains("PLUS"));
    }

    /** A prefix parselet with the wrong number of parameters. */
    public static class WrongArityParselets {
        @Prefix("NUMBER")
        public Expr onlyOne(Parser<Expr, Kind> p) {
            return new Expr.Num(0);
        }
    }

    @Test
    void wrongArityFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(WrongArityParselets.class));
        assertTrue(error.getMessage().contains("onlyOne"));
    }

    /** A prefix parselet whose first parameter is not a Parser. */
    public static class WrongFirstParamParselets {
        @Prefix("NUMBER")
        public Expr notAParser(String s, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void wrongFirstParamTypeFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(WrongFirstParamParselets.class));
        assertTrue(error.getMessage().contains("notAParser"));
    }

    /** A prefix parselet whose last parameter is not a Token. */
    public static class WrongLastParamParselets {
        @Prefix("NUMBER")
        public Expr notAToken(Parser<Expr, Kind> p, String s) {
            return new Expr.Num(0);
        }
    }

    @Test
    void wrongLastParamTypeFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(WrongLastParamParselets.class));
        assertTrue(error.getMessage().contains("notAToken"));
    }

    /** An infix parselet whose last parameter is not a Token. */
    public static class WrongInfixParamParselets {
        @Infix(value = "PLUS", precedence = 1)
        public Expr notAToken(Parser<Expr, Kind> p, Expr left, String s) {
            return new Expr.Num(0);
        }
    }

    @Test
    void wrongInfixParamTypeFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(WrongInfixParamParselets.class));
        assertTrue(error.getMessage().contains("notAToken"));
    }

    /** A prefix parselet returning void. */
    public static class VoidReturnParselets {
        @Prefix("NUMBER")
        public void nothing(Parser<Expr, Kind> p, Token<Kind> t) {
        }
    }

    @Test
    void voidReturnFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(VoidReturnParselets.class));
        assertTrue(error.getMessage().contains("nothing"));
    }

    /** A parselets class with no no-arg constructor at all. */
    public static class NoNoArgCtorParselets {
        public NoNoArgCtorParselets(String s) {
        }

        @Prefix("NUMBER")
        public Expr number(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void missingNoArgConstructorFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(NoNoArgCtorParselets.class));
        assertTrue(error.getMessage().contains("NoNoArgCtorParselets"));
    }

    /** A parselets class whose no-arg constructor is private. */
    public static class PrivateCtorParselets {
        private PrivateCtorParselets() {
        }

        @Prefix("NUMBER")
        public Expr number(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void privateConstructorFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(PrivateCtorParselets.class));
        assertTrue(error.getMessage().contains("PrivateCtorParselets"));
    }

    /** A private method carrying a parselet annotation. */
    public static class NonPublicMethodParselets {
        @Prefix("NUMBER")
        private Expr hidden(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void nonPublicAnnotatedMethodFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(NonPublicMethodParselets.class));
        assertTrue(error.getMessage().contains("hidden"));
    }

    /** A static method carrying a parselet annotation. */
    public static class StaticMethodParselets {
        @Prefix("NUMBER")
        public static Expr notAnInstance(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void staticAnnotatedMethodFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(StaticMethodParselets.class));
        assertTrue(error.getMessage().contains("notAnInstance"));
    }

    /** One method carrying both annotations. */
    public static class BothAnnotationsParselets {
        @Prefix("NUMBER")
        @Infix(value = "PLUS", precedence = 1)
        public Expr both(Parser<Expr, Kind> p, Token<Kind> t) {
            return new Expr.Num(0);
        }
    }

    @Test
    void bothAnnotationsFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> from(BothAnnotationsParselets.class));
        assertTrue(error.getMessage().contains("both"));
    }

    @Test
    void nullResolverResultFails() {
        Function<String, Kind> nulls = name -> null;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> Parser.from(ArithmeticParselets.class, nulls,
                List.of(tok(Kind.EOF, "")), Kind.EOF));
        assertTrue(error.getMessage().contains("null"));
    }
}
