package io.github.reimdeveloper.jnterpreter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.function.Function;

/** A REPL is a function of (eval, reader, writer): testable, embeddable,
    never hardcoded to System.in/out. v1 reads one line per input; a
    multiline (brace-balance) REPL is a named gap, not a silent bug. */
public final class Repl {
    private Repl() {}

    public static void run(Function<String, Value> evalLine,
                           Reader in, Writer out) throws IOException {
        BufferedReader reader = new BufferedReader(in);
        BufferedWriter writer = new BufferedWriter(out);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                Value v = evalLine.apply(line);
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
