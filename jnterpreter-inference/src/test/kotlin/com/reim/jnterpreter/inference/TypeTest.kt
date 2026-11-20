package com.reim.jnterpreter.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Printing and pruning of the type algebra. */
class TypeTest {

    @Test
    fun baseTypesPrintTheirNames() {
        assertEquals("num", Type.NUM.toString())
        assertEquals("str", Type.STR.toString())
        assertEquals("bool", Type.BOOL.toString())
        assertEquals("nil", Type.NIL.toString())
    }

    @Test
    fun functionTypePrintsArrow() {
        assertEquals("num -> str", Type.`fun`(Type.NUM, Type.STR).toString())
    }

    @Test
    fun rightNestedArrowsNeedNoParens() {
        val a = Type.Var()
        val b = Type.Var()
        assertEquals("a -> b -> a", Type.`fun`(a, Type.`fun`(b, a)).toString())
    }

    @Test
    fun leftNestedArrowIsParenthesized() {
        val a = Type.Var()
        val b = Type.Var()
        assertEquals("(a -> b) -> a", Type.`fun`(Type.`fun`(a, b), a).toString())
    }

    @Test
    fun variablesAreNamedByFirstAppearance() {
        val first = Type.Var()
        val second = Type.Var()
        // The same variable twice keeps one letter.
        assertEquals("a -> a", Type.`fun`(first, first).toString())
        assertEquals("a -> b", Type.`fun`(first, second).toString())
        // Appearance order, not creation order: second appears first here.
        assertEquals("a -> b", Type.`fun`(second, first).toString())
    }

    @Test
    fun eachToStringStartsNamingFromA() {
        val a = Type.Var()
        val b = Type.Var()
        val type = Type.`fun`(a, b)
        assertEquals("a -> b", type.toString())
        assertEquals("a -> b", type.toString())
    }

    @Test
    fun twentySevenVariablesPrintLettersThenT26() {
        val vars = List(27) { Type.Var() }
        var type: Type = vars.last()
        for (i in vars.size - 2 downTo 0) {
            type = Type.`fun`(vars[i], type)
        }
        val expected = (('a'..'z').map { it.toString() } + "t26").joinToString(" -> ")
        assertEquals(expected, type.toString())
    }

    @Test
    fun constructorWithArgumentsPrintsAngleBrackets() {
        assertEquals("list<num>", Type.Con("list", listOf(Type.NUM)).toString())
        assertEquals(
            "pair<num, str>",
            Type.Con("pair", listOf(Type.NUM, Type.STR)).toString(),
        )
    }

    @Test
    fun pruneReturnsItselfForUnboundVarConstructorAndFun() {
        val v = Type.Var()
        assertSame(v, v.prune())
        assertSame(Type.NUM, Type.NUM.prune())
        val fn = Type.`fun`(Type.NUM, Type.STR)
        assertSame(fn, fn.prune())
    }

    @Test
    fun funFactoryBuildsFun() {
        val built = Type.`fun`(Type.NUM, Type.STR)
        assertEquals(Type.Fun(Type.NUM, Type.STR), built)
    }

    @Test
    fun recordPrintsSortedFields() {
        // Built y-first; field order is not part of the type, so
        // printing sorts by name.
        val record = Type.record(mapOf("y" to Type.STR, "x" to Type.NUM))
        assertEquals("{x: num, y: str}", record.toString())
    }

    @Test
    fun emptyRecordPrintsBraces() {
        assertEquals("{}", Type.record(emptyMap()).toString())
    }

    @Test
    fun nestedRecordPrints() {
        val inner = Type.record(mapOf("b" to Type.NUM))
        assertEquals("{a: {b: num}}", Type.record(mapOf("a" to inner)).toString())
    }

    @Test
    fun recordInFunctionPrintsWithoutParens() {
        val record = Type.record(mapOf("x" to Type.NUM))
        assertEquals("{x: num} -> num", Type.`fun`(record, Type.NUM).toString())
        assertEquals("num -> {x: num}", Type.`fun`(Type.NUM, record).toString())
    }

    @Test
    fun functionFieldPrintsWithoutParens() {
        val a = Type.Var()
        val b = Type.Var()
        val record = Type.record(mapOf("f" to Type.`fun`(a, b)))
        assertEquals("{f: a -> b}", record.toString())
    }

    @Test
    fun recordVariableNamingFollowsSortedOrder() {
        val first = Type.Var()
        val second = Type.Var()
        // Built y-first, but the printer walks fields sorted, so the
        // x field's variable is named first.
        val record = Type.record(mapOf("y" to first, "x" to second))
        assertEquals("{x: a, y: b}", record.toString())
    }
}
