package io.github.reimdeveloper.jnterpreter;

/** A token. Kind is a plain string the language defines ("NUMBER", "+").
    v1 trades enum safety for zero generics noise; a typed-kind variant
    is a named v2 item. */
public record Token(String kind, String lexeme, Span span) {}
