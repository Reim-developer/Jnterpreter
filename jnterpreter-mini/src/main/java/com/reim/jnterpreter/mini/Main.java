package com.reim.jnterpreter.mini;

import com.reim.jnterpreter.repl.Repl;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Mini's entry point: a REPL over standard input and output. */
public final class Main {
    private Main() {}

    /**
     * Runs the Mini REPL.
     *
     * @param args ignored
     * @throws IOException if reading or writing fails
     */
    public static void main(String[] args) throws IOException {
        System.out.println("Mini - a demo language on Jnterpreter.");
        Mini.Session session = new Mini.Session();
        new Repl(session::evalLine,
            new InputStreamReader(System.in, StandardCharsets.UTF_8),
            new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
            "mini> ").run();
    }
}
