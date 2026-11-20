package com.reim.jnterpreter.parser;
import com.reim.jnterpreter.lexer.Token;

/** Pratt parsing: the framework owns precedence climbing; the language
    supplies prefix/infix parselets. E is the language's AST type. */
public interface InfixParselet<E> {
    E parse(Parser<E> p, E left, Token tok);
    int precedence();
}
