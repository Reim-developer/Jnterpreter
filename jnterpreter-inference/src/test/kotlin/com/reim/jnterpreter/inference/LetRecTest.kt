package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.inference.TestLang.Expr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Recursive let: the name is monomorphic in its value, polymorphic in the body. */
class LetRecTest {

    @Test
    fun recursiveFunctionInfers() {
        // letrec f = fn(n) => f(n) in f
        val program = Expr.LetRec(
            "f",
            Expr.Lam("n", Expr.App(Expr.Var("f"), Expr.Var("n"))),
            Expr.Var("f"),
        )
        assertEquals("a -> b", TestLang.inferType(program))
    }

    @Test
    fun recursiveFunctionApplied() {
        // letrec f = fn(n) => f(n) in f(42) infers a: the result
        // type is unconstrained - the function never returns a
        // concrete value - so it stays a variable.
        val program = Expr.LetRec(
            "f",
            Expr.Lam("n", Expr.App(Expr.Var("f"), Expr.Var("n"))),
            Expr.App(Expr.Var("f"), Expr.Lit(Type.NUM)),
        )
        assertEquals("a", TestLang.inferType(program))
    }

    @Test
    fun letrecWithoutSelfUseGeneralizesLikeLetAtNum() {
        val id = Expr.Lam("x", Expr.Var("x"))
        val program = Expr.LetRec("id", id, Expr.App(Expr.Var("id"), Expr.Lit(Type.NUM)))
        assertEquals("num", TestLang.inferType(program))
    }

    @Test
    fun letrecWithoutSelfUseGeneralizesLikeLetAtStr() {
        val id = Expr.Lam("x", Expr.Var("x"))
        val program = Expr.LetRec("id", id, Expr.App(Expr.Var("id"), Expr.Lit(Type.STR)))
        assertEquals("str", TestLang.inferType(program))
    }

    @Test
    fun selfUseIsMonomorphic() {
        // letrec f = fn(x) => x(f(1))(f("s")) in f: the two
        // self-uses must agree on one type, and they do not.
        // With polymorphic self-use this program would
        // type-check; with monomorphic self-use it fails.
        val value = Expr.Lam(
            "x",
            Expr.App(
                Expr.App(Expr.Var("x"), Expr.App(Expr.Var("f"), Expr.Lit(Type.NUM))),
                Expr.App(Expr.Var("f"), Expr.Lit(Type.STR)),
            ),
        )
        val program = Expr.LetRec("f", value, Expr.Var("f"))
        val error = assertThrows(LangError::class.java) {
            TestLang.inferType(program)
        }
        assertTrue(error.diag.message().contains("cannot unify num with str"), error.diag.message())
    }

    @Test
    fun selfApplicationInLetrecIsInfiniteType() {
        // letrec f = f(42) in f: the value's type is the result
        // of applying f, so unifying the name's variable with it
        // builds an infinite type.
        val program = Expr.LetRec(
            "f",
            Expr.App(Expr.Var("f"), Expr.Lit(Type.NUM)),
            Expr.Var("f"),
        )
        val error = assertThrows(LangError::class.java) {
            TestLang.inferType(program)
        }
        assertEquals("infinite type, cannot construct a = num -> a", error.diag.message())
        assertEquals(TestLang.span, error.diag.span())
    }

    @Test
    fun bareSelfReferenceIsWellTyped() {
        // letrec x = x in x infers a: a bare self-reference
        // constrains nothing. Divergence is a runtime matter,
        // not a type error.
        val program = Expr.LetRec("x", Expr.Var("x"), Expr.Var("x"))
        assertEquals("a", TestLang.inferType(program))
    }

    @Test
    fun nestedLetrecWorks() {
        val inner = Expr.LetRec(
            "g",
            Expr.Lam("m", Expr.App(Expr.Var("g"), Expr.Var("m"))),
            Expr.Var("g"),
        )
        val program = Expr.LetRec(
            "f",
            Expr.Lam("n", Expr.App(Expr.Var("f"), Expr.Var("n"))),
            inner,
        )
        assertEquals("a -> b", TestLang.inferType(program))
    }

    @Test
    fun failedLetrecDefinesNothing() {
        val value = Expr.Lam(
            "x",
            Expr.App(
                Expr.App(Expr.Var("x"), Expr.App(Expr.Var("f"), Expr.Lit(Type.NUM))),
                Expr.App(Expr.Var("f"), Expr.Lit(Type.STR)),
            ),
        )
        val program = Expr.LetRec("f", value, Expr.Var("f"))
        val checker = Checker()
        val env = TypeEnv()
        assertThrows(LangError::class.java) {
            TestLang.infer(program, env, checker)
        }
        val lookupError = assertThrows(LangError::class.java) {
            env.lookup("f", TestLang.span)
        }
        assertEquals("undefined variable 'f'", lookupError.diag.message())
    }

    @Test
    fun letrecPrimitiveGeneralizes() {
        val checker = Checker()
        val a = checker.freshVar()
        val scheme = checker.letrec(TypeEnv(), "f", TestLang.span) { _ ->
            Type.`fun`(a, a)
        }
        assertEquals(setOf(a), scheme.quantified)
    }

    @Test
    fun letrecPrimitiveBindsNameMonomorphically() {
        val checker = Checker()
        val captured = ArrayList<Scheme>()
        checker.letrec(TypeEnv(), "f", TestLang.span) { valueEnv ->
            captured.add(valueEnv.lookup("f", TestLang.span))
            Type.NUM
        }
        assertEquals(1, captured.size)
        assertTrue(captured[0].quantified.isEmpty())
        assertTrue(captured[0].type is Type.Var)
    }
}
