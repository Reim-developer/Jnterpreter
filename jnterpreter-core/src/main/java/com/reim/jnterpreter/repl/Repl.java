package com.reim.jnterpreter.repl;

import com.reim.jnterpreter.diagnostic.Diag;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.runtime.Value;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/** A read-eval-print loop: it reads lines from a reader, evaluates
 * each one with an {@link Evaluator}, and writes the results to a
 * writer. Blank lines are skipped; {@link Value.Nil} results are not
 * printed; a {@link LangError} is rendered by the
 * {@link ErrorRenderer} - by default as {@code error: <message>}.
 * The loop is never hardcoded to System.in/out, so it is testable with
 * string readers and writers and embeddable in any host. An optional
 * prompt is written and flushed before each line is read.
 */
public final class Repl {
    /** Evaluates one line of input and returns its value. */
    @FunctionalInterface
    public interface Evaluator {
        /**
         * Evaluates one line.
         *
         * @param line the input line, without its line terminator
         * @return the value of the line; {@link Value.Nil} is not printed
         * @throws LangError if the line fails to parse or evaluate; the
         *                   diagnostic is rendered by the error renderer
         */
        Value evalLine(String line);
    }

    /** Renders a language error as one line of REPL output. */
    @FunctionalInterface
    public interface ErrorRenderer {
        /**
         * Renders a diagnostic. The result is written as one line;
         * the loop adds the line terminator, so the result must not
         * end with one. The renderer sees the full diagnostic - the
         * message, the span, and the note - so a language can print
         * its errors in its own format, for example with the source
         * position or the Rust-style rendering of
         * {@link Diag#render(String)}.
         *
         * @param diag the diagnostic to render
         * @return the rendered text, without a trailing line terminator
         */
        String render(Diag diag);
    }

    /** The default renderer: {@code error: <message>}. */
    private static final ErrorRenderer DEFAULT_RENDERER =
        diag -> "error: " + diag.message();

    private final Evaluator eval;
    private final Reader in;
    private final Writer out;
    private final String prompt;
    private final ErrorRenderer errorRenderer;

    /**
     * Creates a REPL with no prompt and the default error renderer.
     *
     * @param eval the evaluator for input lines
     * @param in   where lines are read from
     * @param out  where results are written
     * @throws NullPointerException if any argument is null
     */
    public Repl(Evaluator eval, Reader in, Writer out) {
        this(eval, in, out, "", DEFAULT_RENDERER);
    }

    /**
     * Creates a REPL with a prompt, written and flushed before each
     * line is read, and the default error renderer.
     *
     * @param eval   the evaluator for input lines
     * @param in     where lines are read from
     * @param out    where results are written
     * @param prompt the prompt; an empty prompt writes nothing
     * @throws NullPointerException if any argument is null
     */
    public Repl(Evaluator eval, Reader in, Writer out, String prompt) {
        this(eval, in, out, prompt, DEFAULT_RENDERER);
    }

    /**
     * Creates a REPL with no prompt and a custom error renderer.
     *
     * @param eval          the evaluator for input lines
     * @param in            where lines are read from
     * @param out           where results are written
     * @param errorRenderer how language errors are rendered
     * @throws NullPointerException if any argument is null
     */
    public Repl(Evaluator eval, Reader in, Writer out, ErrorRenderer errorRenderer) {
        this(eval, in, out, "", errorRenderer);
    }

    /**
     * Creates a REPL with a prompt, written and flushed before each
     * line is read, and a custom error renderer.
     *
     * @param eval          the evaluator for input lines
     * @param in            where lines are read from
     * @param out           where results are written
     * @param prompt        the prompt; an empty prompt writes nothing
     * @param errorRenderer how language errors are rendered
     * @throws NullPointerException if any argument is null
     */
    public Repl(Evaluator eval, Reader in, Writer out, String prompt, ErrorRenderer errorRenderer) {
        this.eval = Objects.requireNonNull(eval, "eval");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.errorRenderer = Objects.requireNonNull(errorRenderer, "errorRenderer");
    }

    /**
     * Runs the loop until the reader is exhausted. Each line is
     * evaluated and its result printed; the writer is flushed after
     * every line.
     *
     * @throws IOException if reading or writing fails
     */
    public void run() throws IOException {
        BufferedReader reader = new BufferedReader(in);
        BufferedWriter writer = new BufferedWriter(out);
        while (true) {
            if (!prompt.isEmpty()) {
                writer.write(prompt);
                writer.flush();
            }
            String line = reader.readLine();
            if (line == null) break;
            if (line.isBlank()) continue;
            try {
                Value v = eval.evalLine(line);
                if (!(v instanceof Value.Nil)) {
                    writer.write(v.toString());
                    writer.newLine();
                }
            } catch (LangError e) {
                String rendered = Objects.requireNonNull(
                    errorRenderer.render(e.diag), "error renderer returned null");
                writer.write(rendered);
                writer.newLine();
            }
            writer.flush();
        }
    }
}
