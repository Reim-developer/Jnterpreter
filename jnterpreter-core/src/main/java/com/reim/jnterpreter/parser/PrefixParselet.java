package com.reim.jnterpreter.parser;

import com.reim.jnterpreter.lexer.Token;

/**
 * Parses a token in prefix position: at the start of an expression, or
 * after an operator. Registered with
 * {@link Parser#prefix(Object, PrefixParselet)}.
 *
 * @param <E> the language's expression (AST) type
 * @param <K> the language's token kind type
 */
public interface PrefixParselet<E, K> {
    /**
     * Parses the expression starting at {@code tok}.
     *
     * @param p   the parser, for consuming further tokens
     * @param tok the prefix token
     * @return the parsed expression
     */
    E parse(Parser<E, K> p, Token<K> tok);
}
