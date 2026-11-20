package com.reim.jnterpreter.diagnostic;

/** The single exception type for language errors. Parse errors and
 * evaluation errors are both {@code LangError}; each carries a
 * {@link Diag} with the message, the span, and an optional note.
 */
public final class LangError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** The diagnostic carried by this error. */
    public final Diag diag;

    /**
     * Creates an error from a diagnostic.
     *
     * @param diag the diagnostic; its message becomes the exception message
     */
    public LangError(Diag diag) { super(diag.message()); this.diag = diag; }

    /**
     * Creates an error with a message and a span, and no note.
     *
     * @param message the error message
     * @param span    the source range the error points at
     */
    public LangError(String message, Span span) {
        this(new Diag(message, span, null));
    }
}
