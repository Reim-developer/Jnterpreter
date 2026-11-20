package com.reim.jnterpreter.diagnostic;

import java.io.Serializable;

/** An error diagnostic: a message, the span it points at, and an
 * optional note. {@link #render(String)} prints it Rust-style, with
 * the source line and a caret under the span.
 *
 * @param message the error message
 * @param span    the source range the error points at
 * @param note    an optional hint shown after the caret, or null
 */
public record Diag(String message, Span span, String note)
    implements Serializable {
    /**
     * Renders this diagnostic against the full source text.
     *
     * @param sourceText the complete text of the source named by the span
     * @return the rendered diagnostic, ending with a newline
     */
    public String render(String sourceText) {
        String[] lines = sourceText.split("\n", -1);
        String lineText = span.line() - 1 < lines.length
            ? lines[span.line() - 1] : "";
        int caretLen = Math.max(1, span.end() - span.start());
        String gutter = " ".repeat(String.valueOf(span.line()).length());
        StringBuilder sb = new StringBuilder();
        sb.append("error: ").append(message).append('\n');
        sb.append(" --> ").append(span.source()).append(':')
            .append(span.line()).append(':').append(span.col()).append('\n');
        sb.append(gutter).append(" |\n");
        sb.append(span.line()).append(" | ").append(lineText).append('\n');
        sb.append(gutter).append(" | ")
            .append(" ".repeat(Math.max(0, span.col() - 1)))
            .append("^".repeat(caretLen)).append('\n');
        if (note != null) sb.append(" = note: ").append(note).append('\n');
        return sb.toString();
    }
}
