package com.reim.jnterpreter.lexer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum constant as a token kind matched by a regular
 * expression: {@link RegexLexer} tries the pattern at the current
 * position, and the pattern must match a prefix of the remaining
 * input (lookingAt semantics, not a whole-input match). The kind is
 * the annotated constant itself.
 *
 * <p>At each position the lexer tries every rule and the longest
 * match wins; ties are broken by declaration order, so an earlier
 * constant wins over a later one matching the same text. A pattern
 * that can match the empty string is rejected at construction - an
 * empty match would make no progress. A constant must carry exactly
 * one of {@code Pattern}, {@link Literal}, or {@link Skip}, and the
 * EOF kind must carry none.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Pattern {
    /**
     * The regular expression for this kind, matched at the current
     * position.
     *
     * @return the pattern
     */
    String value();
}
