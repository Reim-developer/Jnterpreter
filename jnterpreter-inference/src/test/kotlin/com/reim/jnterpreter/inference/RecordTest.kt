package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Closed records: field-wise unification, exact field sets. */
class RecordTest {
    private val span = Span("test", 0, 1, 1, 1)

    @Test
    fun recordUnifiesFieldwise() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        val first = Type.record(mapOf("x" to a, "y" to b))
        checker.unify(first, Type.record(mapOf("x" to Type.NUM, "y" to Type.STR)), span, "rec")
        assertEquals("{x: num, y: str}", first.toString())
    }

    @Test
    fun recordFieldOrderDoesNotMatter() {
        val checker = Checker()
        val first = Type.record(mapOf("x" to Type.NUM, "y" to Type.STR))
        val second = Type.record(mapOf("y" to Type.STR, "x" to Type.NUM))
        checker.unify(first, second, span, "rec")
        assertEquals("{x: num, y: str}", first.toString())
        assertEquals("{x: num, y: str}", second.toString())
    }

    @Test
    fun recordFieldSetMismatchFails() {
        val checker = Checker()
        val error = assertThrows(LangError::class.java) {
            checker.unify(
                Type.record(mapOf("x" to Type.NUM)),
                Type.record(mapOf("x" to Type.NUM, "y" to Type.STR)),
                span, "field sets must match",
            )
        }
        assertEquals("cannot unify {x: num} with {x: num, y: str}", error.diag.message())
        assertEquals(span, error.diag.span())
        assertEquals("field sets must match", error.diag.note())
    }

    @Test
    fun recordFieldTypeMismatchFailsOnFirstSortedField() {
        val checker = Checker()
        val error = assertThrows(LangError::class.java) {
            checker.unify(
                Type.record(mapOf("a" to Type.NUM, "b" to Type.STR)),
                Type.record(mapOf("a" to Type.STR, "b" to Type.NUM)),
                span, "rec",
            )
        }
        // Fields unify in sorted name order, so field a fails first.
        assertEquals("cannot unify num with str", error.diag.message())
    }

    @Test
    fun recordAgainstNonRecordFails() {
        val checker = Checker()
        val firstError = assertThrows(LangError::class.java) {
            checker.unify(Type.record(mapOf("x" to Type.NUM)), Type.NUM, span, "rec")
        }
        assertEquals("cannot unify {x: num} with num", firstError.diag.message())
        val a = checker.freshVar()
        val b = checker.freshVar()
        val secondError = assertThrows(LangError::class.java) {
            checker.unify(Type.record(mapOf("x" to Type.NUM)), Type.`fun`(a, b), span, "rec")
        }
        assertEquals("cannot unify {x: num} with a -> b", secondError.diag.message())
    }

    @Test
    fun variableBindsToRecord() {
        val checker = Checker()
        val v = checker.freshVar()
        checker.unify(v, Type.record(mapOf("x" to Type.NUM)), span, "bind")
        assertEquals("{x: num}", v.toString())
    }

    @Test
    fun occursCheckThroughRecord() {
        val checker = Checker()
        val v = checker.freshVar()
        val error = assertThrows(LangError::class.java) {
            checker.unify(v, Type.record(mapOf("x" to v)), span, "occurs")
        }
        assertEquals("infinite type, cannot construct a = {x: a}", error.diag.message())
        assertEquals(span, error.diag.span())
        assertEquals("occurs", error.diag.note())
    }

    @Test
    fun emptyRecordUnifiesOnlyWithEmpty() {
        val checker = Checker()
        checker.unify(Type.record(emptyMap()), Type.record(emptyMap()), span, "rec")
        val error = assertThrows(LangError::class.java) {
            checker.unify(
                Type.record(emptyMap()),
                Type.record(mapOf("x" to Type.NUM)),
                span, "rec",
            )
        }
        assertEquals("cannot unify {} with {x: num}", error.diag.message())
    }

    @Test
    fun generalizeQuantifiesRecordFields() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        val scheme = checker.generalize(TypeEnv(), Type.record(mapOf("x" to a, "y" to b)))
        assertEquals(setOf(a, b), scheme.quantified)
    }

    @Test
    fun generalizeSkipsEnvVarsInRecords() {
        val checker = Checker()
        val a = checker.freshVar()
        val c = checker.freshVar()
        val env = TypeEnv()
        env.define("y", Scheme.mono(c))
        val scheme = checker.generalize(env, Type.record(mapOf("x" to a, "y" to c)))
        assertEquals(setOf(a), scheme.quantified)
    }

    @Test
    fun instantiateRebuildsRecordIndependently() {
        val checker = Checker()
        val a = checker.freshVar()
        val scheme = Scheme(Type.record(mapOf("x" to a)), setOf(a))
        val first = checker.instantiate(scheme)
        val second = checker.instantiate(scheme)
        checker.unify(first, Type.record(mapOf("x" to Type.NUM)), span, "first")
        checker.unify(second, Type.record(mapOf("x" to Type.STR)), span, "second")
        assertEquals("{x: num}", first.toString())
        assertEquals("{x: str}", second.toString())
    }

    @Test
    fun instantiateSharesUnquantifiedRecordVars() {
        val checker = Checker()
        val a = checker.freshVar()
        val c = checker.freshVar()
        val scheme = Scheme(Type.record(mapOf("x" to a, "y" to c)), setOf(a))
        val instantiated = checker.instantiate(scheme) as Type.Rec
        assertNotSame(a, instantiated.fields.getValue("x"))
        assertSame(c, instantiated.fields.getValue("y"))
    }

    @Test
    fun recordEqualityIgnoresFieldOrder() {
        val first = Type.record(mapOf("x" to Type.NUM, "y" to Type.STR))
        val second = Type.record(mapOf("y" to Type.STR, "x" to Type.NUM))
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
