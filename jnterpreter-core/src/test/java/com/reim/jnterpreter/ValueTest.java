package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reim.jnterpreter.runtime.NativeFn;
import com.reim.jnterpreter.runtime.Value;
import java.util.List;
import org.junit.jupiter.api.Test;

// The value domain: printing, native functions, the Fn contract.

class ValueTest {

    @Test
    void numIntegralPrintsWithoutDecimal() {
        assertEquals("5", new Value.Num(5).toString());
        assertEquals("-3", new Value.Num(-3).toString());
    }

    @Test
    void numFractionalPrintsDecimal() {
        assertEquals("2.5", new Value.Num(2.5).toString());
    }

    @Test
    void numBoundaryAt1e15() {
        assertEquals("999999999999999", new Value.Num(1e15 - 1).toString());
        assertEquals("1.0E15", new Value.Num(1e15).toString());
    }

    @Test
    void strPrintsContents() {
        assertEquals("hi", new Value.Str("hi").toString());
    }

    @Test
    void boolPrints() {
        assertEquals("true", new Value.Bool(true).toString());
        assertEquals("false", new Value.Bool(false).toString());
    }

    @Test
    void nilPrintsNil() {
        assertEquals("nil", new Value.Nil().toString());
    }

    @Test
    void nativeFnToString() {
        NativeFn fn = new NativeFn("print", 1, args -> new Value.Nil());
        assertEquals("<native fn print>", fn.toString());
        assertEquals("print", fn.name());
        assertEquals(1, fn.arity());
    }

    @Test
    void nativeFnCallDispatches() {
        NativeFn fn = new NativeFn("id", 1, args -> args.get(0));
        assertEquals(new Value.Num(7),
            fn.call(List.of(new Value.Num(7))));
    }

    @Test
    void nativeFnNullChecks() {
        assertThrows(NullPointerException.class,
            () -> new NativeFn(null, 1, args -> new Value.Nil()));
        assertThrows(NullPointerException.class,
            () -> new NativeFn("f", 1, null));
    }

    /** A minimal Fn implementation, mirroring a language's closure type. */
    static final class TestFn implements Value.Fn {
        @Override public String name() { return "f"; }
        @Override public int arity() { return 0; }
        @Override public Value call(List<Value> args) { return new Value.Nil(); }
        @Override public String toString() { return "<fn " + name() + ">"; }
    }

    @Test
    void fnToString() {
        Value.Fn fn = new TestFn();
        assertEquals("<fn f>", fn.toString());
        assertEquals("f", fn.name());
        assertEquals(0, fn.arity());
        assertTrue(fn.call(List.of()) instanceof Value.Nil);
    }
}
