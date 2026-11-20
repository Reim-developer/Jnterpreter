package com.reim.jnterpreter.lexer;
import com.reim.jnterpreter.diagnostic.Span;

/** A token: a kind, the text it was scanned from, and the span
 * it covers. The kind type is defined by the language -
 * typically an enum - so a mistyped kind is a compile error,
 * not a runtime surprise.
 *
 * @param <K> the language's token kind type
 * @param kind   the token kind
 * @param lexeme the text the token was scanned from
 * @param span   the source range the token covers
 */
public record Token<K>(K kind, String lexeme, Span span) {}
