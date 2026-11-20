package io.github.reimdeveloper.jnterpreter;

/** Pratt parsing: the framework owns precedence climbing; the language
    supplies prefix/infix parselets. E is the language's AST type. */
public interface PrefixParselet<E> { E parse(Parser<E> p, Token tok); }
