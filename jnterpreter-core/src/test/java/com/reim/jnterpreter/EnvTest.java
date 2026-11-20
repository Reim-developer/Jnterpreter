package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.runtime.Env;
import com.reim.jnterpreter.runtime.Value;
import org.junit.jupiter.api.Test;

// Lexical scopes: definition, lookup, shadowing, assignment.

class EnvTest {
    private static final Span SPAN = new Span("test", 0, 1, 1, 1);

    @Test
    void defineAndGet() {
        Env env = new Env();
        env.define("x", new Value.Num(1));
        assertEquals(new Value.Num(1), env.get("x", SPAN));
    }

    @Test
    void shadowing() {
        Env env = new Env();
        env.define("x", new Value.Num(1));
        Env child = env.child();
        child.define("x", new Value.Num(2));
        assertEquals(new Value.Num(2), child.get("x", SPAN));
        assertEquals(new Value.Num(1), env.get("x", SPAN));
    }

    @Test
    void childIsolation() {
        Env env = new Env();
        Env child = env.child();
        child.define("x", new Value.Num(1));
        assertThrows(LangError.class, () -> env.get("x", SPAN));
    }

    @Test
    void lookupWalksUp() {
        Env env = new Env();
        env.define("x", new Value.Num(1));
        Env grandchild = env.child().child();
        assertEquals(new Value.Num(1), grandchild.get("x", SPAN));
    }

    @Test
    void assignUpdatesDefiningScope() {
        Env env = new Env();
        env.define("x", new Value.Num(1));
        Env child = env.child();
        child.assign("x", new Value.Num(2), SPAN);
        assertEquals(new Value.Num(2), env.get("x", SPAN));
        assertEquals(new Value.Num(2), child.get("x", SPAN));
    }

    @Test
    void assignToUndefinedThrows() {
        Env env = new Env();
        LangError error = assertThrows(LangError.class,
            () -> env.assign("y", new Value.Num(1), SPAN));
        assertTrue(error.diag.message().contains("undefined variable 'y'"));
        assertEquals(1, error.diag.span().line());
        assertEquals(1, error.diag.span().col());
    }

    @Test
    void getUndefinedThrows() {
        Env env = new Env();
        LangError error = assertThrows(LangError.class, () -> env.get("y", SPAN));
        assertTrue(error.diag.message().contains("undefined variable 'y'"));
        assertEquals(1, error.diag.span().line());
        assertEquals(1, error.diag.span().col());
    }
}
