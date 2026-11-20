package com.reim.jnterpreter.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Type schemes: monomorphic and quantified. */
class SchemeTest {

    @Test
    fun monoHasNoQuantifiedVariables() {
        val scheme = Scheme.mono(Type.NUM)
        assertEquals(Type.NUM, scheme.type)
        assertTrue(scheme.quantified.isEmpty())
    }

    @Test
    fun quantifiedSetIsCopiedOnConstruction() {
        val checker = Checker()
        val v = checker.freshVar()
        val vars = mutableSetOf(v)
        val scheme = Scheme(Type.`fun`(v, v), vars)
        vars.clear()
        assertEquals(setOf(v), scheme.quantified)
    }

    @Test
    fun handBuiltSchemeKeepsTypeAndQuantified() {
        val checker = Checker()
        val a = checker.freshVar()
        // print : forall a. a -> nil
        val scheme = Scheme(Type.`fun`(a, Type.NIL), setOf(a))
        assertEquals("a -> nil", scheme.type.toString())
        assertEquals(setOf(a), scheme.quantified)
    }
}
