package com.reim.jnterpreter.lexer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum constant as text to skip: {@link RegexLexer} matches
 * the pattern at the current position like a {@link Pattern}, but
 * the matched text is consumed and produces no token. The kind is
 * the annotated constant itself, and it never appears in the token
 * list. Use it for whitespace and comments.
 *
 * <p>At each position the lexer tries every rule and the longest
 * match wins; ties are broken by declaration order, so an earlier
 * constant wins over a later one matching the same text. A pattern
 * that can match the empty string is rejected at construction - an
 * empty match would make no progress. A constant must carry exactly
 * one of {@link Pattern}, {@link Literal}, or {@code Skip}, and the
 * EOF kind must carry none.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Skip {
    /**
     * The regular expression for the text to skip, matched at the
     * current position.
     *
     * @return the pattern
     */
    String value();
}
