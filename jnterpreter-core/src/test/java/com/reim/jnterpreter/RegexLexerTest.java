package com.reim.jnterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.lexer.LexerBase;
import com.reim.jnterpreter.lexer.Literal;
import com.reim.jnterpreter.lexer.Pattern;
import com.reim.jnterpreter.lexer.RegexLexer;
import com.reim.jnterpreter.lexer.Skip;
import com.reim.jnterpreter.lexer.Token;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.jupiter.api.Test;

// The annotation-driven lexer: maximal-munch matching, skip rules,
// spans, construction-time validation, and parity with a
// hand-written lexer.

class RegexLexerTest {

    /** The test language's token kinds. Declaration order matters:
     * EQ is declared before EQEQ so only the longest match can pick
     * "==", and IF is declared before IDENT so the keyword wins the
     * tie at "if". */
    enum Kind {
        @Skip("\\s+") WS,
        @Skip("//[^\n]*") COMMENT,
        @Literal("=") EQ,
        @Literal("==") EQEQ,
        @Literal("if") IF,
        @Pattern("[a-zA-Z_][a-zA-Z0-9_]*") IDENT,
        @Pattern("\\d+(\\.\\d+)?") NUMBER,
        @Literal("+") PLUS,
        @Literal("-") MINUS,
        @Literal("/") DIV,
        @Literal("(") LPAREN,
        @Literal(")") RPAREN,
        EOF
    }

    /** Two identical patterns: the first declared must win. */
    enum TieKind {
        @Pattern("a+") FIRST,
        @Pattern("a+") SECOND,
        EOF
    }

    /** A kind with no rule annotation. */
    enum UnannotatedKind { NUMBER, EOF }

    /** A kind with both @Pattern and @Literal. */
    enum BothKind {
        @Pattern("a") @Literal("a") X,
        EOF
    }

    /** A kind with @Skip and @Pattern. */
    enum SkipPatternKind {
        @Skip("\\s+") @Pattern("a") X,
        EOF
    }

    /** A kind with @Skip and @Literal. */
    enum SkipLiteralKind {
        @Skip("\\s+") @Literal("a") X,
        EOF
    }

    /** A kind whose pattern matches the empty string. */
    enum EmptyPatternKind {
        @Pattern("x*") X,
        EOF
    }

    /** A kind with an empty literal. */
    enum EmptyLiteralKind {
        @Literal("") X,
        EOF
    }

    /** A kind with an invalid regex. */
    enum BadRegexKind {
        @Pattern("[") X,
        EOF
    }

    /** An EOF kind carrying a rule annotation. */
    enum AnnotatedEofKind {
        @Literal("x") X,
        @Pattern("e") EOF
    }

    /** Another enum, for the wrong-eof-kind test. */
    enum OtherKind {
        @Pattern("b") B,
        EOF
    }

    /** A hand-written lexer for the parity test: integers, +, -,
     * parens, spaces. */
    static final class HandLexer extends LexerBase<Kind> {
        HandLexer(String source, String text) { super(source, text, Kind.EOF); }

        @Override public Token<Kind> nextToken() {
            while (!eof() && peek() == ' ') advance();
            int startPos = pos, startLine = line, startCol = col;
            if (eof()) return new Token<>(eofKind, "", spanFrom(startPos, startLine, startCol));
            char c = peek();
            if (Character.isDigit(c)) {
                while (Character.isDigit(peek())) advance();
                return new Token<>(Kind.NUMBER, text.substring(startPos, pos),
                    spanFrom(startPos, startLine, startCol));
            }
            advance();
            Kind kind = switch (c) {
                case '+' -> Kind.PLUS;
                case '-' -> Kind.MINUS;
                case '(' -> Kind.LPAREN;
                case ')' -> Kind.RPAREN;
                default -> throw error("unexpected character '" + c + "'",
                    spanFrom(startPos, startLine, startCol));
            };
            return new Token<>(kind, String.valueOf(c), spanFrom(startPos, startLine, startCol));
        }
    }

