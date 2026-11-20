package com.reim.jnterpreter.diagnostic;

/** The one error type: parse errors and eval errors are both this. */
public final class LangError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final Diag diag;

    public LangError(Diag diag) { super(diag.message()); this.diag = diag; }

    public LangError(String message, Span span) {
        this(new Diag(message, span, null));
    }
}
