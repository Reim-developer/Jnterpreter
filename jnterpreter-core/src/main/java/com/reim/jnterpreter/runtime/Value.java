package com.reim.jnterpreter.runtime;

import java.util.List;

/** The value domain of an interpreted language: numbers, strings,
 * booleans, nil, and callables. Sealed, so an evaluator that dispatches
 * on the cases is exhaustive and the compiler catches a missing case.
 */
public sealed interface Value
    permits Value.Num, Value.Str, Value.Bool, Value.Nil, Value.Fn {
    /** A number. Printed without a decimal point when integral. */
    record Num(double v) implements Value {
        @Override public String toString() {
            if (v == Math.rint(v) && Math.abs(v) < 1e15) {
                return Long.toString((long) v);
            }
            return Double.toString(v);
        }
    }

    /** A string. Prints as its contents. */
    record Str(String v) implements Value {
        @Override public String toString() { return v; }
    }

    /** A boolean. */
    record Bool(boolean v) implements Value {
        @Override public String toString() { return Boolean.toString(v); }
    }

    /** The absence of a value. Prints as {@code nil}. */
    record Nil() implements Value {
        @Override public String toString() { return "nil"; }
    }

    /**
     * A callable value: a function defined in the interpreted language
     * or provided natively by the host (see {@link NativeFn}).
     * Non-sealed, so the language defines its own closure type by
     * implementing this interface.
     */
    non-sealed interface Fn extends Value {
        /** The function's name, as shown by {@link #toString}. */
        String name();

        /** The number of arguments a call must supply. */
        int arity();

        /**
         * Calls this function.
         *
         * @param args the argument values; the caller checks the count
         *             against {@link #arity()} before calling
         * @return the result of the call
         */
        Value call(List<Value> args);
    }
}
