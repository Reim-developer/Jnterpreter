package io.github.reimdeveloper.jnterpreter;

// Mini -- a tiny language built ON the Jnterpreter toolkit, proving the API.
//
// The language: numbers, strings, booleans, nil, let, fn (closures), if,
// blocks, calls, binary/unary ops, print. Grammar in ~150 lines because the
// toolkit owns spans, errors, environments and Pratt parsing; this file
// owns only tokens, the AST, statement parsing and evaluation semantics.
//
// Test fixture, not library code: it lives in src/test/java and exists so
// MiniTest can exercise the toolkit end to end.

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class Mini {

    // ---- AST: the language's own types. Every node carries a Span. ----

    sealed interface Expr permits Expr.Lit, Expr.Var, Expr.Binary, Expr.Unary,
        Expr.Call, Expr.FnExpr {
        Span span();
        record Lit(Value v, Span span) implements Expr {}
        record Var(String name, Span span) implements Expr {}
        record Binary(String op, Expr l, Expr r, Span span) implements Expr {}
        record Unary(String op, Expr e, Span span) implements Expr {}
        record Call(Expr callee, List<Expr> args, Span span) implements Expr {}
        record FnExpr(List<String> params, List<Stmt> body, Span span) implements Expr {}
    }

    sealed interface Stmt permits Stmt.Let, Stmt.ExprStmt, Stmt.Block, Stmt.If {
        Span span();
        record Let(String name, Span nameSpan, Expr value, Span span) implements Stmt {}
        record ExprStmt(Expr e, Span span) implements Stmt {}
        record Block(List<Stmt> stmts, Span span) implements Stmt {}
        record If(Expr cond, List<Stmt> then, List<Stmt> els, Span span) implements Stmt {}
    }

    // ---- Lexer: extends the framework base; only token rules are local. ----
    // For STRING tokens the lexeme is the COOKED value (escapes resolved);
    // for everything else it is the raw text.

    static final class MiniLexer extends LexerBase {
        private static final Map<String, String> KEYWORDS = Map.of(
            "let", "LET", "fn", "FN", "if", "IF", "else", "ELSE",
            "true", "TRUE", "false", "FALSE", "nil", "NIL");

        MiniLexer(String source, String text) { super(source, text); }

        @Override public Token nextToken() {
            while (!eof()) {
                char c = peek();
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') { advance(); continue; }
                if (c == '/' && peekAt(1) == '/') {
                    while (!eof() && peek() != '\n') advance();
                    continue;
                }
                break;
            }
            int startPos = pos, startLine = line, startCol = col;
            if (eof()) return new Token("EOF", "", spanFrom(startPos, startLine, startCol));
            char c = advance();
            if (Character.isDigit(c)) {
                while (Character.isDigit(peek())) advance();
                if (peek() == '.' && Character.isDigit(peekAt(1))) {
                    advance();
                    while (Character.isDigit(peek())) advance();
                }
                return new Token("NUMBER", text.substring(startPos, pos),
                    spanFrom(startPos, startLine, startCol));
            }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                while (!eof() && peek() != '"') {
                    char ch = advance();
                    if (ch == '\\') {
                        char e = advance();
                        switch (e) {
                            case 'n' -> sb.append('\n');
                            case 't' -> sb.append('\t');
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            default -> sb.append(e);
                        }
                    } else {
                        sb.append(ch);
                    }
                }
                if (eof()) {
                    throw error("unterminated string", spanFrom(startPos, startLine, startCol));
                }
                advance(); // closing quote
                return new Token("STRING", sb.toString(),
                    spanFrom(startPos, startLine, startCol));
            }
            if (Character.isLetter(c) || c == '_') {
                while (Character.isLetterOrDigit(peek()) || peek() == '_') advance();
                String word = text.substring(startPos, pos);
                String kind = KEYWORDS.getOrDefault(word, "IDENT");
                return new Token(kind, word, spanFrom(startPos, startLine, startCol));
            }
            String two = "" + c + peek();
            if (two.equals("==") || two.equals("!=") || two.equals("<=")
                || two.equals(">=") || two.equals("&&") || two.equals("||")) {
                advance();
                return new Token(two, two, spanFrom(startPos, startLine, startCol));
            }
            if ("+-*/<> =!(){},;".indexOf(c) >= 0) {
                return new Token(String.valueOf(c), String.valueOf(c),
                    spanFrom(startPos, startLine, startCol));
            }
            throw error("unexpected character '" + c + "'",
                spanFrom(startPos, startLine, startCol));
        }
    }

    // ---- Parser: statements are local; expressions use the framework Pratt parser. ----

    static final class MiniParser {
        private final Parser<Expr> p;

        MiniParser(List<Token> tokens) {
            p = new Parser<>(tokens);
            p.prefix("NUMBER", (pp, tok) -> new Expr.Lit(
                new Value.Num(Double.parseDouble(tok.lexeme())), tok.span()));
            p.prefix("STRING", (pp, tok) -> new Expr.Lit(
                new Value.Str(tok.lexeme()), tok.span()));
            p.prefix("TRUE", (pp, tok) -> new Expr.Lit(
                new Value.Bool(true), tok.span()));
            p.prefix("FALSE", (pp, tok) -> new Expr.Lit(
                new Value.Bool(false), tok.span()));
            p.prefix("NIL", (pp, tok) -> new Expr.Lit(
                new Value.Nil(), tok.span()));
            p.prefix("IDENT", (pp, tok) -> new Expr.Var(tok.lexeme(), tok.span()));
            p.prefix("(", (pp, tok) -> {
                Expr e = pp.parseExpression();
                pp.expect(")", "')'");
                return e;
            });
            p.prefix("-", (pp, tok) -> new Expr.Unary("-", pp.parseExpression(7), tok.span()));
            p.prefix("!", (pp, tok) -> new Expr.Unary("!", pp.parseExpression(7), tok.span()));
            p.prefix("FN", (pp, tok) -> {
                pp.expect("(", "'(' after fn");
                List<String> params = new ArrayList<>();
                if (!pp.match(")")) {
                    params.add(pp.expect("IDENT", "parameter name").lexeme());
                    while (pp.match(",")) {
                        params.add(pp.expect("IDENT", "parameter name").lexeme());
                    }
                    pp.expect(")", "')' after parameters");
                }
                List<Stmt> body = parseBlock();
                return new Expr.FnExpr(params, body, tok.span());
            });
            binary("||", 1);
            binary("&&", 2);
            binary("==", 3);
            binary("!=", 3);
            binary("<", 4);
            binary("<=", 4);
            binary(">", 4);
            binary(">=", 4);
            binary("+", 5);
            binary("-", 5);
            binary("*", 6);
            binary("/", 6);
            p.infix("(", new InfixParselet<Expr>() {
                @Override public int precedence() { return 8; }
                @Override public Expr parse(Parser<Expr> pp, Expr left, Token tok) {
                    List<Expr> args = new ArrayList<>();
                    if (!pp.match(")")) {
                        args.add(pp.parseExpression());
                        while (pp.match(",")) args.add(pp.parseExpression());
                        pp.expect(")", "')' after arguments");
                    }
                    return new Expr.Call(left, args, tok.span());
                }
            });
        }

        private void binary(String op, int prec) {
            p.infix(op, new InfixParselet<Expr>() {
                @Override public int precedence() { return prec; }
                @Override public Expr parse(Parser<Expr> pp, Expr left, Token tok) {
                    return new Expr.Binary(op, left, pp.parseExpression(prec + 1), tok.span());
                }
            });
        }

        List<Stmt> parseProgram() {
            List<Stmt> out = new ArrayList<>();
            while (!p.eof()) out.add(parseStatement());
            return out;
        }

        List<Stmt> parseBlock() {
            p.expect("{", "'{'");
            List<Stmt> out = new ArrayList<>();
            while (!p.peek().kind().equals("}")) {
                if (p.eof()) {
                    throw new LangError(
                        "expected '}', got end of input", p.peek().span());
                }
                out.add(parseStatement());
            }
            p.expect("}", "'}'");
            return out;
        }

        /** ';' is required between statements, optional before '}' or EOF. */
        private void endStmt() {
            if (p.match(";")) return;
            if (p.peek().kind().equals("}") || p.eof()) return;
            throw new LangError("expected ';'", p.peek().span());
        }

        Stmt parseStatement() {
            if (p.match("LET")) {
                Token name = p.expect("IDENT", "variable name after 'let'");
                p.expect("=", "'=' in let");
                Expr value = p.parseExpression();
                endStmt();
                return new Stmt.Let(name.lexeme(), name.span(), value, name.span());
            }
            if (p.match("IF")) {
                p.expect("(", "'(' after if");
                Expr cond = p.parseExpression();
                p.expect(")", "')' after condition");
                List<Stmt> then = parseBlock();
                List<Stmt> els = null;
                if (p.match("ELSE")) els = parseBlock();
                return new Stmt.If(cond, then, els, cond.span());
            }
            if (p.peek().kind().equals("{")) {
                Token open = p.peek();
                return new Stmt.Block(parseBlock(), open.span());
            }
            Expr e = p.parseExpression();
            endStmt();
            return new Stmt.ExprStmt(e, e.span());
        }
    }

    // ---- Evaluator: tree-walking over the sealed AST. Exhaustive switches. ----

    static Value evalExpr(Expr e, Env env) {
        if (e instanceof Expr.Lit lit) return lit.v();
        if (e instanceof Expr.Var var) return env.get(var.name(), var.span());
        if (e instanceof Expr.Unary u) {
            Value v = evalExpr(u.e(), env);
            if (u.op().equals("-")) {
                if (v instanceof Value.Num n) return new Value.Num(-n.v());
                throw new LangError("expected number after '-'", u.span());
            }
            if (v instanceof Value.Bool b) return new Value.Bool(!b.v());
            throw new LangError("expected boolean after '!'", u.span());
        }
        if (e instanceof Expr.Binary b) return evalBinary(b, env);
        if (e instanceof Expr.Call call) {
            Value callee = evalExpr(call.callee(), env);
            List<Value> args = new ArrayList<>();
            for (Expr arg : call.args()) args.add(evalExpr(arg, env));
            if (callee instanceof Value.Fn fn) {
                if (args.size() != fn.params().size()) {
                    throw new LangError("expected " + fn.params().size()
                        + " arguments, got " + args.size(), call.span());
                }
                return fn.body().apply(args);
            }
            if (callee instanceof Value.NativeFn fn) {
                if (args.size() != fn.arity()) {
                    throw new LangError("expected " + fn.arity()
                        + " arguments, got " + args.size(), call.span());
                }
                return fn.body().apply(args);
            }
            throw new LangError("not callable: " + callee, call.span());
        }
        if (e instanceof Expr.FnExpr fn) {
            Env closure = env;
            return new Value.Fn("anonymous", fn.params(), args -> {
                Env callEnv = closure.child();
                for (int i = 0; i < fn.params().size(); i++) {
                    callEnv.define(fn.params().get(i), args.get(i));
                }
                return evalBlock(fn.body(), callEnv);
            });
        }
        throw new AssertionError("unreachable: " + e);
    }

    static Value evalBinary(Expr.Binary b, Env env) {
        // Short-circuit first: the right side may not even be evaluated.
        if (b.op().equals("&&")) {
            Value l = evalExpr(b.l(), env);
            if (!(l instanceof Value.Bool lb)) {
                throw new LangError("expected boolean before '&&'", b.span());
            }
            if (!lb.v()) return new Value.Bool(false);
            Value r = evalExpr(b.r(), env);
            if (!(r instanceof Value.Bool rb)) {
                throw new LangError("expected boolean after '&&'", b.span());
            }
            return new Value.Bool(rb.v());
        }
        if (b.op().equals("||")) {
            Value l = evalExpr(b.l(), env);
            if (!(l instanceof Value.Bool lb)) {
                throw new LangError("expected boolean before '||'", b.span());
            }
            if (lb.v()) return new Value.Bool(true);
            Value r = evalExpr(b.r(), env);
            if (!(r instanceof Value.Bool rb)) {
                throw new LangError("expected boolean after '||'", b.span());
            }
            return new Value.Bool(rb.v());
        }
        Value l = evalExpr(b.l(), env);
        Value r = evalExpr(b.r(), env);
        if (b.op().equals("==")) return new Value.Bool(Objects.equals(l, r));
        if (b.op().equals("!=")) return new Value.Bool(!Objects.equals(l, r));
        if (b.op().equals("+") && l instanceof Value.Str ls
            && r instanceof Value.Str rs) {
            return new Value.Str(ls.v() + rs.v());
        }
        if (l instanceof Value.Num ln && r instanceof Value.Num rn) {
            return switch (b.op()) {
                case "+" -> new Value.Num(ln.v() + rn.v());
                case "-" -> new Value.Num(ln.v() - rn.v());
                case "*" -> new Value.Num(ln.v() * rn.v());
                case "/" -> new Value.Num(ln.v() / rn.v());
                case "<" -> new Value.Bool(ln.v() < rn.v());
                case "<=" -> new Value.Bool(ln.v() <= rn.v());
                case ">" -> new Value.Bool(ln.v() > rn.v());
                case ">=" -> new Value.Bool(ln.v() >= rn.v());
                default -> throw new LangError("unknown operator '" + b.op() + "'", b.span());
            };
        }
        throw new LangError(
            "expected numbers for '" + b.op() + "', got " + l + " and " + r, b.span());
    }

    static Value evalBlock(List<Stmt> stmts, Env env) {
        Value v = new Value.Nil();
        for (Stmt s : stmts) v = evalStmt(s, env);
        return v;
    }

    static Value evalStmt(Stmt s, Env env) {
        if (s instanceof Stmt.Let let) {
            env.define(let.name(), evalExpr(let.value(), env));
            return new Value.Nil();
        }
        if (s instanceof Stmt.ExprStmt es) return evalExpr(es.e(), env);
        if (s instanceof Stmt.Block block) return evalBlock(block.stmts(), env.child());
        if (s instanceof Stmt.If ifs) {
            Value cond = evalExpr(ifs.cond(), env);
            if (!(cond instanceof Value.Bool b)) {
                throw new LangError("if condition must be boolean", ifs.cond().span());
            }
            if (b.v()) return evalBlock(ifs.then(), env.child());
            if (ifs.els() == null) return new Value.Nil();
            return evalBlock(ifs.els(), env.child());
        }
        throw new AssertionError("unreachable: " + s);
    }

    static Value evalProgram(List<Stmt> program, Env env) {
        Value v = new Value.Nil();
        for (Stmt s : program) v = evalStmt(s, env);
        return v;
    }

    // ---- Session: a persistent global env + captured output. ----

    static final class Session {
        final Env global = new Env();
        final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(outBytes);

        Session() {
            global.define("print", new Value.NativeFn("print", 1, args -> {
                out.println(args.get(0));
                return new Value.Nil();
            }));
        }

        Value eval(String text, String name) {
            List<Token> tokens = new MiniLexer(name, text).tokenize();
            List<Stmt> program = new MiniParser(tokens).parseProgram();
            return evalProgram(program, global);
        }

        Value evalLine(String line) { return eval(line, "repl"); }

        String output() {
            out.flush();
            return outBytes.toString(StandardCharsets.UTF_8);
        }
    }
}
