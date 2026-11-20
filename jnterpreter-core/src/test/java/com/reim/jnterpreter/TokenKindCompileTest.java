package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

// Negative compilation tests for typed token kinds: a valid program
// using the typed API compiles, and kind mismatches fail to compile.
// These tests need the core classes on java.class.path - the harness
// runs the JUnit console launcher with -cp, not -jar.

class TokenKindCompileTest {

    private static final String VALID = """
        import com.reim.jnterpreter.lexer.Token;
        import com.reim.jnterpreter.parser.Parser;
        import java.util.List;

        public class ValidTokenKind {
            enum Kind { NUMBER, RPAREN, EOF }
            public void run() {
                Token<Kind> t = new Token<>(Kind.NUMBER, "1", null);
                Parser<String, Kind> p = new Parser<>(
                    List.of(new Token<>(Kind.NUMBER, "1", null)), Kind.EOF);
                p.expect(Kind.RPAREN, "')'");
                p.match(Kind.NUMBER);
            }
        }
        """;

    private static final String WRONG_TOKEN = """
        import com.reim.jnterpreter.lexer.Token;

        public class WrongTokenKind {
            enum Kind { NUMBER }
            enum OtherKind { NUMBER }
            public void run() {
                Token<OtherKind> t = new Token<>(Kind.NUMBER, "1", null);
            }
        }
        """;

    private static final String WRONG_EOF_KIND = """
        import com.reim.jnterpreter.lexer.Token;
        import com.reim.jnterpreter.parser.Parser;
        import java.util.List;

        public class WrongParserKind {
            enum Kind { NUMBER, EOF }
            enum OtherKind { EOF }
            public void run() {
                Parser<String, Kind> p = new Parser<>(
                    List.of(new Token<>(Kind.NUMBER, "1", null)), OtherKind.EOF);
            }
        }
        """;

    private static final String WRONG_TOKEN_LIST = """
        import com.reim.jnterpreter.lexer.Token;
        import com.reim.jnterpreter.parser.Parser;
        import java.util.List;

        public class WrongParserTokens {
            enum Kind { NUMBER, EOF }
            enum OtherKind { EOF }
            public void run() {
                Parser<String, OtherKind> p = new Parser<>(
                    List.of(new Token<>(Kind.NUMBER, "1", null)), OtherKind.EOF);
            }
        }
        """;

    @Test
    void validTypedProgramCompiles() throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = compile("ValidTokenKind", VALID);
        assertTrue(diagnostics.getDiagnostics().isEmpty(),
            () -> diagnostics.getDiagnostics().toString());
    }

    @Test
    void wrongTokenKindFailsToCompile() throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = compile("WrongTokenKind", WRONG_TOKEN);
        assertHasError(diagnostics);
    }

    @Test
    void wrongParserKindFailsToCompile() throws IOException {
        assertHasError(compile("WrongParserKind", WRONG_EOF_KIND));
        assertHasError(compile("WrongParserTokens", WRONG_TOKEN_LIST));
    }

    private static void assertHasError(DiagnosticCollector<JavaFileObject> diagnostics) {
        assertFalse(diagnostics.getDiagnostics().isEmpty());
        assertTrue(diagnostics.getDiagnostics().stream()
            .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR));
    }

    private static DiagnosticCollector<JavaFileObject> compile(String className, String code)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "no system compiler - tests must run on a JDK");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"));
            compiler.getTask(null, fileManager, diagnostics, options, null,
                List.of(source(className, code))).call();
        }
        return diagnostics;
    }

    private static JavaFileObject source(String className, String code) {
        return new SimpleJavaFileObject(
            URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }
}
