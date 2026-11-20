package com.reim.jnterpreter.parser;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.lexer.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A Pratt parser for expressions: it owns precedence climbing, and
 * the language supplies a prefix parselet for each token kind that can
 * start an expression and an infix parselet for each kind that can
 * continue one.
 *
 * @param <E> the language's expression (AST) type
 */
public final class Parser<E> {
    private final List<Token> tokens;
    private int pos = 0;
    private final Map<String, PrefixParselet<E>> prefix = new HashMap<>();
    private final Map<String, InfixParselet<E>> infix = new HashMap<>();

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    /**
     * Registers the parselet that parses {@code kind} at the start of
     * an expression.
     *
     * @param kind the token kind
     * @param p    the parselet
     */
    public void prefix(String kind, PrefixParselet<E> p) { prefix.put(kind, p); }

    /**
     * Registers the parselet that parses {@code kind} after a left-hand
     * expression.
     *
     * @param kind the token kind
     * @param p    the parselet
     */
    public void infix(String kind, InfixParselet<E> p) { infix.put(kind, p); }

    /** The next token without consuming it; the EOF token at end of input. */
    public Token peek() { return tokens.get(Math.min(pos, tokens.size() - 1)); }

    /** Consumes and returns the next token. */
    public Token advance() {
        Token t = peek();
        if (pos < tokens.size() - 1) pos++;
        return t;
    }

    /**
     * Consumes the next token if it has the given kind.
     *
     * @param kind the expected kind
     * @return whether the token matched and was consumed
     */
    public boolean match(String kind) {
        if (peek().kind().equals(kind)) { advance(); return true; }
        return false;
    }

    /**
     * Consumes the next token, which must have the given kind.
     *
     * @param kind the expected kind
     * @param what a description of the expected token, for the error
     * @return the consumed token
     * @throws LangError if the next token has another kind
     */
    public Token expect(String kind, String what) {
        if (peek().kind().equals(kind)) return advance();
        throw new LangError(
            "expected " + what + ", got '" + peek().lexeme() + "'", peek().span());
    }
    /** Whether the next token is the EOF token. */
    public boolean eof() { return peek().kind().equals("EOF"); }

    /**
     * Parses an expression, consuming operators with precedence at least
     * {@code minPrecedence}. A call with 0 parses a full expression.
     * Infix parselets parse their right side at precedence + 1 for
     * left-associative operators, or at the same precedence for
     * right-associative ones.
     *
     * @param minPrecedence the lowest operator precedence to consume
     * @return the parsed expression
     * @throws LangError if the next token cannot start an expression
     */
    public E parseExpression(int minPrecedence) {
        Token tok = advance();
        PrefixParselet<E> pre = prefix.get(tok.kind());
        if (pre == null) {
            throw new LangError(
                "unexpected token '" + tok.lexeme() + "'", tok.span());
        }
        E left = pre.parse(this, tok);
        while (true) {
            InfixParselet<E> inf = infix.get(peek().kind());
            if (inf == null || inf.precedence() < minPrecedence) break;
            Token op = advance();
            left = inf.parse(this, left, op);
        }
        return left;
    }

    /** Parses a full expression (precedence 0). */
    public E parseExpression() { return parseExpression(0); }
}
