package com.reim.jnterpreter.lexer;
import com.reim.jnterpreter.diagnostic.Span;

/** A token: a kind, the text it was scanned from, and the span it
 * covers. Kinds are plain strings defined by the language ("NUMBER",
 * "+"), so a lexer needs no shared enum with the parser.
 *
 * @param kind   the token kind, as defined by the language
 * @param lexeme the text the token was scanned from
 * @param span   the source range the token covers
 */
public record Token(String kind, String lexeme, Span span) {}
