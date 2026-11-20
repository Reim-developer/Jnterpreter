package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.Diag
import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span

/**
 * Hindley-Milner inference: fresh variables and unification with an
 * occurs check. One Checker per inference run; it is not thread-safe.
 */
class Checker {
    /**
     * A fresh, unbound type variable.
     *
     * @return the new variable
     */
    fun freshVar(): Type.Var = Type.Var()

    /**
     * Unifies two types, binding variables as needed.
     *
     * @param a the first type
     * @param b the second type
     * @param span the source range the constraint comes from; the
     *             error points here
     * @param context why the types must match; becomes the diagnostic
     *                note (for example, "the branches of if must have
     *                the same type")
     * @throws LangError if the types cannot unify, or unifying them
     *                   would build an infinite type
     */
    fun unify(a: Type, b: Type, span: Span, context: String) {
        val left = a.prune()
        val right = b.prune()
        when {
            left is Type.Var -> {
                if (left === right) return
                if (occurs(left, right)) throw infiniteType(left, right, span, context)
                left.bind(right)
            }
            right is Type.Var -> {
                if (occurs(right, left)) throw infiniteType(right, left, span, context)
                right.bind(left)
            }
            left is Type.Con && right is Type.Con -> {
                if (left.name != right.name || left.args.size != right.args.size) {
                    throw cannotUnify(left, right, span, context)
                }
                left.args.zip(right.args).forEach { (x, y) -> unify(x, y, span, context) }
            }
            left is Type.Fun && right is Type.Fun -> {
                unify(left.from, right.from, span, context)
                unify(left.to, right.to, span, context)
            }
            else -> throw cannotUnify(left, right, span, context)
        }
    }

    private fun occurs(variable: Type.Var, inType: Type): Boolean {
        return when (val t = inType.prune()) {
            is Type.Var -> t === variable
            is Type.Con -> t.args.any { occurs(variable, it) }
            is Type.Fun -> occurs(variable, t.from) || occurs(variable, t.to)
        }
    }

    private fun cannotUnify(a: Type, b: Type, span: Span, context: String): LangError {
        val printer = TypePrinter()
        val message = "cannot unify ${printer.format(a)} with ${printer.format(b)}"
        return LangError(Diag(message, span, context))
    }

    private fun infiniteType(variable: Type.Var, type: Type, span: Span, context: String): LangError {
        val printer = TypePrinter()
        val message =
            "infinite type, cannot construct ${printer.format(variable)} = ${printer.format(type)}"
        return LangError(Diag(message, span, context))
    }
}
