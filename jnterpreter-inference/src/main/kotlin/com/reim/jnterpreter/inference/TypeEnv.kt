package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span

/**
 * Lexical scopes of type schemes: a chain of environments, mirroring
 * [com.reim.jnterpreter.runtime.Env]. A closure's type environment is
 * the one where it was defined.
 *
 * @param parent the enclosing scope, or null for a top-level environment
 */
class TypeEnv @JvmOverloads constructor(private val parent: TypeEnv? = null) {
    private val vars = HashMap<String, Scheme>()

    /** Creates a new environment nested in this one. */
    fun child(): TypeEnv = TypeEnv(this)

    /**
     * Defines a name in this environment, shadowing any definition of
     * the same name in an enclosing scope.
     *
     * @param name the variable name
     * @param scheme its scheme
     */
    fun define(name: String, scheme: Scheme) {
        vars[name] = scheme
    }

    /**
     * Looks up a name, walking up the chain of enclosing scopes.
     *
     * @param name the variable name
     * @param span the span of the use, for the error if undefined
     * @return the name's scheme
     * @throws LangError if no scope in the chain defines the name
     */
    fun lookup(name: String, span: Span): Scheme {
        vars[name]?.let { return it }
        parent?.let { return it.lookup(name, span) }
        throw LangError("undefined variable '$name'", span)
    }

    /**
     * Every scheme defined in this environment and its enclosing
     * scopes, for generalization. Not public API.
     *
     * @return the schemes in this chain
     */
    internal fun allSchemes(): List<Scheme> {
        val out = ArrayList(vars.values)
        var scope = parent
        while (scope != null) {
            out.addAll(scope.vars.values)
            scope = scope.parent
        }
        return out
    }
}
