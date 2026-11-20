package com.reim.jnterpreter.parser;

import com.reim.jnterpreter.lexer.Token;

/**
 * Parses a token in prefix position: at the start of an expression, or
 * after an operator. Registered with
 * {@link Parser#prefix(String, PrefixParselet)}.
 *
 * @param <E> the language's expression (AST) type
 */
public interface PrefixParselet<E> {
    /**
     * Parses the expression starting at {@code tok}.
     *
     * @param p   the parser, for consuming further tokens
     * @param tok the prefix token
     * @return the parsed expression
     */
    E parse(Parser<E> p, Token tok);
}
