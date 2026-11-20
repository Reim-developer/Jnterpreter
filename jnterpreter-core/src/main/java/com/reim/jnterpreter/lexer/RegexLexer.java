package com.reim.jnterpreter.lexer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.PatternSyntaxException;

/** A lexer built from annotations on the kind enum: each constant
 * carries exactly one of {@link Pattern}, {@link Literal}, or
 * {@link Skip}, and {@link #of(Class, String, String, Enum)} scans
 * the enum once and lexes from the rules it finds. The kind type
 * must be an enum - a non-enum kind type keeps the hand-written
 * {@link LexerBase} subclass, which stays the escape hatch for
 * lexers with rules annotations cannot express.
 *
 * <p>Matching is maximal munch (lex/flex semantics): at each
 * position every rule is tried, the longest match wins, and ties
 * are broken by declaration order, so an earlier constant wins over
 * a later one matching the same text. That is what makes keywords
 * work: {@code @Literal("if")} declared before
 * {@code @Pattern("[a-z]+")} wins at {@code "if"} (a tie, decided
 * by order) while the identifier wins at {@code "ifx"} (longer).
 * A winning skip rule consumes its text and produces no token. If
 * no rule matches, the lexer throws a {@code LangError} pointing at
 * the offending character. At end of input it returns the EOF
 * token, with an empty lexeme and a span at the end.
 *
 * <p>Patterns are compiled once at construction, not per token.
 * Matched text is consumed char by char through
 * {@link LexerBase#advance()}, so newlines inside tokens and
 * skipped text keep line and column tracking correct.
 *
 * <p>Example:
 * <pre>{@code
 * enum Kind {
 *     @Pattern("\\d+(\\.\\d+)?") NUMBER,
 *     @Literal("+") PLUS,
 *     @Skip("\\s+") WHITESPACE,
 *     EOF
 * }
 *
 * LexerBase<Kind> lexer = RegexLexer.of(Kind.class, "demo", "1 + 2", Kind.EOF);
 * List<Token<Kind>> tokens = lexer.tokenize();
 * }</pre>
 *
 * @param <K> the language's token kind type, an enum
 */
public final class RegexLexer<K extends Enum<K>> extends LexerBase<K> {
    /** One rule: a kind, how it matches, and whether it is skipped. */
    private record Rule<E extends Enum<E>>(
        E kind, java.util.regex.Pattern pattern, String literal, boolean skip) {
        /** The length of the match at {@code pos}, or -1 for no match. */
        int matchLength(String text, int pos) {
            if (literal != null) {
                return text.startsWith(literal, pos) ? literal.length() : -1;
            }
            java.util.regex.Matcher matcher = pattern.matcher(text);
            matcher.region(pos, text.length());
            return matcher.lookingAt() ? matcher.end() - pos : -1;
        }
    }

    private final List<Rule<K>> rules;

    private RegexLexer(String source, String text, K eofKind, List<Rule<K>> rules) {
        super(source, text, eofKind);
        this.rules = rules;
    }

    /**
     * Builds a lexer from the annotations on {@code kindClass}.
     * Every non-EOF constant must carry exactly one of
     * {@link Pattern}, {@link Literal}, or {@link Skip}; the EOF
     * kind must carry none. Rules are built in declaration order,
     * which is also the tie-break order at match time.
     *
     * @param kindClass the kind enum to scan for rule annotations
     * @param source    the name of the source the input comes from
     * @param text      the input to lex
     * @param eofKind   the kind that means end of input
     * @param <K> the language's token kind type, an enum
     * @return the lexer
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if the kind class is not an
     *         enum; the EOF kind is not a constant of it or carries
     *         a rule annotation; a non-EOF kind has no rule
     *         annotation or more than one; a pattern or literal can
     *         match the empty string; or a regex is invalid
     */
    public static <K extends Enum<K>> RegexLexer<K> of(
            Class<K> kindClass, String source, String text, K eofKind) {
        Objects.requireNonNull(kindClass, "kindClass");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(eofKind, "eofKind");
        if (!kindClass.isEnum()) {
            throw new IllegalArgumentException(
                "kind class " + kindClass.getName() + " is not an enum");
        }
        if (eofKind.getDeclaringClass() != kindClass) {
            throw new IllegalArgumentException("eof kind " + eofKind
                + " is not a constant of " + kindClass.getName());
        }
        List<Rule<K>> rules = new ArrayList<>();
        for (K constant : kindClass.getEnumConstants()) {
            if (constant == eofKind) {
                checkEofUnannotated(kindClass, constant);
                continue;
            }
            rules.add(ruleFor(kindClass, constant));
        }
        return new RegexLexer<>(source, text, eofKind, List.copyOf(rules));
    }

