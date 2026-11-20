package io.github.reimdeveloper.jnterpreter;

import java.util.HashMap;
import java.util.Map;

/** Lexical scopes: a chain of maps. `get`/`assign` throw span errors. */
public final class Env {
    private final Map<String, Value> vars = new HashMap<>();
    private final Env parent;

    public Env() { this(null); }
    public Env(Env parent) { this.parent = parent; }
    public Env child() { return new Env(this); }
    public void define(String name, Value v) { vars.put(name, v); }

    public Value get(String name, Span span) {
        if (vars.containsKey(name)) return vars.get(name);
        if (parent != null) return parent.get(name, span);
        throw new LangError("undefined variable '" + name + "'", span);
    }

    public void assign(String name, Value v, Span span) {
        if (vars.containsKey(name)) { vars.put(name, v); return; }
        if (parent != null) { parent.assign(name, v, span); return; }
        throw new LangError("undefined variable '" + name + "'", span);
    }
}
