package com.reim.jnterpreter.parser;

import com.reim.jnterpreter.lexer.Token;

/**
 * Parses a token in infix position: after a left-hand expression, as an
 * operator or a call. Registered with
 * {@link Parser#infix(Object, InfixParselet)}.
 *
 * @param <E> the language's expression (AST) type
 * @param <K> the language's token kind type
 */
public interface InfixParselet<E, K> {
    /**
     * Parses the rest of the expression, given the left-hand side.
     *
     * @param p    the parser, for consuming further tokens
     * @param left the already-parsed left-hand side
     * @param tok  the infix token
     * @return the parsed expression
     */
    E parse(Parser<E, K> p, E left, Token<K> tok);

    /**
     * The precedence of this operator. Higher binds tighter; the parser
     * consumes infix operators with precedence at least the current
     * minimum.
     *
     * @return the precedence
     */
    int precedence();
}
