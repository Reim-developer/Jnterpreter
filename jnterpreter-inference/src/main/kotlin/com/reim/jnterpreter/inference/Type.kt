package com.reim.jnterpreter.inference

/**
 * A type: a variable, a named constructor, or a function type.
 *
 * The algebra is Hindley-Milner: variables are mutable (unifying one
 * binds it), constructors are named with argument types, and function
 * types nest to the right. Base types are nullary constructors
 * ([NUM], [STR], [BOOL], [NIL]). A type is not a pure value — a
 * variable's binding changes as inference proceeds — so tests assert
 * on the printed form, which is the type's observable contract.
 */
sealed interface Type {
    /**
     * Follows a variable's binding to its current representative.
     * A bound variable returns what it is bound to; an unbound
     * variable, a constructor, and a function type return
     * themselves. Follows only the top-level link, with path
     * compression — it does not rebuild function types.
     *
     * @return the representative of this type
     */
    fun prune(): Type

    /**
     * A type variable. Mutable: unifying it binds it, and [prune]
     * follows the binding. Created only by [Checker.freshVar];
     * identity is reference equality, so two variables are the same
     * only if they are the same object.
     */
    class Var internal constructor() : Type {
        /** The type this variable is bound to, or null when unbound. */
        private var instance: Type? = null

        /** Binds this variable to [type]. Called only by [Checker]. */
        internal fun bind(type: Type) {
            instance = type
        }

        override fun prune(): Type {
            val bound = instance ?: return this
            val pruned = bound.prune()
            instance = pruned
            return pruned
        }

        override fun toString(): String = TypePrinter().format(this)
    }

    /**
     * A named type constructor, with argument types. The base types
     * are nullary constructors ([NUM] and friends); the argument list
     * exists for parameterized types.
     *
     * @param name the constructor's name
     * @param args the argument types, empty for a base type
     */
    data class Con(val name: String, val args: List<Type>) : Type {
        override fun prune(): Type = this

        override fun toString(): String = TypePrinter().format(this)
    }

    /**
     * A function type, `from -> to`.
     *
     * @param from the argument type
     * @param to the result type
     */
    data class Fun(val from: Type, val to: Type) : Type {
        override fun prune(): Type = this

        override fun toString(): String = TypePrinter().format(this)
    }

    companion object {
        /** The type of numbers. */
        @JvmField
        val NUM: Type = Con("num", emptyList())

        /** The type of strings. */
        @JvmField
        val STR: Type = Con("str", emptyList())

        /** The type of booleans. */
        @JvmField
        val BOOL: Type = Con("bool", emptyList())

        /** The type of nil. */
        @JvmField
        val NIL: Type = Con("nil", emptyList())

        /**
         * A function type, `from -> to`.
         *
         * @param from the argument type
         * @param to the result type
         * @return the function type
         */
        @JvmStatic
        fun `fun`(from: Type, to: Type): Type = Fun(from, to)
    }
}
