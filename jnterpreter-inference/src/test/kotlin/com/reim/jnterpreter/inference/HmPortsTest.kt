package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The 8 hm.c demo cases, ported, plus the environment-leak case from
 * the design. The test AST (Lit/Var/Lam/App/Let) and the infer walk
 * live in this file: the inference module must not depend on Mini,
 * so the engine is exercised through a minimal language of its own.
 * Base-type mapping: hm.c `int` -> `num`, `string` -> `str`.
 */
class HmPortsTest {
    private val span = Span("test", 0, 1, 1, 1)

    /** A minimal expression language for exercising the engine. */
    private sealed interface Expr {
        data class Lit(val type: Type) : Expr
        data class Var(val name: String) : Expr
        data class Lam(val param: String, val body: Expr) : Expr
        data class App(val fn: Expr, val arg: Expr) : Expr
        data class Let(val name: String, val value: Expr, val body: Expr) : Expr
    }

    private fun infer(e: Expr, env: TypeEnv, checker: Checker): Type = when (e) {
        is Expr.Lit -> e.type
        is Expr.Var -> checker.instantiate(env.lookup(e.name, span))
        is Expr.Lam -> {
            val param = checker.freshVar()
            val bodyEnv = env.child()
            bodyEnv.define(e.param, Scheme.mono(param))
            Type.`fun`(param, infer(e.body, bodyEnv, checker))
        }
        is Expr.App -> {
            val fnTy = infer(e.fn, env, checker)
            val argTy = infer(e.arg, env, checker)
            val res = checker.freshVar()
            checker.unify(fnTy, Type.`fun`(argTy, res), span, "calling a non-function value")
            res
        }
        is Expr.Let -> {
            val valueTy = infer(e.value, env, checker)
            val bodyEnv = env.child()
            bodyEnv.define(e.name, checker.generalize(env, valueTy))
            infer(e.body, bodyEnv, checker)
        }
    }

    private fun inferType(e: Expr): String = infer(e, TypeEnv(), Checker()).toString()

    @Test
    fun identityInfersAToA() {
        assertEquals("a -> a", inferType(Expr.Lam("x", Expr.Var("x"))))
    }

    @Test
    fun polymorphicLetUsedAtNum() {
        val id = Expr.Lam("x", Expr.Var("x"))
        val program = Expr.Let("id", id, Expr.App(Expr.Var("id"), Expr.Lit(Type.NUM)))
        assertEquals("num", inferType(program))
    }

    @Test
    fun polymorphicLetUsedAtStr() {
        val id = Expr.Lam("x", Expr.Var("x"))
        val program = Expr.Let("id", id, Expr.App(Expr.Var("id"), Expr.Lit(Type.STR)))
        assertEquals("str", inferType(program))
    }

    @Test
    fun composeInfersNestedArrows() {
        // fn(f) => fn(x) => f(f(x))
        val program = Expr.Lam(
            "f",
            Expr.Lam(
                "x",
                Expr.App(Expr.Var("f"), Expr.App(Expr.Var("f"), Expr.Var("x"))),
            ),
        )
        assertEquals("(a -> a) -> a -> a", inferType(program))
    }

    @Test
    fun constFunctionPicksFirstArgument() {
        // let k = fn(x) => fn(y) => x in k(1)("s")
        val k = Expr.Lam("x", Expr.Lam("y", Expr.Var("x")))
        val program = Expr.Let(
            "k",
            k,
            Expr.App(
                Expr.App(Expr.Var("k"), Expr.Lit(Type.NUM)),
                Expr.Lit(Type.STR),
            ),
        )
        assertEquals("num", inferType(program))
    }

    @Test
    fun applyInfersTwoArrows() {
        // fn(f) => fn(x) => f(x)
        val program = Expr.Lam(
            "f",
            Expr.Lam("x", Expr.App(Expr.Var("f"), Expr.Var("x"))),
        )
        assertEquals("(a -> b) -> a -> b", inferType(program))
    }

    @Test
    fun selfApplicationIsAnInfiniteTypeError() {
        val error = assertThrows(LangError::class.java) {
            inferType(Expr.Lam("x", Expr.App(Expr.Var("x"), Expr.Var("x"))))
        }
        assertTrue(error.diag.message().contains("infinite type"), error.diag.message())
        assertEquals(span, error.diag.span())
    }

    @Test
    fun unboundVariableIsAnError() {
        val error = assertThrows(LangError::class.java) {
            inferType(Expr.Var("y"))
        }
        assertEquals("undefined variable 'y'", error.diag.message())
        assertEquals(span, error.diag.span())
    }

    @Test
    fun environmentVariableIsNotGeneralized() {
        // fn(y) => let f = fn(x) => y in f(1) infers a -> a, NOT a -> b:
        // f's result type is y's variable, free in the environment, so
        // generalization must not quantify it.
        val program = Expr.Lam(
            "y",
            Expr.Let(
                "f",
                Expr.Lam("x", Expr.Var("y")),
                Expr.App(Expr.Var("f"), Expr.Lit(Type.NUM)),
            ),
        )
        assertEquals("a -> a", inferType(program))
    }

    @Test
    fun polymorphicLetUsedAtTwoTypesInOneProgram() {
        // let id = fn(x) => x in id(42) is num and id("hi") is str,
        // checked through two independent instantiations of one scheme.
        val checker = Checker()
        val env = TypeEnv()
        val idType = infer(Expr.Lam("x", Expr.Var("x")), env, checker)
        val scheme = checker.generalize(env, idType)
        env.define("id", scheme)
        val first = checker.instantiate(scheme)
        val second = checker.instantiate(scheme)
        checker.unify(first, Type.`fun`(Type.NUM, Type.NUM), span, "num use")
        checker.unify(second, Type.`fun`(Type.STR, Type.STR), span, "str use")
        assertEquals("num -> num", first.toString())
        assertEquals("str -> str", second.toString())
    }
}
