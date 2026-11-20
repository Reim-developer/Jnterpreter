package com.reim.jnterpreter.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A callable value implemented by host code: a name, a fixed arity,
 * and a function that receives the argument values and returns the
 * result. Use it to expose host operations (such as {@code print}) to
 * the interpreted language.
 */
public final class NativeFn implements Value.Fn {
    private final String name;
    private final int arity;
    private final Function<List<Value>, Value> body;

    /**
     * Creates a native function.
     *
     * @param name  the function's name, as shown by {@link #toString}
     * @param arity the number of arguments a call must supply
     * @param body  the host function; receives the argument values and
     *              returns the result
     * @throws NullPointerException if {@code name} or {@code body} is null
     */
    public NativeFn(String name, int arity, Function<List<Value>, Value> body) {
        this.name = Objects.requireNonNull(name, "name");
        this.arity = arity;
        this.body = Objects.requireNonNull(body, "body");
    }

    @Override public String name() { return name; }

    @Override public int arity() { return arity; }

    @Override public Value call(List<Value> args) { return body.apply(args); }

    @Override public String toString() { return "<native fn " + name + ">"; }
}
