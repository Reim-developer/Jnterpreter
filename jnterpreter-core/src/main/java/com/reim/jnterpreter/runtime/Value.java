package com.reim.jnterpreter.runtime;

import java.util.List;

/** The value domain. Sealed, so the evaluator's dispatch is exhaustive
    and the compiler catches a missing case. */
public sealed interface Value
    permits Value.Num, Value.Str, Value.Bool, Value.Nil, Value.Fn {
    record Num(double v) implements Value {
        @Override public String toString() {
            if (v == Math.rint(v) && Math.abs(v) < 1e15) {
                return Long.toString((long) v);
            }
            return Double.toString(v);
        }
    }

    record Str(String v) implements Value {
        @Override public String toString() { return v; }
    }

    record Bool(boolean v) implements Value {
        @Override public String toString() { return Boolean.toString(v); }
    }

    record Nil() implements Value {
        @Override public String toString() { return "nil"; }
    }

    /** A callable value. Implemented by closures defined in the
        interpreted language and by native functions provided by the
        host (see {@link NativeFn}). Non-sealed: any class may implement
        it, so the language defines its own closure type. */
    non-sealed interface Fn extends Value {
        String name();
        int arity();
        Value call(List<Value> args);
    }
}