    private static List<Token<Kind>> lex(String input) {
        return RegexLexer.of(Kind.class, "test", input, Kind.EOF).tokenize();
    }

    @Test
    void tokenizesNumbersOperatorsAndIdentifiers() {
        List<Token<Kind>> tokens = lex("if x1 + 12.5 - 3");
        assertEquals(List.of(Kind.IF, Kind.IDENT, Kind.PLUS, Kind.NUMBER,
            Kind.MINUS, Kind.NUMBER, Kind.EOF),
            tokens.stream().map(Token::kind).toList());
        assertEquals(List.of("if", "x1", "+", "12.5", "-", "3", ""),
            tokens.stream().map(Token::lexeme).toList());
        assertEquals(new Span("test", 0, 2, 1, 1), tokens.get(0).span());
        assertEquals(new Span("test", 8, 12, 1, 9), tokens.get(3).span());
    }

    @Test
    void longestMatchBeatsDeclarationOrder() {
        List<Token<Kind>> eqeq = lex("a == b");
        assertEquals(Kind.EQEQ, eqeq.get(1).kind());
        assertEquals("==", eqeq.get(1).lexeme());
        List<Token<Kind>> eq = lex("a = b");
        assertEquals(Kind.EQ, eq.get(1).kind());
        assertEquals("=", eq.get(1).lexeme());
    }

    @Test
    void keywordWinsTieAgainstIdentifier() {
        List<Token<Kind>> tokens = lex("if");
        assertEquals(Kind.IF, tokens.get(0).kind());
        assertEquals("if", tokens.get(0).lexeme());
    }

    @Test
    void longerIdentifierBeatsKeywordPrefix() {
        List<Token<Kind>> tokens = lex("ifx");
        assertEquals(Kind.IDENT, tokens.get(0).kind());
        assertEquals("ifx", tokens.get(0).lexeme());
    }

    @Test
    void declarationOrderBreaksTies() {
        List<Token<TieKind>> tokens =
            RegexLexer.of(TieKind.class, "test", "aaa", TieKind.EOF).tokenize();
        assertEquals(TieKind.FIRST, tokens.get(0).kind());
        assertEquals("aaa", tokens.get(0).lexeme());
    }

    @Test
    void skipsWhitespaceAndComments() {
        List<Token<Kind>> tokens = lex("1 // two\n+ 2 // done");
        assertEquals(List.of(Kind.NUMBER, Kind.PLUS, Kind.NUMBER, Kind.EOF),
            tokens.stream().map(Token::kind).toList());
        assertEquals(List.of("1", "+", "2", ""),
            tokens.stream().map(Token::lexeme).toList());
    }

    @Test
    void lineNumbersCorrectAfterSkippedNewlines() {
        List<Token<Kind>> comment = lex("1 // c\n2");
        assertEquals(new Span("test", 7, 8, 2, 1), comment.get(1).span());
        List<Token<Kind>> blank = lex("1\n\n2");
        assertEquals(new Span("test", 3, 4, 3, 1), blank.get(1).span());
    }

    @Test
    void commentBeatsShorterSlashToken() {
        List<Token<Kind>> tokens = lex("// hi\n/ 2");
        assertEquals(List.of(Kind.DIV, Kind.NUMBER, Kind.EOF),
            tokens.stream().map(Token::kind).toList());
        assertEquals(List.of("/", "2", ""),
            tokens.stream().map(Token::lexeme).toList());
        List<Token<Kind>> onlyComment = lex("// hi");
        assertEquals(1, onlyComment.size());
        assertEquals(Kind.EOF, onlyComment.get(0).kind());
    }

    @Test
    void eofTokenHasEmptyLexemeAndEndSpan() {
        List<Token<Kind>> tokens = lex("1");
        assertEquals(Kind.EOF, tokens.get(1).kind());
        assertEquals("", tokens.get(1).lexeme());
        assertEquals(new Span("test", 1, 1, 1, 2), tokens.get(1).span());
        List<Token<Kind>> empty = lex("");
        assertEquals(1, empty.size());
        assertEquals(new Span("test", 0, 0, 1, 1), empty.get(0).span());
        List<Token<Kind>> onlySpace = lex("   ");
        assertEquals(1, onlySpace.size());
        assertEquals(new Span("test", 3, 3, 1, 4), onlySpace.get(0).span());
    }

