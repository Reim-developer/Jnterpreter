# Jnterpreter

A toolkit for building tree-walking interpreters in Java.

## Quick start

```sh
git clone https://github.com/Reim-developer/Jnterpreter.git
cd Jnterpreter
./gradlew build
```

Requires JDK 21.

Jnterpreter is a toolkit, not a framework: it owns the reusable parts
(spans and pretty errors, the value domain, lexical scopes, Pratt
expression parsing, a REPL, and Hindley-Milner type inference) and
never owns your AST or evaluator.

```java
import com.reim.jnterpreter.diagnostic.LangError;
import com.reim.jnterpreter.lexer.LexerBase;
import com.reim.jnterpreter.parser.Parser;
import com.reim.jnterpreter.repl.Repl;
import com.reim.jnterpreter.runtime.Env;
import com.reim.jnterpreter.runtime.Value;

// 1. Tokenize with your own lexer (subclass LexerBase).
// 2. Parse expressions with Pratt parsing (Expr is your own AST type).
Parser<Expr> parser = new Parser<>(tokens);
parser.prefix("NUMBER", (p, tok) ->
    new Expr.Lit(new Value.Num(Double.parseDouble(tok.lexeme())), tok.span()));
Expr expr = parser.parseExpression();

// 3. Evaluate with lexical scopes; errors carry spans.
Env env = new Env();
env.define("x", new Value.Num(5));
Value x = env.get("x", span);

// 4. Errors render Rust-style with source context.
try {
    Value result = evaluate(expr, env);
} catch (LangError e) {
    System.out.print(e.diag.render(sourceText));
}

// 5. A REPL reads lines, evaluates them, and prints results.
new Repl(this::evalLine,
    new InputStreamReader(System.in), new OutputStreamWriter(System.out)).run();
```

## Type inference

The `jnterpreter-inference` module adds Hindley-Milner type
inference for your language (implemented in Kotlin; the public API
is Java-friendly). You write the walk over your own AST; the engine
owns unification, generalization, and instantiation. Type errors are
`LangError`, so they print through the same path as parse and
evaluation errors.

```java
import com.reim.jnterpreter.diagnostic.Span;
import com.reim.jnterpreter.inference.Checker;
import com.reim.jnterpreter.inference.Scheme;
import com.reim.jnterpreter.inference.Type;
import com.reim.jnterpreter.inference.TypeEnv;
import java.util.Set;

Span span = new Span("demo", 0, 1, 1, 1);
Checker checker = new Checker();
TypeEnv env = new TypeEnv();
Type.Var a = checker.freshVar();
env.define("id", new Scheme(Type.fun(a, a), Set.of(a)));
Type idType = checker.instantiate(env.lookup("id", span));
checker.unify(idType, Type.fun(Type.NUM, Type.NUM), span, "id used at num");
```

Records are closed: a record type is a fixed set of named
fields, and two records unify only when their field sets
match exactly. Projection is the author's walk: look the
field up in a known record type, or unify an unknown record
type with a single-field record.

```java
import com.reim.jnterpreter.inference.Type;
import java.util.Map;

Type point = Type.record(Map.of("x", Type.NUM, "y", Type.NUM));
```

A polymorphic accessor like `fn(r) => r.x` applied to a
record with extra fields does not type-check - that needs
row polymorphism, which is not in this release.

## License

Licensed under either of Apache License, Version 2.0 or MIT license, at
your option (see LICENSE-APACHE and LICENSE-MIT).

Jnterpreter is not yet published to Maven Central.
