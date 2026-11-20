package com.reim.jnterpreter.lexer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum constant as a token kind matched by a fixed string:
 * {@link RegexLexer} matches it by prefix comparison, without the
 * regex engine. The kind is the annotated constant itself.
 *
 * <p>At each position the lexer tries every rule and the longest
 * match wins; ties are broken by declaration order, so an earlier
 * constant wins over a later one matching the same text. An empty
 * literal is rejected at construction - it would match nothing and
 * make no progress. A constant must carry exactly one of
 * {@link Pattern}, {@code Literal}, or {@link Skip}, and the EOF
 * kind must carry none.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Literal {
    /**
     * The fixed string for this kind, matched at the current
     * position.
     *
     * @return the literal
     */
    String value();
}
