package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.inference.TestLang.Expr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records through the infer walk: literals, projection, the limit. */
class RecordWalkTest {

    @Test
    fun polymorphicRecordFunction() {
        val id = Expr.Lam("x", Expr.Var("x"))
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM), "y" to Expr.Lit(Type.STR)))
        val program = Expr.Let("id", id, Expr.App(Expr.Var("id"), record))
        assertEquals("{x: num, y: str}", TestLang.inferType(program))
    }

    @Test
    fun recordLiteralInfersFieldTypes() {
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM), "y" to Expr.Lit(Type.STR)))
        assertEquals("{x: num, y: str}", TestLang.inferType(record))
    }

    @Test
    fun projectionWithKnownType() {
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM), "y" to Expr.Lit(Type.STR)))
        val program = Expr.Let("r", record, Expr.Project(Expr.Var("r"), "x"))
        assertEquals("num", TestLang.inferType(program))
    }

    @Test
    fun projectionOfSingleFieldParameter() {
        val accessor = Expr.Lam("r", Expr.Project(Expr.Var("r"), "x"))
        assertEquals("{x: a} -> a", TestLang.inferType(accessor))
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM)))
        assertEquals("num", TestLang.inferType(Expr.App(accessor, record)))
    }

    @Test
    fun projectionThroughPolymorphicRecordFails() {
        // THE closed-record limitation, pinned: a polymorphic field
        // accessor applied to a record with MORE fields than the
        // accessor knows fails, because records unify with exact
        // field sets. A future row-polymorphism round flips this test.
        val accessor = Expr.Lam("r", Expr.Project(Expr.Var("r"), "x"))
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM), "y" to Expr.Lit(Type.NUM)))
        val error = assertThrows(LangError::class.java) {
            TestLang.inferType(Expr.App(accessor, record))
        }
        assertEquals("cannot unify {x: a} with {x: num, y: num}", error.diag.message())
        assertEquals(TestLang.span, error.diag.span())
    }

    @Test
    fun projectionOfMissingFieldFails() {
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM)))
        val program = Expr.Let("r", record, Expr.Project(Expr.Var("r"), "y"))
        val error = assertThrows(LangError::class.java) {
            TestLang.inferType(program)
        }
        assertTrue(error.diag.message().contains("no field 'y' in {x: num}"), error.diag.message())
    }

    @Test
    fun duplicateRecordFieldFails() {
        val record = Expr.Record(listOf("x" to Expr.Lit(Type.NUM), "x" to Expr.Lit(Type.NUM)))
        val error = assertThrows(LangError::class.java) {
            TestLang.inferType(record)
        }
        assertTrue(error.diag.message().contains("duplicate field 'x'"), error.diag.message())
    }
}
