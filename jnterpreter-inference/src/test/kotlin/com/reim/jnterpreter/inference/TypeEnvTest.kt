package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Lexical scopes of type schemes. */
class TypeEnvTest {
    private val span = Span("test", 0, 1, 1, 1)

    @Test
    fun defineAndLookup() {
        val env = TypeEnv()
        env.define("x", Scheme.mono(Type.NUM))
        assertEquals(Scheme.mono(Type.NUM), env.lookup("x", span))
    }

    @Test
    fun shadowing() {
        val env = TypeEnv()
        env.define("x", Scheme.mono(Type.NUM))
        val child = env.child()
        child.define("x", Scheme.mono(Type.STR))
        assertEquals(Scheme.mono(Type.STR), child.lookup("x", span))
        assertEquals(Scheme.mono(Type.NUM), env.lookup("x", span))
    }

    @Test
    fun childIsolation() {
        val env = TypeEnv()
        val child = env.child()
        child.define("x", Scheme.mono(Type.NUM))
        assertThrows(LangError::class.java) { env.lookup("x", span) }
    }

    @Test
    fun lookupWalksUp() {
        val env = TypeEnv()
        env.define("x", Scheme.mono(Type.NUM))
        val grandchild = env.child().child()
        assertEquals(Scheme.mono(Type.NUM), grandchild.lookup("x", span))
    }

    @Test
    fun lookupUndefinedThrowsWithMessageAndSpan() {
        val env = TypeEnv()
        val error = assertThrows(LangError::class.java) { env.lookup("y", span) }
        assertEquals("undefined variable 'y'", error.diag.message())
        assertEquals(span, error.diag.span())
    }

    @Test
    fun parentConstructorNests() {
        val parent = TypeEnv()
        parent.define("x", Scheme.mono(Type.NUM))
        val child = TypeEnv(parent)
        assertEquals(Scheme.mono(Type.NUM), child.lookup("x", span))
    }
}
