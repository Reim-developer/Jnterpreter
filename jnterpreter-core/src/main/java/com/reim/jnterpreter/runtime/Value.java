package com.reim.jnterpreter.runtime;

import java.util.List;
import java.util.function.Function;

/** The value domain. Sealed, so the evaluator's dispatch is exhaustive
    and the compiler catches a missing case. */
public sealed interface Value
    permits Value.Num, Value.Str, Value.Bool, Value.Nil, Value.Fn, Value.NativeFn {
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

    /** A closure: params + a body the evaluator builds as a lambda that
        closes over the DEFINING Env. The framework never sees the AST --
        lexical capture falls out of Java's own closures. */
    record Fn(String name, List<String> params,
              Function<List<Value>, Value> body) implements Value {
        @Override public String toString() { return "<fn " + name + ">"; }
    }

    record NativeFn(String name, int arity,
                    Function<List<Value>, Value> body) implements Value {
        @Override public String toString() { return "<native fn " + name + ">"; }
    }
}
