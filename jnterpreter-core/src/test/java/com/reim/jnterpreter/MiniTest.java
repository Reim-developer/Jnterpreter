package com.reim.jnterpreter;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.repl.Repl;
import com.reim.jnterpreter.runtime.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

// Ports all 10 scenarios / 20 checks from the spike's Mini.main() to JUnit 5.

class MiniTest {

    private static void assertNum(Value v, double expected) {
        assertTrue(v instanceof Value.Num, "expected Num, got " + v);
        assertEquals(expected, ((Value.Num) v).v());
    }

    private static void assertBool(Value v, boolean expected) {
        assertTrue(v instanceof Value.Bool, "expected Bool, got " + v);
        assertEquals(expected, ((Value.Bool) v).v());
    }

    @Test
    void precedenceAndLeftAssociativity() {
        assertNum(new Mini.Session().eval("1 + 2 * 3;", "demo"), 7);
        assertNum(new Mini.Session().eval("(1 + 2) * 3;", "demo"), 9);
        assertNum(new Mini.Session().eval("10 - 4 - 3;", "demo"), 3);
        assertNum(new Mini.Session().eval("-2 * 3;", "demo"), -6);
    }

    @Test
    void letAndVariables() {
        assertNum(new Mini.Session().eval("let x = 5; x * 2;", "demo"), 10);
    }

    @Test
    void closuresCaptureLexically() {
        Mini.Session session = new Mini.Session();
        assertNum(session.eval(
            "let add = fn(n) { fn(x) { x + n; }; }; let add5 = add(5); add5(10);",
            "demo"), 15);
    }

    @Test
    void ifElse() {
        assertNum(new Mini.Session().eval("if (1 < 2) { 10; } else { 20; }", "demo"), 10);
        assertNum(new Mini.Session().eval("if (1 > 2) { 10; } else { 20; }", "demo"), 20);
    }

    @Test
    void stringsAndNativePrint() {
        Mini.Session session = new Mini.Session();
        Value concat = session.eval("\"a\" + \"b\";", "demo");
        assertTrue(concat instanceof Value.Str str && str.v().equals("ab"));
        session.eval("print(\"hi\");", "demo");
        // print() writes through PrintStream.println, which ends lines with
        // the platform separator ("\r\n" on Windows). The expected string
        // must use it too — a hardcoded "\n" failed CI run #12 on Windows.
        assertEquals("hi" + System.lineSeparator(), session.output());
    }

    @Test
    void parseErrorRendersWithSpan() {
        String bad = "let x = ;";
        LangError error = assertThrows(LangError.class,
            () -> new Mini.Session().eval(bad, "demo"));
        String rendered = error.diag.render(bad);
        assertTrue(rendered.contains("demo:1:9"), rendered);
        assertTrue(rendered.contains("unexpected token ';'"), rendered);
        assertTrue(rendered.contains("^"), rendered);
    }

    @Test
    void undefinedVariableAndTypeErrorCarrySpans() {
        LangError undefined = assertThrows(LangError.class,
            () -> new Mini.Session().eval("y + 1;", "demo"));
        assertTrue(undefined.diag.message().contains("undefined variable 'y'"));
        assertEquals(1, undefined.diag.span().line());
        assertEquals(1, undefined.diag.span().col());
        LangError typeError = assertThrows(LangError.class,
            () -> new Mini.Session().eval("1 + true;", "demo"));
        assertTrue(typeError.diag.message().contains("expected numbers"));
    }

    @Test
    void shortCircuitAndOr() {
        assertBool(new Mini.Session().eval("false && y;", "demo"), false);
        assertBool(new Mini.Session().eval("true || y;", "demo"), true);
    }

    @Test
    void blockScopeDoesNotLeak() {
        assertNum(new Mini.Session().eval("let x = 1; { let x = 2; } x;", "demo"), 1);
    }

    @Test
    void replSession() throws IOException {
        Mini.Session repl = new Mini.Session();
        StringWriter out = new StringWriter();
        new Repl(repl::evalLine,
            new StringReader("let a = 2;\na * 3\nb * 2\n"), out).run();
        // Repl writes through BufferedWriter.newLine(): the platform
        // separator, not always "\n" (see stringsAndNativePrint).
        String nl = System.lineSeparator();
        assertEquals("6" + nl + "error: undefined variable 'b'" + nl, out.toString());
    }
}
