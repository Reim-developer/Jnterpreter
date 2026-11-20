# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.7.0] - 2026-11-21

### Added

- `jnterpreter-core`: an annotation-driven lexer.
  `@Pattern`, `@Literal`, and `@Skip` on the constants of the
  kind enum, scanned by `RegexLexer.of`. Matching is maximal
  munch: the longest match wins, ties broken by declaration
  order. Skipped text (whitespace, comments) produces no token.
  The kind type must be an enum for this path; a hand-written
  `LexerBase` subclass stays available for lexers with rules
  annotations cannot express.

## [0.6.0] - 2026-11-21

### Added

- `jnterpreter-core`: parselet annotations - `@Prefix` and
  `@Infix` on methods, discovered by `Parser.from`. The
  annotation names the token kind as a string, resolved by a
  caller-supplied function (`Kind::valueOf` for enum kinds),
  because a Java annotation cannot hold a generic kind value.
  Explicit `prefix()`/`infix()` registration is unchanged, and
  the two can be mixed.
- `jnterpreter-core`: an error seam. `LangError` is no longer
  final, so a language can throw its own subtype from its own
  code, and `Repl` takes an `ErrorRenderer` that formats each
  diagnostic. The default renders `error: <message>` as before.

## [0.5.0] - 2026-11-21

### Changed

- **Breaking:** token kinds are now typed. `Token` is
  `Token<K>`, `LexerBase` is `LexerBase<K>`, `Parser` is
  `Parser<E, K>`, and both parselets take a `K` parameter.
  `LexerBase` and `Parser` take the language's EOF kind as a
  constructor argument. Migration:

  ```java
  // Before
  Token t = new Token("NUMBER", "1", span);
  Parser<Expr> p = new Parser(tokens);
  class MyLexer extends LexerBase { ... }

  // After
  enum Kind { NUMBER, EOF }
  Token<Kind> t = new Token<>(Kind.NUMBER, "1", span);
  Parser<Expr, Kind> p = new Parser<>(tokens, Kind.EOF);
  class MyLexer extends LexerBase<Kind> { ... }
  ```

  Error messages are unchanged.

## [0.4.0] - 2026-11-21

### Added

- `jnterpreter-inference`: `Checker.letrec`, a primitive
  for recursive bindings. The bound name is monomorphic
  inside its own value and polymorphic in the body.

## [0.3.0] - 2026-11-21

### Added

- `jnterpreter-inference`: closed record types - `Type.Rec`
  and the `Type.record` factory. Records unify field-wise
  with exact field sets; fields print sorted by name.
  Projection of a record with a known type works;
  projection through a polymorphic record type needs row
  polymorphism, which is not in this release.

## [0.2.0] - 2026-11-20

### Added

- `jnterpreter-inference`: Hindley-Milner type inference (Kotlin) -
  the `Type` algebra, `Checker` (fresh variables, unification with an
  occurs check, generalization, instantiation), `Scheme`, and
  `TypeEnv`. The author writes the walk over their own AST; the
  engine owns the algorithm. Type errors are `LangError`.
- Direct unit tests for the core module (diagnostics, scopes,
  parser, lexer, REPL, values).
- `Repl` gains a 4-argument constructor with a prompt; the
  3-argument constructor is unchanged.

### Changed

- JUnit test dependencies are declared once in the root build
  script for all modules.
- Gradle wrapper 9.9.2 -> 9.7.0: the newest Gradle the Kotlin
  Gradle plugin 2.4.21 supports.

### Removed

- Mini, the demo language, and the `jnterpreter-mini` module.
  Jnterpreter is a library, not a language.

## [0.1.0] - 2026-11-20

### Added

- The toolkit: spans and Rust-style diagnostics, the value domain,
  lexical scopes, Pratt expression parsing, and a REPL.
- `jnterpreter-core` module, package `com.reim.jnterpreter`.
- Mini, a demo language, as the end-to-end test fixture.