    private static <K extends Enum<K>> Rule<K> ruleFor(Class<K> kindClass, K constant) {
        Field field = fieldFor(kindClass, constant);
        Pattern patternAnn = field.getAnnotation(Pattern.class);
        Literal literalAnn = field.getAnnotation(Literal.class);
        Skip skipAnn = field.getAnnotation(Skip.class);
        if (patternAnn != null && literalAnn != null) {
            throw new IllegalArgumentException("kind " + constant.name()
                + " carries both @Pattern and @Literal; exactly one rule annotation is allowed");
        }
        if (skipAnn != null && (patternAnn != null || literalAnn != null)) {
            throw new IllegalArgumentException("kind " + constant.name()
                + " carries @Skip with another rule annotation; @Skip must stand alone");
        }
        if (patternAnn != null) {
            return new Rule<>(constant, compile(patternAnn.value(), constant), null, false);
        }
        if (skipAnn != null) {
            return new Rule<>(constant, compile(skipAnn.value(), constant), null, true);
        }
        if (literalAnn != null) {
            String literal = literalAnn.value();
            if (literal.isEmpty()) {
                throw new IllegalArgumentException("kind " + constant.name()
                    + " has an empty @Literal, which would match nothing and make no progress");
            }
            return new Rule<>(constant, null, literal, false);
        }
        throw new IllegalArgumentException("kind " + constant.name()
            + " has no @Pattern, @Literal, or @Skip annotation");
    }

    private static java.util.regex.Pattern compile(String regex, Enum<?> constant) {
        java.util.regex.Pattern pattern;
        try {
            pattern = java.util.regex.Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("kind " + constant.name()
                + " has an invalid regex '" + regex + "'", e);
        }
        if (pattern.matcher("").matches()) {
            throw new IllegalArgumentException("kind " + constant.name()
                + " has a regex '" + regex + "' that matches the empty string");
        }
        return pattern;
    }

    private static void checkEofUnannotated(Class<?> kindClass, Enum<?> eofKind) {
        Field field = fieldFor(kindClass, eofKind);
        if (field.getAnnotation(Pattern.class) != null
                || field.getAnnotation(Literal.class) != null
                || field.getAnnotation(Skip.class) != null) {
            throw new IllegalArgumentException("the EOF kind " + eofKind.name()
                + " must not carry a rule annotation; it is matched by end of input");
        }
    }

    private static Field fieldFor(Class<?> kindClass, Enum<?> constant) {
        try {
            return kindClass.getField(constant.name());
        } catch (NoSuchFieldException e) {
            // Unreachable: every enum constant has a public field.
            throw new IllegalStateException("enum constant " + constant.name()
                + " has no field in " + kindClass.getName(), e);
        }
    }

    /**
     * Scans and returns the next token, skipping text matched by
     * skip rules. At end of input returns the EOF token, with an
     * empty lexeme and a span at the end.
     *
     * @return the next token
     * @throws com.reim.jnterpreter.diagnostic.LangError if no rule
     *         matches at the current position; the error points at
     *         the offending character
     */
    @Override
    public Token<K> nextToken() {
        while (true) {
            if (eof()) {
                int startPos = pos, startLine = line, startCol = col;
                return new Token<>(eofKind, "", spanFrom(startPos, startLine, startCol));
            }
            Rule<K> best = null;
            int bestLen = 0;
            for (Rule<K> rule : rules) {
                int len = rule.matchLength(text, pos);
                if (len > bestLen) {
                    best = rule;
                    bestLen = len;
                }
            }
            if (best == null) {
                int startPos = pos, startLine = line, startCol = col;
                char c = advance();
                throw error("unexpected character '" + c + "'",
                    spanFrom(startPos, startLine, startCol));
            }
            int startPos = pos, startLine = line, startCol = col;
            for (int i = 0; i < bestLen; i++) {
                advance();
            }
            if (best.skip()) {
                continue;
            }
            return new Token<>(best.kind(), text.substring(startPos, pos),
                spanFrom(startPos, startLine, startCol));
        }
    }
}
