package com.reim.jnterpreter.repl;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.runtime.Value;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/** A read-eval-print loop: it reads lines from a reader, evaluates each
    one, and writes results to a writer. Testable and embeddable: it is
    never hardcoded to System.in/out. */
public final class Repl {
    /** Evaluates one line of input and returns its value. A result of
        {@link Value.Nil} is not printed; a {@link LangError} is printed
        as {@code error: <message>}. */
    @FunctionalInterface
    public interface Evaluator {
        Value evalLine(String line);
    }

    private final Evaluator eval;
    private final Reader in;
    private final Writer out;

    public Repl(Evaluator eval, Reader in, Writer out) {
        this.eval = Objects.requireNonNull(eval, "eval");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
    }

    public void run() throws IOException {
        BufferedReader reader = new BufferedReader(in);
        BufferedWriter writer = new BufferedWriter(out);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                Value v = eval.evalLine(line);
                if (!(v instanceof Value.Nil)) {
                    writer.write(v.toString());
                    writer.newLine();
                }
            } catch (LangError e) {
                writer.write("error: " + e.diag.message());
                writer.newLine();
            }
            writer.flush();
        }
    }
}
