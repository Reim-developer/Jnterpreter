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
expression parsing, a REPL) and never owns your AST or evaluator.

```java
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

// 5. A REPL is a function of (eval, reader, writer).
Repl.run(this::evalLine,
    new InputStreamReader(System.in), new OutputStreamWriter(System.out));
```

## License

Licensed under either of Apache License, Version 2.0 or MIT license, at
your option (see LICENSE-APACHE and LICENSE-MIT).

Jnterpreter is not yet published to Maven Central.
