package com.reim.jnterpreter.inference

/**
 * Prints types with variables named by first appearance: `a`, `b`,
 * `c`, and so on; past 26 variables the names are `t26`, `t27`, and
 * so on. One printer names consistently across every type it formats,
 * so an error message can print both sides of a failed unification in
 * one context. Not public API.
 */
internal class TypePrinter {
    private val names = java.util.IdentityHashMap<Type.Var, String>()
    private var nextName = 0

    /**
     * Formats one type, with this printer's naming context.
     *
     * @param type the type to format
     * @return the printed type
     */
    fun format(type: Type): String = format(type, inLeft = false)

    private fun format(type: Type, inLeft: Boolean): String {
        return when (val t = type.prune()) {
            is Type.Var -> nameOf(t)
            is Type.Con -> {
                if (t.args.isEmpty()) {
                    t.name
                } else {
                    t.args.joinToString(prefix = "${t.name}<", separator = ", ", postfix = ">") {
                        format(it, inLeft = false)
                    }
                }
            }
            is Type.Fun -> {
                val rendered = "${format(t.from, inLeft = true)} -> ${format(t.to, inLeft = false)}"
                if (inLeft) "($rendered)" else rendered
            }
        }
    }

    private fun nameOf(variable: Type.Var): String {
        return names.getOrPut(variable) {
            val index = nextName++
            if (index < 26) ('a' + index).toString() else "t$index"
        }
    }
}
