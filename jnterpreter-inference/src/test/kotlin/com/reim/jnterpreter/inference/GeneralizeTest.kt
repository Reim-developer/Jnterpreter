package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Let-polymorphism: generalization and instantiation. */
class GeneralizeTest {
    private val span = Span("test", 0, 1, 1, 1)

    @Test
    fun generalizeQuantifiesFreeVariables() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        val scheme = checker.generalize(TypeEnv(), Type.`fun`(a, b))
        assertEquals(setOf(a, b), scheme.quantified)
    }

    @Test
    fun generalizeQuantifiesNothingForMonomorphicType() {
        val checker = Checker()
        val scheme = checker.generalize(TypeEnv(), Type.NUM)
        assertTrue(scheme.quantified.isEmpty())
    }

    @Test
    fun generalizeSkipsBoundVariables() {
        val checker = Checker()
        val a = checker.freshVar()
        val b = checker.freshVar()
        checker.unify(a, Type.NUM, span, "bind")
        val scheme = checker.generalize(TypeEnv(), Type.`fun`(a, b))
        assertEquals(setOf(b), scheme.quantified)
    }

    @Test
    fun generalizeDoesNotQuantifyEnvironmentFreeVariables() {
        val checker = Checker()
        val yVar = checker.freshVar()
        val env = TypeEnv()
        env.define("y", Scheme.mono(yVar))
        // f = fn(x) => y has type a -> b, where b is y's variable,
        // free in the environment. Only a is quantified.
        val xVar = checker.freshVar()
        val scheme = checker.generalize(env, Type.`fun`(xVar, yVar))
        assertEquals(setOf(xVar), scheme.quantified)
    }

    @Test
    fun instantiateReplacesQuantifiedVariablesConsistently() {
        val checker = Checker()
        val a = checker.freshVar()
        val scheme = Scheme(Type.`fun`(a, a), setOf(a))
        val instantiated = checker.instantiate(scheme) as Type.Fun
        // Both sides are the same fresh variable, and it is not a.
        assertSame(instantiated.from, instantiated.to)
        assertNotSame(a, instantiated.from)
    }

    @Test
    fun instantiateSharesUnquantifiedVariables() {
        val checker = Checker()
        val yVar = checker.freshVar()
        val xVar = checker.freshVar()
        val scheme = Scheme(Type.`fun`(xVar, yVar), setOf(xVar))
        val instantiated = checker.instantiate(scheme) as Type.Fun
        assertNotSame(xVar, instantiated.from)
        assertSame(yVar, instantiated.to)
    }

    @Test
    fun twoInstantiationsAreIndependent() {
        val checker = Checker()
        val a = checker.freshVar()
        // id : forall a. a -> a
        val scheme = Scheme(Type.`fun`(a, a), setOf(a))
        val first = checker.instantiate(scheme)
        val second = checker.instantiate(scheme)
        // Unifying the first with num -> num leaves the second free
        // to unify with str -> str.
        checker.unify(first, Type.`fun`(Type.NUM, Type.NUM), span, "first")
        checker.unify(second, Type.`fun`(Type.STR, Type.STR), span, "second")
        assertEquals("num -> num", first.toString())
        assertEquals("str -> str", second.toString())
    }

    @Test
    fun instantiateOfMonoSchemeKeepsVariables() {
        val checker = Checker()
        val a = checker.freshVar()
        val instantiated = checker.instantiate(Scheme.mono(Type.`fun`(a, Type.NUM))) as Type.Fun
        // The unquantified variable is shared as-is; the base type is
        // equal (substitution rebuilds constructor nodes).
        assertSame(a, instantiated.from)
        assertEquals(Type.NUM, instantiated.to)
    }
}
