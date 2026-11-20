package com.reim.jnterpreter.parser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the prefix parselet for a token kind: the method
 * parses an expression starting at a token of the named kind.
 * Discovered by
 * {@link Parser#from(Class, java.util.function.Function, java.util.List, Object)}.
 *
 * <p>The kind is named as a string, not as a value of the language's
 * kind type: a Java annotation cannot hold a generic value, so the
 * name is resolved by the caller-supplied function passed to
 * {@code Parser.from} - {@code Kind::valueOf} for an enum kind type,
 * the identity function for string kinds.
 *
 * <p>The method must be a public instance method taking
 * {@code (Parser<E, K>, Token<K>)} and returning the expression
 * type {@code E}. A method annotated with both {@code Prefix} and
 * {@link Infix} is rejected at registration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Prefix {
    /**
     * The token kind name this method parses at expression start.
     *
     * @return the kind name, resolved by the caller-supplied function
     */
    String value();
}
