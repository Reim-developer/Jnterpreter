package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unification: binding, recursion, and errors. */
class CheckerTest {
    private val span = Span("test", 0, 1, 1, 1)

    @Test
    fun unifyingAVariableWithItselfSucceeds() {
        val checker = Checker()
        val v = checker.freshVar()
        checker.unify(v, v, span, "self")
        assertEquals("a", v.toString())
    }

    @Test
    fun boundVariablePrintsAsItsBinding() {
        val checker = Checker()
        val v = checker.freshVar()
        checker.unify(v, Type.NUM, span, "bind")
        assertEquals("num", v.toString())
    }

    @Test
    fun unifyBindsOnEitherSide() {
        val checker = Checker()
        val v = checker.freshVar()
        checker.unify(Type.STR, v, span, "bind")
        assertEquals("str", v.toString())
    }

    @Test
    fun unifyingTwoVariablesLinksThem() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        checker.unify(a, b, span, "link")
        checker.unify(a, Type.NUM, span, "bind")
        assertEquals("num", b.toString())
    }

    @Test
    fun functionUnificationRecurses() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        val c = checker.freshVar()
        val d = checker.freshVar()
        checker.unify(Type.`fun`(a, b), Type.`fun`(c, d), span, "fun")
        checker.unify(a, Type.NUM, span, "bind")
        assertEquals("num", c.toString())
        assertEquals("a", b.toString())
        assertEquals("a", d.toString())
    }

    @Test
    fun constructorUnificationRecursesOverArguments() {
        val checker = Checker()
        val a = checker.freshVar()
        checker.unify(
            Type.Con("list", listOf(a)),
            Type.Con("list", listOf(Type.NUM)),
            span, "list",
        )
        assertEquals("num", a.toString())
    }

    @Test
    fun mismatchedBaseTypesThrowWithSpanAndNote() {
        val checker = Checker()
        val error = assertThrows(LangError::class.java) {
            checker.unify(Type.NUM, Type.STR, span, "the operands must match")
        }
        assertEquals("cannot unify num with str", error.diag.message())
        assertEquals(span, error.diag.span())
        assertEquals("the operands must match", error.diag.note())
    }

    @Test
    fun mismatchedConstructorNamesThrow() {
        val checker = Checker()
        val error = assertThrows(LangError::class.java) {
            checker.unify(
                Type.Con("list", listOf(Type.NUM)),
                Type.Con("pair", listOf(Type.NUM)),
                span, "ctor",
            )
        }
        assertTrue(error.diag.message().contains("cannot unify list<num> with pair<num>"))
    }

    @Test
    fun mismatchedConstructorAritiesThrow() {
        val checker = Checker()
        val error = assertThrows(LangError::class.java) {
            checker.unify(
                Type.Con("pair", listOf(Type.NUM)),
                Type.Con("pair", listOf(Type.NUM, Type.STR)),
                span, "ctor",
            )
        }
        assertTrue(error.diag.message().contains("cannot unify pair<num> with pair<num, str>"))
    }

    @Test
    fun functionMismatchReportsTheInnerFailure() {
        val checker = Checker()
        val error = assertThrows(LangError::class.java) {
            checker.unify(
                Type.`fun`(Type.NUM, Type.STR),
                Type.`fun`(Type.NUM, Type.BOOL),
                span, "fun",
            )
        }
        assertEquals("cannot unify str with bool", error.diag.message())
    }

    @Test
    fun occursCheckRejectsInfiniteType() {
        val checker = Checker()
        val v = checker.freshVar()
        val error = assertThrows(LangError::class.java) {
            checker.unify(v, Type.`fun`(v, Type.NUM), span, "occurs")
        }
        assertEquals("infinite type, cannot construct a = a -> num", error.diag.message())
        assertEquals(span, error.diag.span())
        assertEquals("occurs", error.diag.note())
    }

    @Test
    fun occursCheckSeesThroughConstructors() {
        val checker = Checker()
        val v = checker.freshVar()
        val error = assertThrows(LangError::class.java) {
            checker.unify(v, Type.Con("list", listOf(v)), span, "occurs")
        }
        assertTrue(error.diag.message().contains("infinite type"))
    }

    @Test
    fun errorPrintsBothTypesInOneNamingContext() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        val error = assertThrows(LangError::class.java) {
            checker.unify(Type.`fun`(a, b), Type.NUM, span, "context")
        }
        // One context: the same variables keep their letters across
        // both sides of the message.
        assertEquals("cannot unify a -> b with num", error.diag.message())
    }
}