    @Test
    void unannotatedKindRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(UnannotatedKind.class, "test", "a", UnannotatedKind.EOF));
        assertTrue(error.getMessage().contains("NUMBER"));
    }

    @Test
    void patternAndLiteralRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(BothKind.class, "test", "a", BothKind.EOF));
        assertTrue(error.getMessage().contains("X"));
    }

    @Test
    void skipAndPatternRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(SkipPatternKind.class, "test", "a", SkipPatternKind.EOF));
        assertTrue(error.getMessage().contains("X"));
    }

    @Test
    void skipAndLiteralRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(SkipLiteralKind.class, "test", "a", SkipLiteralKind.EOF));
        assertTrue(error.getMessage().contains("X"));
    }

    @Test
    void emptyMatchingPatternRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(EmptyPatternKind.class, "test", "a", EmptyPatternKind.EOF));
        assertTrue(error.getMessage().contains("X"));
    }

    @Test
    void emptyLiteralRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(EmptyLiteralKind.class, "test", "a", EmptyLiteralKind.EOF));
        assertTrue(error.getMessage().contains("X"));
    }

    @Test
    void invalidRegexRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(BadRegexKind.class, "test", "a", BadRegexKind.EOF));
        assertTrue(error.getMessage().contains("X"));
    }

    /** An unchecked cast that erases to no runtime check, so a
     * test can pass a value of the wrong type to a generic
     * factory and assert the factory rejects it itself. */
    @SuppressWarnings("unchecked")
    private static <T> T coerce(Object value) {
        return (T) value;
    }

    @Test
    void nonEnumKindClassRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(coerce(String.class), "test", "a", Kind.EOF));
        assertTrue(error.getMessage().contains("not an enum"));
    }

    @Test
    void eofKindFromAnotherEnumRejected() throws Exception {
        // Generics forbid passing another enum's constant at compile
        // time, so the factory is invoked reflectively on its erased
        // signature (Class, String, String, Enum); the factory's own
        // validation must reject it.
        var method = RegexLexer.class.getMethod(
            "of", Class.class, String.class, String.class, Enum.class);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
            () -> method.invoke(null, Kind.class, "test", "a", OtherKind.EOF));
        assertTrue(thrown.getCause() instanceof IllegalArgumentException);
        assertTrue(thrown.getCause().getMessage().contains("not a constant"));
    }

    @Test
    void annotatedEofKindRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> RegexLexer.of(AnnotatedEofKind.class, "test", "a", AnnotatedEofKind.EOF));
        assertTrue(error.getMessage().contains("EOF"));
    }

    @Test
    void unexpectedCharacterReportsSpan() {
        RegexLexer<Kind> lexer = RegexLexer.of(Kind.class, "test", "1 @ 2", Kind.EOF);
        lexer.nextToken();
        LangError error = assertThrows(LangError.class, lexer::nextToken);
        assertEquals("unexpected character '@'", error.diag.message());
        assertEquals(new Span("test", 2, 3, 1, 3), error.diag.span());
    }

    @Test
    void unexpectedCharacterAfterNewlineReportsLine() {
        RegexLexer<Kind> lexer = RegexLexer.of(Kind.class, "test", "1\n@", Kind.EOF);
        lexer.nextToken();
        LangError error = assertThrows(LangError.class, lexer::nextToken);
        assertEquals("unexpected character '@'", error.diag.message());
        assertEquals(new Span("test", 2, 3, 2, 1), error.diag.span());
    }

    @Test
    void matchesHandWrittenLexer() {
        String input = "12 + 3 - (4)";
        List<Token<Kind>> annotated = lex(input);
        List<Token<Kind>> hand = new HandLexer("test", input).tokenize();
        assertEquals(hand, annotated);
    }
}
