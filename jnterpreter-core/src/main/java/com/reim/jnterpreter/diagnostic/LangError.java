package com.reim.jnterpreter.diagnostic;

/** The exception type for language errors. Parse errors and
 * evaluation errors are both {@code LangError}; each carries a
 * {@link Diag} with the message, the span, and an optional note.
 *
 * <p>A language may define its own exception type by subclassing
 * {@code LangError} and throwing the subtype from its own code (its
 * evaluator, its inference walk). The library itself throws plain
 * {@code LangError}; {@link com.reim.jnterpreter.repl.Repl} catches
 * the base type, so subtypes print through the same path, and a
 * language that needs its own error type at its own boundary catches
 * {@code LangError} and rethrows its own exception from the public
 * {@link #diag} field.
 */
public class LangError extends RuntimeException {
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
