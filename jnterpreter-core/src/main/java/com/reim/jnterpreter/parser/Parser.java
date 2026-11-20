package com.reim.jnterpreter.parser;
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.lexer.Token;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** A Pratt parser for expressions: it owns precedence climbing, and
 * the language supplies a prefix parselet for each token kind that can
 * start an expression and an infix parselet for each kind that can
 * continue one.
 *
 * @param <E> the language's expression (AST) type
 * @param <K> the language's token kind type
 */
public final class Parser<E, K> {
    private final List<Token<K>> tokens;
    private final K eofKind;
    private int pos = 0;
    private final Map<K, PrefixParselet<E, K>> prefix = new HashMap<>();
    private final Map<K, InfixParselet<E, K>> infix = new HashMap<>();

    /**
     * @param tokens  the tokens to parse, ending with an EOF token
     * @param eofKind the kind that means end of input
     */
    public Parser(List<Token<K>> tokens, K eofKind) {
        this.tokens = tokens;
        this.eofKind = eofKind;
    }

    /**
     * Builds a parser from annotated parselet methods: each public
     * method of {@code parseletsClass} annotated with {@link Prefix}
     * or {@link Infix} becomes the parselet for the kind its
     * annotation names. The class is instantiated once through its
     * public no-arg constructor; methods are processed in name
     * order, so registration errors are deterministic. This is sugar
     * over {@link #prefix(Object, PrefixParselet)} and
     * {@link #infix(Object, InfixParselet)}: the returned parser
     * accepts further manual registration, so annotated and explicit
     * parselets can be mixed. A class with no annotated methods
     * yields a parser with no parselets, the same as a parser built
     * with the constructor and nothing registered.
     *
     * <p>Kind names are strings because Java annotations cannot hold
     * a generic kind value; {@code kinds} resolves each name to a
     * kind. For an enum kind type, pass {@code Kind::valueOf}; for
     * string kinds, pass the identity function.
     *
     * <p>Example:
     * <pre>{@code
     * enum Kind { NUMBER, PLUS, EOF }
     *
     * class Arithmetic {
     *     @Prefix("NUMBER")
     *     public Expr number(Parser<Expr, Kind> p, Token<Kind> t) {
     *         return new Expr.Num(Double.parseDouble(t.lexeme()));
     *     }
     *
     *     @Infix(value = "PLUS", precedence = 1)
     *     public Expr plus(Parser<Expr, Kind> p, Expr left, Token<Kind> t) {
     *         return new Expr.Bin("+", left, p.parseExpression(2));
     *     }
     * }
     *
     * Parser<Expr, Kind> parser =
     *     Parser.from(Arithmetic.class, Kind::valueOf, tokens, Kind.EOF);
     * }</pre>
     *
     * <p>A prefix method takes {@code (Parser<E, K>, Token<K>)} and
     * returns {@code E}; an infix method takes
     * {@code (Parser<E, K>, E, Token<K>)} and returns {@code E}. The
     * middle infix parameter is the expression type and cannot be
     * checked at registration (erasure); a mismatch fails when the
     * parselet runs, not when the parser is built. An exception a
     * parselet method throws propagates as-is when it is unchecked;
     * a checked exception is wrapped in an
     * {@link IllegalStateException}.
     *
     * @param parseletsClass the class holding the annotated methods
     * @param kinds          resolves annotation names to kinds
     * @param tokens         the tokens to parse, ending with an EOF token
     * @param eofKind        the kind that means end of input
     * @param <E> the language's expression (AST) type
     * @param <K> the language's token kind type
     * @return the parser, with the annotated parselets registered
     * @throws NullPointerException if parseletsClass or kinds is null
     * @throws IllegalArgumentException if the class has no public
     *         no-arg constructor; an annotated method is not a public
     *         instance method, has the wrong parameters, or returns
     *         void; a method carries both annotations; two methods
     *         claim the same kind in the same position; or the kind
     *         resolver fails or returns null
     */
    public static <E, K> Parser<E, K> from(
            Class<?> parseletsClass,
            Function<String, K> kinds,
            List<Token<K>> tokens,
            K eofKind) {
        Objects.requireNonNull(parseletsClass, "parseletsClass");
        Objects.requireNonNull(kinds, "kinds");
        Object instance = newInstance(parseletsClass);
        Parser<E, K> parser = new Parser<>(tokens, eofKind);
        registerAnnotated(parser, parseletsClass, kinds, instance);
        return parser;
    }

