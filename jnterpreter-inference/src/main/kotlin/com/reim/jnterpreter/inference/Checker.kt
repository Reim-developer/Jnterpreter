package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.Diag
import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span

/**
 * Hindley-Milner inference: fresh variables, unification with an
 * occurs check, generalization, and instantiation. One Checker per
 * inference run; it is not thread-safe.
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

    /**
     * Generalizes a type over an environment: quantifies the free
     * variables of [type] that do not appear free in any scheme in
     * [env]. A variable free in the environment is never quantified.
     *
     * @param env the environment the type is generalized over
     * @param type the type to generalize
     * @return the scheme quantifying the type's environment-free variables
     */
    fun generalize(env: TypeEnv, type: Type): Scheme {
        val envVars = env.allSchemes()
            .flatMapTo(HashSet()) { freeVars(it.type) - it.quantified }
        return Scheme(type, freeVars(type) - envVars)
    }

    /**
     * Instantiates a scheme: replaces each quantified variable with a
     * fresh one, consistently within this one call. Unquantified
     * variables are shared as-is.
     *
     * @param scheme the scheme to instantiate
     * @return the instantiated type
     */
    fun instantiate(scheme: Scheme): Type {
        return substitute(scheme.type, scheme.quantified, HashMap())
    }

    private fun substitute(
        type: Type,
        quantified: Set<Type.Var>,
        mapping: MutableMap<Type.Var, Type.Var>,
    ): Type {
        return when (val t = type.prune()) {
            is Type.Var -> {
                if (t in quantified) mapping.getOrPut(t) { freshVar() } else t
            }
            is Type.Con -> Type.Con(t.name, t.args.map { substitute(it, quantified, mapping) })
            is Type.Fun -> Type.Fun(
                substitute(t.from, quantified, mapping),
                substitute(t.to, quantified, mapping),
            )
        }
    }

    private fun freeVars(type: Type): Set<Type.Var> {
        val out = HashSet<Type.Var>()
        collectFreeVars(type, out)
        return out
    }

    private fun collectFreeVars(type: Type, out: MutableSet<Type.Var>) {
        when (val t = type.prune()) {
            is Type.Var -> out.add(t)
            is Type.Con -> t.args.forEach { collectFreeVars(it, out) }
            is Type.Fun -> {
                collectFreeVars(t.from, out)
                collectFreeVars(t.to, out)
            }
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
