package com.reim.jnterpreter.inference

import com.reim.jnterpreter.diagnostic.LangError
import com.reim.jnterpreter.diagnostic.Span

/**
 * Test infrastructure for the inference engine: a minimal
 * expression language (AST + infer walk) that exercises
 * Checker primitives. This is NOT a demo language - it has
 * no lexer, no parser, no evaluator, no REPL; it exists only
 * so engine tests can run whole programs instead of single
 * unify calls. Test sources only, never shipped.
 */
object TestLang {
    /** The single span every walk step reports errors at. */
    val span: Span = Span("test", 0, 1, 1, 1)

    /** A minimal expression language for exercising the engine. */
    sealed interface Expr {
        data class Lit(val type: Type) : Expr
        data class Var(val name: String) : Expr
        data class Lam(val param: String, val body: Expr) : Expr
        data class App(val fn: Expr, val arg: Expr) : Expr
        data class Let(val name: String, val value: Expr, val body: Expr) : Expr
        data class LetRec(val name: String, val value: Expr, val body: Expr) : Expr
        data class Record(val fields: List<Pair<String, Expr>>) : Expr
        data class Project(val record: Expr, val field: String) : Expr
    }

    /**
     * Infers the type of [e] in [env], using [checker]. Record
     * literals infer their fields in source order (a duplicate
     * field name is an error); projection looks the field up in
     * a known record type, or constrains an unknown record type
     * to a single-field record.
     *
     * @param e the expression to infer
     * @param env the type environment
     * @param checker the inference engine
     * @return the inferred type
     * @throws LangError if the expression fails to type-check
     */
    fun infer(e: Expr, env: TypeEnv, checker: Checker): Type = when (e) {
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
        is Expr.LetRec -> {
            val scheme = checker.letrec(env, e.name, span) { valueEnv ->
                infer(e.value, valueEnv, checker)
            }
            val bodyEnv = env.child()
            bodyEnv.define(e.name, scheme)
            infer(e.body, bodyEnv, checker)
        }
        is Expr.Record -> {
            val fieldTys = LinkedHashMap<String, Type>()
            for ((name, fieldExpr) in e.fields) {
                if (fieldTys.containsKey(name)) {
                    throw LangError("duplicate field '$name'", span)
                }
                fieldTys[name] = infer(fieldExpr, env, checker)
            }
            Type.record(fieldTys)
        }
        is Expr.Project -> {
            when (val recordTy = infer(e.record, env, checker).prune()) {
                is Type.Rec -> {
                    recordTy.fields[e.field]
                        ?: throw LangError("no field '${e.field}' in $recordTy", span)
                }
                is Type.Var -> {
                    val result = checker.freshVar()
                    checker.unify(
                        recordTy,
                        Type.record(mapOf(e.field to result)),
                        span,
                        "projecting field '${e.field}'",
                    )
                    result
                }
                else -> throw LangError(
                    "cannot project field '${e.field}' from a non-record type",
                    span,
                )
            }
        }
    }

    /**
     * Infers the type of [e] in a fresh environment and returns
     * its printed form.
     *
     * @param e the expression to infer
     * @return the printed inferred type
     */
    fun inferType(e: Expr): String = infer(e, TypeEnv(), Checker()).toString()
}
