package io.github.reimdeveloper.jnterpreter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pratt parsing: the framework owns precedence climbing; the language
    supplies prefix/infix parselets. E is the language's AST type. */
public final class Parser<E> {
    private final List<Token> tokens;
    private int pos = 0;
    private final Map<String, PrefixParselet<E>> prefix = new HashMap<>();
    private final Map<String, InfixParselet<E>> infix = new HashMap<>();

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    public void prefix(String kind, PrefixParselet<E> p) { prefix.put(kind, p); }
    public void infix(String kind, InfixParselet<E> p) { infix.put(kind, p); }

    public Token peek() { return tokens.get(Math.min(pos, tokens.size() - 1)); }
    public Token advance() {
        Token t = peek();
        if (pos < tokens.size() - 1) pos++;
        return t;
    }
    public boolean match(String kind) {
        if (peek().kind().equals(kind)) { advance(); return true; }
        return false;
    }
    public Token expect(String kind, String what) {
        if (peek().kind().equals(kind)) return advance();
        throw new LangError(
            "expected " + what + ", got '" + peek().lexeme() + "'", peek().span());
    }
    public boolean eof() { return peek().kind().equals("EOF"); }

    /** Precedence climbing. minPrecedence 0 parses a full expression.
        Infix parselets parse the right side at precedence+1 (left-assoc)
        or precedence (right-assoc). */
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

    public E parseExpression() { return parseExpression(0); }
}
