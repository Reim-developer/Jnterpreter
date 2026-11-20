# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
