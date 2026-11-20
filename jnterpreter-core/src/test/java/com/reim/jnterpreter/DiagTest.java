package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.reim.jnterpreter.diagnostic.Diag;
import com.reim.jnterpreter.diagnostic.Span;
import org.junit.jupiter.api.Test;

// Rendering of spans and diagnostics: every assertion is the exact
// full render, not a substring match.

class DiagTest {

    private static String render(String message, Span span, String note, String source) {
        return new Diag(message, span, note).render(source);
    }

    @Test
    void rendersSingleLineSpan() {
        String rendered = render("unexpected token ';'",
            new Span("demo", 8, 9, 1, 9), null, "let x = ;");
        assertEquals(String.join("\n",
            "error: unexpected token ';'",
            " --> demo:1:9",
            "  |",
            "1 | let x = ;",
            "  |         ^",
            ""), rendered);
    }

    @Test
    void caretLengthMatchesSpan() {
        String rendered = render("m", new Span("demo", 1, 4, 1, 2), null, "abcdef");
        assertEquals(String.join("\n",
            "error: m",
            " --> demo:1:2",
            "  |",
            "1 | abcdef",
            "  |  ^^^",
            ""), rendered);
    }

    @Test
    void rendersNote() {
        String withNote = render("m", new Span("demo", 0, 1, 1, 1), "the note", "x");
        assertEquals(String.join("\n",
            "error: m",
            " --> demo:1:1",
            "  |",
            "1 | x",
            "  | ^",
            " = note: the note",
            ""), withNote);
        String withoutNote = render("m", new Span("demo", 0, 1, 1, 1), null, "x");
        assertEquals(String.join("\n",
            "error: m",
            " --> demo:1:1",
            "  |",
            "1 | x",
            "  | ^",
            ""), withoutNote);
    }

    @Test
    void twoDigitLineNumberAlignsGutter() {
        String source = String.join("\n",
            "l1", "l2", "l3", "l4", "l5", "l6", "l7", "l8", "l9", "l10", "l11", "l12");
        String rendered = render("m", new Span("demo", 33, 36, 12, 1), null, source);
        assertEquals(String.join("\n",
            "error: m",
            " --> demo:12:1",
            "   |",
            "12 | l12",
            "   | ^^^",
            ""), rendered);
    }

    @Test
    void spanPastEndOfLine() {
        String rendered = render("m", new Span("demo", 7, 8, 5, 1), null, "one\ntwo");
        assertEquals(String.join("\n",
            "error: m",
            " --> demo:5:1",
            "  |",
            "5 | ",
            "  | ^",
            ""), rendered);
    }

    @Test
    void emptySpanRendersOneCaret() {
        String rendered = render("m", new Span("demo", 3, 3, 1, 4), null, "abcdef");
        assertEquals(String.join("\n",
            "error: m",
            " --> demo:1:4",
            "  |",
            "1 | abcdef",
            "  |    ^",
            ""), rendered);
    }
}
