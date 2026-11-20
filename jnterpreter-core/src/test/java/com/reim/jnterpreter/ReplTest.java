package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.repl.Repl;
import com.reim.jnterpreter.runtime.Value;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.Test;

// The REPL loop: blank lines, nil results, errors, flushing, EOF.
// Expected output uses the platform line separator: the REPL writes
// through BufferedWriter.newLine(), not a hardcoded "\n".

class ReplTest {
    private static final String NL = System.lineSeparator();
    private static final Span SPAN = new Span("test", 0, 1, 1, 1);

    private static String run(String input, Repl.Evaluator eval) throws IOException {
        StringWriter out = new StringWriter();
        new Repl(eval, new StringReader(input), out).run();
        return out.toString();
    }

    @Test
    void blankLinesSkipped() throws IOException {
        String out = run("\n\n1\n\n", line -> new Value.Num(Double.parseDouble(line)));
        assertEquals("1" + NL, out);
    }

    @Test
    void nilResultsNotPrinted() throws IOException {
        String out = run("x\n", line -> new Value.Nil());
        assertEquals("", out);
    }

    @Test
    void errorLinePrinted() throws IOException {
        String out = run("x\n", line -> {
            throw new LangError("boom", SPAN);
        });
        assertEquals("error: boom" + NL, out);
    }

    @Test
    void flushesAfterEveryLine() throws IOException {
        int[] flushes = {0};
        Writer counting = new StringWriter() {
            @Override public void flush() {
                flushes[0]++;
                super.flush();
            }
        };
        new Repl(line -> new Value.Num(1), new StringReader("1\n2\n"), counting).run();
        assertEquals(2, flushes[0]);
    }

    @Test
    void eofEndsLoop() throws IOException {
        String out = run("1", line -> new Value.Num(Double.parseDouble(line)));
        assertEquals("1" + NL, out);
    }

    @Test
    void valuesPrintedInOrder() throws IOException {
        String out = run("1\n2\n", line -> new Value.Num(Double.parseDouble(line)));
        assertEquals("1" + NL + "2" + NL, out);
    }
}
