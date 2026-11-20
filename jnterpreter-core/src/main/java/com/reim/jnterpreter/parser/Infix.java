package com.reim.jnterpreter.parser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the infix parselet for a token kind: the method
 * parses the rest of an expression after a left-hand side, given a
 * token of the named kind. Discovered by
 * {@link Parser#from(Class, java.util.function.Function, java.util.List, Object)}.
 *
 * <p>The kind is named as a string, not as a value of the language's
 * kind type: a Java annotation cannot hold a generic value, so the
 * name is resolved by the caller-supplied function passed to
 * {@code Parser.from} - {@code Kind::valueOf} for an enum kind type,
 * the identity function for string kinds.
 *
 * <p>The method must be a public instance method taking
 * {@code (Parser<E, K>, E, Token<K>)} and returning the expression
 * type {@code E}. A method annotated with both {@code Infix} and
 * {@link Prefix} is rejected at registration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Infix {
    /**
     * The token kind name this method parses after a left-hand side.
     *
     * @return the kind name, resolved by the caller-supplied function
     */
    String value();

    /**
     * The precedence of this operator. Higher binds tighter; the
     * parser consumes infix operators with precedence at least the
     * current minimum. There is no default: the precedence must be
     * explicit.
     *
     * @return the precedence
     */
    int precedence();
}
