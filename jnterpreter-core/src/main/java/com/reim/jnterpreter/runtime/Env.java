package com.reim.jnterpreter.runtime;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;

import java.util.HashMap;
import java.util.Map;

/** Lexical scopes: a chain of environments, each holding its own
 * variables and delegating to its parent for names it does not define.
 * {@link #child()} creates a nested scope; a closure captures the
 * environment where it was defined.
 */
public final class Env {
    private final Map<String, Value> vars = new HashMap<>();
    private final Env parent;

    /** Creates a top-level environment with no parent. */
    public Env() { this(null); }

    /**
     * Creates an environment nested in {@code parent}.
     *
     * @param parent the enclosing scope, or null for a top-level environment
     */
    public Env(Env parent) { this.parent = parent; }

    /** Creates a new environment nested in this one. */
    public Env child() { return new Env(this); }

    /**
     * Defines a variable in this environment, shadowing any definition
     * of the same name in an enclosing scope.
     *
     * @param name the variable name
     * @param v    its value
     */
    public void define(String name, Value v) { vars.put(name, v); }

    /**
     * Looks up a variable, walking up the chain of enclosing scopes.
     *
     * @param name the variable name
     * @param span the span of the use, for the error if undefined
     * @return the variable's value
     * @throws LangError if no scope in the chain defines the name
     */
    public Value get(String name, Span span) {
        if (vars.containsKey(name)) return vars.get(name);
        if (parent != null) return parent.get(name, span);
        throw new LangError("undefined variable '" + name + "'", span);
    }

    /**
     * Assigns to a variable defined in this or an enclosing scope.
     *
     * @param name the variable name
     * @param v    the new value
     * @param span the span of the assignment, for the error if undefined
     * @throws LangError if no scope in the chain defines the name
     */
    public void assign(String name, Value v, Span span) {
        if (vars.containsKey(name)) { vars.put(name, v); return; }
        if (parent != null) { parent.assign(name, v, span); return; }
        throw new LangError("undefined variable '" + name + "'", span);
    }
}