    private static <E, K> void registerAnnotated(
            Parser<E, K> parser,
            Class<?> parseletsClass,
            Function<String, K> kinds,
            Object instance) {
        failOnNonPublicAnnotated(parseletsClass);
        Method[] methods = parseletsClass.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)
            .thenComparingInt(m -> m.getParameterTypes().length));
        for (Method m : methods) {
            if (m.isSynthetic()) continue;
            Prefix prefix = m.getAnnotation(Prefix.class);
            Infix infix = m.getAnnotation(Infix.class);
            if (prefix == null && infix == null) continue;
            if (prefix != null && infix != null) {
                throw new IllegalArgumentException("method " + describe(m)
                    + " is annotated with both @Prefix and @Infix");
            }
            checkNotStatic(m);
            // The method is public, but its class may not be (a
            // package-private or nested class); make it callable.
            m.setAccessible(true);
            if (prefix != null) {
                checkPrefixSignature(m);
                K kind = resolve(kinds, prefix.value(), m);
                if (parser.prefix.containsKey(kind)) {
                    throw new IllegalArgumentException("duplicate @Prefix for kind '"
                        + prefix.value() + "': method " + describe(m));
                }
                parser.prefix.put(kind, (p, tok) -> invoke(m, instance, p, tok));
            } else {
                checkInfixSignature(m);
                K kind = resolve(kinds, infix.value(), m);
                if (parser.infix.containsKey(kind)) {
                    throw new IllegalArgumentException("duplicate @Infix for kind '"
                        + infix.value() + "': method " + describe(m));
                }
                int precedence = infix.precedence();
                parser.infix.put(kind, new InfixParselet<E, K>() {
                    @Override public int precedence() { return precedence; }
                    @Override public E parse(Parser<E, K> p, E left, Token<K> tok) {
                        return invoke(m, instance, p, left, tok);
                    }
                });
            }
        }
    }

    // getMethods only returns public methods, so an annotated
    // non-public method would be silently skipped without this walk.
    // The walk covers superclasses too: an annotated non-public
    // method inherited from a base class fails just as loudly.
    private static void failOnNonPublicAnnotated(Class<?> parseletsClass) {
        for (Class<?> c = parseletsClass; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                if (m.getAnnotation(Prefix.class) == null
                        && m.getAnnotation(Infix.class) == null) continue;
                if (!Modifier.isPublic(m.getModifiers())) {
                    throw new IllegalArgumentException("method " + describe(m)
                        + " is annotated but not public");
                }
            }
        }
    }

    private static Object newInstance(Class<?> parseletsClass) {
        Constructor<?> ctor;
        try {
            ctor = parseletsClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("parselets class "
                + parseletsClass.getName() + " has no no-arg constructor", e);
        }
        if (!Modifier.isPublic(ctor.getModifiers())) {
            throw new IllegalArgumentException("parselets class "
                + parseletsClass.getName()
                + " has a no-arg constructor, but it is not public");
        }
        try {
            // The constructor is public, but the class itself may
            // not be (a package-private or local class); make it
            // callable.
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot instantiate parselets class "
                + parseletsClass.getName(), e);
        }
    }

    private static void checkNotStatic(Method m) {
        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException("method " + describe(m)
                + " is annotated but static; parselet methods must be instance methods");
        }
    }

    private static void checkPrefixSignature(Method m) {
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 2) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Prefix must take 2 parameters (Parser, Token), has "
                + params.length);
        }
        if (params[0] != Parser.class) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Prefix: parameter 0 must be Parser, is "
                + params[0].getName());
        }
        if (params[1] != Token.class) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Prefix: parameter 1 must be Token, is "
                + params[1].getName());
        }
        if (m.getReturnType() == void.class) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Prefix must return an expression, not void");
        }
    }

    private static void checkInfixSignature(Method m) {
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 3) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Infix must take 3 parameters (Parser, E, Token), has "
                + params.length);
        }
        if (params[0] != Parser.class) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Infix: parameter 0 must be Parser, is "
                + params[0].getName());
        }
        if (params[2] != Token.class) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Infix: parameter 2 must be Token, is "
                + params[2].getName());
        }
        if (m.getReturnType() == void.class) {
            throw new IllegalArgumentException("method " + describe(m)
                + " annotated with @Infix must return an expression, not void");
        }
    }

    private static <K> K resolve(Function<String, K> kinds, String name, Method m) {
        K kind;
        try {
            kind = kinds.apply(name);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cannot resolve kind name '" + name
                + "' for method " + describe(m), e);
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind resolver returned null for name '"
                + name + "' for method " + describe(m));
        }
        return kind;
    }

    // The cast is unchecked because the method's return type is E by
    // the contract validated at registration, and erasure leaves no
    // runtime check: a method returning the wrong type fails at the
    // call site with a ClassCastException, not here.
    @SuppressWarnings("unchecked")
    private static <E, K> E invoke(
            Method m, Object instance, Parser<E, K> p, Token<K> tok) {
        try {
            return (E) m.invoke(instance, p, tok);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot call parselet method " + describe(m), e);
        } catch (InvocationTargetException e) {
            throw unwrap(m, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E, K> E invoke(
            Method m, Object instance, Parser<E, K> p, E left, Token<K> tok) {
        try {
            return (E) m.invoke(instance, p, left, tok);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot call parselet method " + describe(m), e);
        } catch (InvocationTargetException e) {
            throw unwrap(m, e);
        }
    }

    // Unchecked exceptions propagate as-is (a LangError thrown by a
    // parselet method keeps its type and diagnostic); a checked
    // exception has no type to propagate as, so it is wrapped.
    private static RuntimeException unwrap(Method m, InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException(
            "parselet method " + describe(m) + " threw a checked exception", cause);
    }

    private static String describe(Method m) {
        return m.getDeclaringClass().getSimpleName() + "." + m.getName();
    }

    /**
     * Registers the parselet that parses {@code kind} at the start of
     * an expression.
     *
     * @param kind the token kind
     * @param p    the parselet
     */
    public void prefix(K kind, PrefixParselet<E, K> p) { prefix.put(kind, p); }

    /**
     * Registers the parselet that parses {@code kind} after a left-hand
     * expression.
     *
     * @param kind the token kind
     * @param p    the parselet
     */
    public void infix(K kind, InfixParselet<E, K> p) { infix.put(kind, p); }

    /** The next token without consuming it; the EOF token at end of input. */
    public Token<K> peek() { return tokens.get(Math.min(pos, tokens.size() - 1)); }

    /** Consumes and returns the next token. */
    public Token<K> advance() {
        Token<K> t = peek();
        if (pos < tokens.size() - 1) pos++;
        return t;
    }

    /**
     * Consumes the next token if it has the given kind.
     *
     * @param kind the expected kind
     * @return whether the token matched and was consumed
     */
    public boolean match(K kind) {
        if (peek().kind().equals(kind)) { advance(); return true; }
        return false;
    }

    /**
     * Consumes the next token, which must have the given kind.
     *
     * @param kind the expected kind
     * @param what a description of the expected token, for the error
     * @return the consumed token
     * @throws LangError if the next token has another kind
     */
    public Token<K> expect(K kind, String what) {
        if (peek().kind().equals(kind)) return advance();
        throw new LangError(
            "expected " + what + ", got '" + peek().lexeme() + "'", peek().span());
    }
    /** Whether the next token is the EOF token. */
    public boolean eof() { return peek().kind().equals(eofKind); }

    /**
     * Parses an expression, consuming operators with precedence at least
     * {@code minPrecedence}. A call with 0 parses a full expression.
     * Infix parselets parse their right side at precedence + 1 for
     * left-associative operators, or at the same precedence for
     * right-associative ones.
     *
     * @param minPrecedence the lowest operator precedence to consume
     * @return the parsed expression
     * @throws LangError if the next token cannot start an expression
     */
    public E parseExpression(int minPrecedence) {
        Token<K> tok = advance();
        PrefixParselet<E, K> pre = prefix.get(tok.kind());
        if (pre == null) {
            throw new LangError(
                "unexpected token '" + tok.lexeme() + "'", tok.span());
        }
        E left = pre.parse(this, tok);
        while (true) {
            InfixParselet<E, K> inf = infix.get(peek().kind());
            if (inf == null || inf.precedence() < minPrecedence) break;
            Token<K> op = advance();
            left = inf.parse(this, left, op);
        }
        return left;
    }

    /** Parses a full expression (precedence 0). */
    public E parseExpression() { return parseExpression(0); }
}
