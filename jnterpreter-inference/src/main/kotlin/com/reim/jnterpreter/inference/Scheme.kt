package com.reim.jnterpreter.inference

/**
 * A type scheme: a type plus the variables quantified over it. A
 * let-bound name stores a scheme; each use instantiates it. A scheme
 * with no quantified variables is monomorphic - see [mono].
 *
 * @param type the scheme's type
 * @param quantified the variables quantified over the type; copied on
 *                   construction, so later changes to the caller's set
 *                   do not affect the scheme
 */
class Scheme(val type: Type, quantified: Set<Type.Var>) {
    /** The variables quantified over the type. */
    val quantified: Set<Type.Var> = quantified.toSet()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Scheme) return false
        return type == other.type && quantified == other.quantified
    }

    override fun hashCode(): Int = 31 * type.hashCode() + quantified.hashCode()

    override fun toString(): String = "Scheme(type=$type, quantified=$quantified)"

    companion object {
        /**
         * A monomorphic scheme: the type, with nothing quantified.
         *
         * @param type the type
         * @return the monomorphic scheme
         */
        @JvmStatic
        fun mono(type: Type): Scheme = Scheme(type, emptySet())
    }
}
