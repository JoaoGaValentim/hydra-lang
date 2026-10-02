package hydra.compiler.parser;

import hydra.compiler.ast.Ast;
import hydra.compiler.ast.Ast.*;
import hydra.compiler.lexer.Lexer;
import hydra.compiler.token.Token;
import hydra.compiler.token.TokenKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser recursivo descendente da EBNF congelada (ESPECIFICACAO §3).
 * Fim de instrução = newline (tokens de posição; o parser ignora newlines
 * dentro de expressões e trata fim de stmt quando o próximo token não
 * pode continuar a produção atual — lookahead limitado).
 */
public final class Parser {

    private final List<Token> toks;
    private final String[] lines;
    private int i;

    private Parser(List<Token> tokens, String source) {
        this.toks = tokens;
        this.lines = source == null ? new String[0] : source.split("\n", -1);
    }

    public static Unit parse(String source) {
        String src = source == null ? "" : source;
        List<Token> tokens = new Lexer(src).tokenize();
        for (Token t : tokens) {
            if (t.kind() == TokenKind.ERROR) {
                throw new SyntaxError(t.text(), t.line(), t.col(), "HYP010", hintForLexerError(t.text()));
            }
        }
        return new Parser(tokens, src).parseUnit();
    }

    private static String hintForLexerError(String msg) {
        if (msg.contains("';'")) {
            return "remova o ';' — em Hydra o fim de instrução é a newline";
        }
        if (msg.contains("string não fechada")) {
            return "feche a string com aspas duplas na mesma linha (sem interpolação)";
        }
        if (msg.contains("escape inválido")) {
            return "escapes válidos: \\n \\t \\\" \\\\";
        }
        return null;
    }

    // ---- helpers ----

    private Token peek() { return toks.get(i); }

    private boolean isWord(String w) {
        Token t = peek();
        return t.kind() == TokenKind.IDENT && w.equals(t.text());
    }

    private Token peek(int ahead) {
        int j = i + ahead;
        return j < toks.size() ? toks.get(j) : toks.get(toks.size() - 1);
    }

    private boolean check(TokenKind k) { return peek().kind() == k; }

    private boolean match(TokenKind k) {
        if (check(k)) {
            i++;
            return true;
        }
        return false;
    }

    private Token expect(TokenKind k, String what) {
        if (!check(k)) {
            Token t = peek();
            throw err("esperava " + what + ", achou " + describe(t), t, "HYP001", hintExpect(k, t, what));
        }
        return toks.get(i++);
    }

    private Token expectIdent(String what) {
        return expect(TokenKind.IDENT, what);
    }

    private SyntaxError err(String msg, Token t, String code, String suggestion) {
        String context = lineContext(t.line(), t.col());
        String full = context.isEmpty() ? msg : msg + "\n  " + context;
        return new SyntaxError(full, t.line(), t.col(), code, suggestion);
    }

    /** Trecho da linha de origem com marcador de coluna (para diagnóstico). */
    private String lineContext(int line, int col) {
        if (lines.length == 0 || line < 1 || line > lines.length) {
            return "";
        }
        String text = lines[line - 1];
        if (text.isBlank()) {
            return "";
        }
        String shown = text.length() > 120 ? text.substring(0, 117) + "..." : text;
        int caret = Math.max(1, Math.min(col, shown.length() + 1));
        return shown + "\n  " + " ".repeat(caret - 1) + "^";
    }

    private static String describe(Token t) {
        if (t.kind() == TokenKind.EOF) return "fim do arquivo";
        if (t.kind() == TokenKind.STRING) return "string \"" + t.text() + "\"";
        if (t.kind() == TokenKind.IDENT || t.kind() == TokenKind.INT || t.kind() == TokenKind.FLOAT) {
            return "'" + t.text() + "'";
        }
        return t.kind().name();
    }

    /** Sugestões para os erros mais comuns de migração/sintaxe. */
    private String hintExpect(TokenKind expected, Token found, String what) {
        if (found.kind() == TokenKind.EOF) {
            return "arquivo terminou antes de " + what + " — verifique chaves/parênteses não fechados";
        }
        if (expected == TokenKind.COLON && found.kind() == TokenKind.IDENT) {
            return "em Hydra parâmetros e campos usam 'nome: Tipo' (ex.: x: Int)";
        }
        if (expected == TokenKind.COLON && found.kind() != TokenKind.COLON) {
            return "esperava ':' após o nome (forma: nome: Tipo)";
        }
        if (expected == TokenKind.LPAREN && found.kind() == TokenKind.IDENT) {
            Token n = peek(1);
            if (n.kind() != TokenKind.LPAREN) {
                return "funções em Hydra não têm palavra-chave: escreva nome(params) { ... }";
            }
        }
        if (expected == TokenKind.RPAREN && found.kind() == TokenKind.LBRACE) {
            return "faltou fechar os parênteses dos parâmetros antes do corpo";
        }
        if (expected == TokenKind.LBRACE && found.kind() == TokenKind.COLON) {
            return "corpo de função precisa de '{ ... }' ou '= expr'";
        }
        if (expected == TokenKind.ARROW && found.kind() == TokenKind.IDENT && "in".equals(found.text())) {
            return "for-in: for (val x in colecao) { ... }";
        }
        if (found.kind() == TokenKind.TYPE) {
            return "use 'type' (não 'class'/'record') para declarações de tipo";
        }
        return null;
    }

    private void skipNewlines() {
        // o lexer não emite NEWLINE; newlines só mudam linha. não há nada a pular.
    }

    // ---- unit ----

    public Unit parseUnit() {
        Token start = peek();
        List<ImportDecl> imports = new ArrayList<>();
        List<Decl> decls = new ArrayList<>();
        while (!check(TokenKind.EOF)) {
            if (check(TokenKind.IMPORT)) {
                imports.add(parseImport());
            } else {
                decls.add(parseDecl());
            }
        }
        return new Unit(imports, decls, start);
    }

    private ImportDecl parseImport() {
        Token start = expect(TokenKind.IMPORT, "import");
        List<String> parts = new ArrayList<>();
        parts.add(expectIdent("nome após import").text());
        while (check(TokenKind.DOT)) {
            i++;
            parts.add(expectIdent("parte do import").text());
        }
        return new ImportDecl(parts, start);
    }

    private Decl parseDecl() {
        if (check(TokenKind.TYPE)) {
            return parseTypeDecl();
        }
        if (check(TokenKind.ENUM)) {
            return parseEnumDecl();
        }
        // fun/fn/func/class/record não são keywords Hydra — erro honesto
        if (check(TokenKind.IDENT)) {
            String w = peek().text();
            if ("fun".equals(w) || "fn".equals(w) || "func".equals(w)) {
                Token bad = peek();
                throw err("'" + w + "' não existe em Hydra", bad, "HYP012",
                        "funções: nome(params) { ... } — sem palavra-chave");
            }
            if ("class".equals(w) || "record".equals(w)) {
                Token bad = peek();
                throw err("'" + w + "' não existe em Hydra", bad, "HYP013",
                        "tipos: type Nome(campos) { métodos } · dados: enum Nome { Casos }");
            }
        }
        // fun-decl: ident ( params ) [ : typ ] bloco| = expr
        // test/application contextuais: ident STRING bloco | application { ... }
        Token nameTok = expectIdent("declaração");
        String name = nameTok.text();
        if (check(TokenKind.LPAREN)) {
            return parseFunRest(name, nameTok);
        }
        if ("test".equals(name) && check(TokenKind.STRING)) {
            Token str = toks.get(i++);
            expect(TokenKind.LBRACE, "{ de test");
            Block body = parseBlockBody();
            return new FunDecl("test:" + str.text(), List.of(), null, body, nameTok);
        }
        if ("application".equals(name) && check(TokenKind.LBRACE)) {
            i++;
            Block body = parseBlockBody();
            return new FunDecl("application", List.of(), null, body, nameTok);
        }
        throw err("esperava '(' para função, 'type' ou 'enum'", nameTok, "HYP002",
                "função: nome(params) { ... } · tipo: type Nome(...) { ... } · enum: enum Nome { Casos }");
    }

    private Decl parseEnumDecl() {
        Token start = expect(TokenKind.ENUM, "enum");
        String name = expectIdent("nome do enum").text();
        expect(TokenKind.LBRACE, "{");
        List<String> cases = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            Token c = expectIdent("caso de enum");
            cases.add(c.text());
        }
        expect(TokenKind.RBRACE, "}");
        if (cases.isEmpty()) {
            throw err("enum sem casos: " + name, start, "HYP003",
                    "enum Color { Red Green Blue } — casos separados por newline (sem vírgulas)");
        }
        return new EnumDecl(name, cases, start);
    }

    private Decl parseTypeDecl() {
        Token start = expect(TokenKind.TYPE, "type");
        String name = expectIdent("nome do type").text();
        TypeRef extendsType = null;
        List<Field> fields = new ArrayList<>();
        List<FunDecl> methods = new ArrayList<>();
        if (check(TokenKind.LPAREN)) {
            i++;
            if (!check(TokenKind.RPAREN)) {
                do {
                    fields.add(parseField());
                } while (match(TokenKind.COMMA));
            }
            expect(TokenKind.RPAREN, ")");
        }
        if (check(TokenKind.EXTENDS)) {
            i++;
            extendsType = parseTypeRef();
        }
        if (check(TokenKind.LBRACE)) {
            i++;
            while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
                if (check(TokenKind.VAR) || looksLikeField()) {
                    if (peek(1).kind() == TokenKind.LPAREN || (peek(1).kind() != TokenKind.COLON && peek(2).kind() == TokenKind.LPAREN)) {
                        methods.add(parseMethod());
                    } else {
                        fields.add(parseField());
                    }
                } else {
                    methods.add(parseMethod());
                }
            }
            expect(TokenKind.RBRACE, "}");
        }
        return new TypeDecl(name, extendsType, fields, methods, start);
    }

    private boolean looksLikeField() {
        // [var] Type name
        int k = 0;
        if (peek(k).kind() == TokenKind.VAR) k++;
        return peek(k).kind() == TokenKind.IDENT || peek(k).kind() == TokenKind.QUESTION;
    }

    private FunDecl parseMethod() {
        Token nameTok = expectIdent("nome do método");
        return parseFunRest(nameTok.text(), nameTok);
    }

    private FunDecl parseFunRest(String name, Token nameTok) {
        expect(TokenKind.LPAREN, "(");
        List<Param> params = new ArrayList<>();
        if (!check(TokenKind.RPAREN)) {
            do {
                Token pName = expectIdent("parâmetro");
                if (!check(TokenKind.COLON)) {
                    Token bad = peek();
                    throw err("esperava ':' após parâmetro '" + pName.text() + "', achou " + describe(bad),
                            bad, "HYP008", "forma: " + pName.text() + ": Tipo  (ex.: x: Int)");
                }
                i++; // :
                TypeRef type = parseTypeRef();
                params.add(new Param(pName.text(), type, pName));
            } while (match(TokenKind.COMMA));
        }
        expect(TokenKind.RPAREN, ")");
        TypeRef ret = null;
        if (match(TokenKind.COLON)) {
            ret = parseTypeRef();
        }
        if (check(TokenKind.LBRACE)) {
            Block body = parseBlock();
            return new FunDecl(name, params, ret, body, nameTok);
        }
        if (check(TokenKind.EQ)) {
            i++;
            Expr e = parseExpr();
            Block body = new Block(List.of(new ReturnStmt(e, e.pos())), e.pos());
            return new FunDecl(name, params, ret, body, nameTok);
        }
        if (isWord("fun") || isWord("fn") || isWord("func")) {
            Token bad = peek();
            throw err("esperava '{' ou '=' após assinatura de função", bad, "HYP009",
                    "Hydra não usa fun/fn/func — a função já começou em '" + name + "'; corpo: { ... } ou = expr");
        }
        // método abstrato (assinatura sem corpo) — só em type-decl
        return new FunDecl(name, params, ret, null, nameTok);
    }

    private Field parseField() {
        boolean mutable = match(TokenKind.VAR);
        Token name = expectIdent("campo");
        expect(TokenKind.COLON, ":");
        TypeRef type = parseTypeRef();
        return new Field(mutable, type, name.text(), name);
    }

    private TypeRef parseTypeRef() {
        // tipo-função: ( T1, T2 ) -> R  (simplificado: aceita e consome)
        if (check(TokenKind.LPAREN)) {
            int save = i;
            i++; // (
            try {
                if (!check(TokenKind.RPAREN)) {
                    do {
                        parseTypeRef();
                    } while (match(TokenKind.COMMA));
                }
                expect(TokenKind.RPAREN, ")");
                if (check(TokenKind.ARROW)) {
                    i++;
                    TypeRef ret = parseTypeRef();
                    return new TypeRef("fun", ret != null && ret.nullable(), ret != null ? ret.pos() : peek());
                }
            } catch (SyntaxError e) {
                // não era tipo-função
            }
            i = save;
        }
        Token name = expectIdent("tipo");
        boolean nullable = match(TokenKind.QUESTION);
        return new TypeRef(name.text(), nullable, name);
    }

    // ---- statements ----

    private Block parseBlock() {
        Token start = expect(TokenKind.LBRACE, "{");
        return parseBlockBody(start);
    }

    private Block parseBlockBody() {
        return parseBlockBody(peek());
    }

    private Block parseBlockBody(Token start) {
        List<Stmt> stmts = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            stmts.add(parseStmt());
        }
        expect(TokenKind.RBRACE, "}");
        return new Block(stmts, start);
    }

    private Stmt parseStmt() {
        Token t = peek();
        return switch (t.kind()) {
            case VAL -> parseVarDecl(false);
            case VAR -> parseVarDecl(true);
            case RETURN -> {
                i++;
                Expr e = null;
                if (!check(TokenKind.RBRACE) && !check(TokenKind.EOF) && !startsStmt()) {
                    e = parseExpr();
                }
                yield new ReturnStmt(e, t);
            }
            case IF -> parseIfStmt();
            case FOR -> parseForStmt();
            case THROW -> {
                i++;
                Expr e = parseExpr();
                yield new ThrowStmt(e, t);
            }
            case TRY -> parseTryStmt();
            case MATCH -> {
                Expr e = parseMatchExpr();
                yield new ExprStmt(e, e.pos());
            }
            case SPAWN -> {
                i++;
                Expr e = parseExpr();
                yield new SpawnStmt(e, t);
            }
            case LBRACE -> parseBlock();
            default -> {
                Expr e = parseExpr();
                yield new ExprStmt(e, e.pos());
            }
        };
    }

    private boolean startsStmt() {
        TokenKind k = peek().kind();
        return k == TokenKind.VAL || k == TokenKind.VAR || k == TokenKind.RETURN
                || k == TokenKind.IF || k == TokenKind.FOR || k == TokenKind.THROW
                || k == TokenKind.TRY || k == TokenKind.MATCH || k == TokenKind.SPAWN
                || k == TokenKind.LBRACE;
    }

    private Stmt parseVarDecl(boolean mutable) {
        Token start = toks.get(i++);
        Token name = expectIdent("variável");
        TypeRef type = null;
        if (match(TokenKind.COLON)) {
            type = parseTypeRef();
        }
        Expr init = null;
        if (match(TokenKind.EQ)) {
            init = parseExpr();
        }
        return new VarDecl(mutable, name.text(), type, init, start);
    }

    private Stmt parseIfStmt() {
        Token start = expect(TokenKind.IF, "if");
        expect(TokenKind.LPAREN, "(");
        Expr cond = parseExpr();
        expect(TokenKind.RPAREN, ")");
        Block thenB = parseBlock();
        Block elseB = null;
        if (match(TokenKind.ELSE)) {
            if (check(TokenKind.IF)) {
                Stmt nested = parseIfStmt();
                elseB = new Block(List.of(nested), nested.pos());
            } else {
                elseB = parseBlock();
            }
        }
        return new IfStmt(cond, thenB, elseB, start);
    }

    private Stmt parseForStmt() {
        Token start = expect(TokenKind.FOR, "for");
        ForHead head;
        if (check(TokenKind.LPAREN)) {
            i++;
            if (check(TokenKind.VAL) || check(TokenKind.VAR)) {
                boolean mutable = check(TokenKind.VAR);
                Token declTok = toks.get(i++);
                Token name = expectIdent("variável");
                if (!check(TokenKind.EQ)) {
                    Token bad = peek();
                    if (bad.kind() == TokenKind.IDENT) {
                        throw err("esperava '=' no for clássico, achou '" + bad.text() + "'", bad, "HYP004",
                                "for-in: for " + name.text() + " in colecao { ... } · "
                                        + "clássico: for (var i = 0, i < n, i = i + 1) { ... }");
                    }
                }
                expect(TokenKind.EQ, "=");
                Expr initVal = parseExpr();
                VarDecl init = new VarDecl(mutable, name.text(), null, initVal, declTok);
                Expr cond = null;
                Expr update = null;
                if (match(TokenKind.COMMA)) {
                    cond = parseExpr();
                    if (match(TokenKind.COMMA)) {
                        update = parseExpr();
                    }
                }
                expect(TokenKind.RPAREN, ")");
                head = new ForClassicHead(init, cond, update, start);
            } else {
                Expr cond = parseExpr();
                expect(TokenKind.RPAREN, ")");
                head = new ForCondHead(cond, start);
            }
        } else if ((check(TokenKind.VAL) || check(TokenKind.VAR))
                || (check(TokenKind.IDENT) && "in".equals(peek(1).text()))) {
            boolean mutable = check(TokenKind.VAR);
            if (check(TokenKind.VAL) || check(TokenKind.VAR)) {
                i++;
            }
            Token name = expectIdent("variável de for");
            // `in` é contextual
            Token inTok = peek();
            if (inTok.kind() != TokenKind.IDENT || !"in".equals(inTok.text())) {
                throw err("esperava 'in' no for-in", inTok, "HYP004",
                        "for (val x in colecao) { ... } ou for (var i = 0, i < n, i = i + 1) { ... }");
            }
            i++;
            Expr iter = parseExpr();
            head = new ForInHead(mutable, name.text(), iter, start);
        } else {
            expect(TokenKind.LPAREN, "(");
            Expr cond = parseExpr();
            expect(TokenKind.RPAREN, ")");
            head = new ForCondHead(cond, start);
        }
        Block body = parseBlock();
        return new ForStmt(head, body, start);
    }

    private Stmt parseTryStmt() {
        Token start = expect(TokenKind.TRY, "try");
        Block body = parseBlock();
        List<CatchClause> catches = new ArrayList<>();
        while (match(TokenKind.CATCH)) {
            expect(TokenKind.LPAREN, "(");
            if (check(TokenKind.IDENT) && peek(1).kind() == TokenKind.IDENT) {
                Token bad = peek(1);
                throw err("catch em Hydra não declara tipo: catch (" + peek().text() + ")", bad, "HYP011",
                        "escreva catch (e) { ... } — o tipo do erro é String (throw \"msg\")");
            }
            Token name = expectIdent("nome do catch");
            expect(TokenKind.RPAREN, ")");
            Block cbody = parseBlock();
            catches.add(new CatchClause(name.text(), cbody, name));
        }
        return new TryStmt(body, catches, start);
    }

    private List<CaseArm> parseMatchArms() {
        expect(TokenKind.LBRACE, "{ do match");
        List<CaseArm> arms = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            // `case`/`default` são contextuais de match (não estão entre as 20 keywords)
            if (isWord("default")) {
                i++;
                expect(TokenKind.ARROW, "->");
                Expr r = parseExpr();
                arms.add(new CaseArm(null, null, null, r, r.pos()));
            } else if (isWord("case")) {
                i++;
                String patternType = null;
                String patternName = null;
                if (check(TokenKind.IDENT)) {
                    Token a = toks.get(i++);
                    if (check(TokenKind.DOT)) {
                        i++;
                        Token b = expectIdent("caso de enum");
                        patternType = a.text() + "." + b.text();
                    } else {
                        // binding: case String s  |  destructuring: case Point x y
                        patternType = a.text();
                        StringBuilder names = new StringBuilder(a.text());
                        while (check(TokenKind.IDENT) || check(TokenKind.VAR) || check(TokenKind.VAL)) {
                            if (check(TokenKind.VAR) || check(TokenKind.VAL)) {
                                i++;
                                continue;
                            }
                            names.append(' ').append(toks.get(i++).text());
                        }
                        String all = names.toString();
                        int sp = all.indexOf(' ');
                        if (sp < 0) {
                            patternType = all;
                            patternName = null;
                        } else {
                            patternType = all.substring(0, sp);
                            patternName = all.substring(sp + 1);
                        }
                    }
                } else if (check(TokenKind.INT) || check(TokenKind.STRING) || check(TokenKind.TRUE) || check(TokenKind.FALSE) || check(TokenKind.NULL)) {
                    Token lit = toks.get(i++);
                    patternType = lit.kind() == TokenKind.STRING ? "\"" + lit.text() + "\"" : lit.text();
                } else {
                    Token t = peek();
                    throw err("padrão inválido em case", t, "HYP005",
                            "case Color.Red -> ... | case String s -> ... | default -> ...");
                }
                Expr guard = null;
                if (match(TokenKind.IF)) {
                    guard = parseExpr();
                }
                expect(TokenKind.ARROW, "->");
                Expr result = parseExpr();
                arms.add(new CaseArm(patternType, patternName, guard, result, result.pos()));
            } else {
                Token t = peek();
                throw err("esperava case/default no match", t, "HYP006",
                        "match (x) { case P -> expr; default -> expr }");
            }
        }
        expect(TokenKind.RBRACE, "}");
        return arms;
    }

    // ---- expressions ----

    public Expr parseExpr() {
        return parseAssign();
    }

    private Expr parseAssign() {
        Expr left = parseBinary(0);
        // `a to b` — par de mapOf; mesma precedência de == (não há outro uso de `to`)
        if (isWord("to")) {
            Token t = toks.get(i++);
            Expr right = parseBinary(0);
            return new PairExpr(left, right, t);
        }
        TokenKind k = peek().kind();
        String op = switch (k) {
            case EQ -> "=";
            case PLUS_EQ -> "+=";
            case MINUS_EQ -> "-=";
            case STAR_EQ -> "*=";
            case SLASH_EQ -> "/=";
            case PERCENT_EQ -> "%=";
            default -> null;
        };
        if (op != null) {
            Token t = toks.get(i++);
            Expr right = parseAssign();
            return new AssignExpr(op, left, right, t);
        }
        return left;
    }

    private Expr parseBinary(int minPrec) {
        Expr left = parseUnary();
        while (true) {
            Token t = peek();
            String op = binaryOp(t.kind());
            if (op == null) break;
            int prec = precedence(op);
            if (prec < minPrec) break;
            i++;
            Expr right = parseBinary(prec + 1);
            left = new BinaryExpr(op, left, right, t);
        }
        return left;
    }

    private static String binaryOp(TokenKind k) {
        return switch (k) {
            case PIPE_PIPE -> "||";
            case AMP_AMP -> "&&";
            case PIPE -> "|";
            case CARET -> "^";
            case AMP -> "&";
            case EQ_EQ -> "==";
            case BANG_EQ -> "!=";
            case LT -> "<";
            case LT_EQ -> "<=";
            case GT -> ">";
            case GT_EQ -> ">=";
            case LT_LT -> "<<";
            case GT_GT -> ">>";
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case PERCENT -> "%";
            default -> null;
        };
    }

    private static int precedence(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|" -> 3;
            case "^" -> 4;
            case "&" -> 5;
            case "==", "!=", "<", "<=", ">", ">=" -> 6;
            case "<<", ">>" -> 7;
            case "+", "-" -> 8;
            case "*", "/", "%" -> 9;
            default -> -1;
        };
    }

    private Expr parseUnary() {
        Token t = peek();
        if (check(TokenKind.BANG) || check(TokenKind.MINUS)) {
            i++;
            String op = t.kind() == TokenKind.BANG ? "!" : "-";
            return new UnaryExpr(op, parseUnary(), t);
        }
        if (check(TokenKind.AWAIT) || check(TokenKind.SPAWN)) {
            i++;
            String op = t.kind() == TokenKind.AWAIT ? "await" : "spawn";
            return new UnaryExpr(op, parseUnary(), t);
        }
        return parsePostfix();
    }

    private Expr parsePostfix() {
        Expr e = parsePrimary();
        while (true) {
            if (check(TokenKind.LPAREN)) {
                i++;
                List<Expr> args = new ArrayList<>();
                if (!check(TokenKind.RPAREN)) {
                    do {
                        args.add(parseExpr());
                    } while (match(TokenKind.COMMA));
                }
                Token rp = expect(TokenKind.RPAREN, ")");
                e = new CallExpr(e, args, rp);
            } else if (check(TokenKind.DOT)) {
                i++;
                Token name = expectIdent("campo/método");
                e = new FieldExpr(e, name.text(), name);
            } else if (check(TokenKind.LBRACKET)) {
                i++;
                Expr idx = parseExpr();
                Token rb = expect(TokenKind.RBRACKET, "]");
                e = new IndexExpr(e, idx, rb);
            } else {
                break;
            }
        }
        return e;
    }

    private Expr parsePrimary() {
        Token t = peek();
        switch (t.kind()) {
            case INT:
                i++;
                return new IntLit(Long.parseLong(t.text()), t);
            case FLOAT:
                i++;
                return new FloatLit(Double.parseDouble(t.text()), t);
            case STRING:
                i++;
                return new StringLit(t.text(), t);
            case TRUE:
                i++;
                return new BoolLit(true, t);
            case FALSE:
                i++;
                return new BoolLit(false, t);
            case NULL:
                i++;
                return new NullLit(t);
            case SUPER:
                i++;
                return new IdentExpr("super", t);
            case LPAREN: {
                i++;
                // lambda após consumir '(' : ( params ) ->
                if (isLambdaAfterParen()) {
                    List<Param> params = parseLambdaParamsAfterParen();
                    expect(TokenKind.ARROW, "->");
                    if (check(TokenKind.LBRACE)) {
                        Block b = parseBlock();
                        return new LambdaExpr(params, b, null, t);
                    }
                    Expr body = parseExpr();
                    return new LambdaExpr(params, null, body, t);
                }
                Expr e = parseExpr();
                expect(TokenKind.RPAREN, ")");
                return e;
            }
            case IF: {
                i++;
                expect(TokenKind.LPAREN, "(");
                Expr cond = parseExpr();
                expect(TokenKind.RPAREN, ")");
                Expr thenE = parseExpr();
                expect(TokenKind.ELSE, "else (if é expressão)");
                Expr elseE = parseExpr();
                return new IfExpr(cond, thenE, elseE, t);
            }
            case MATCH:
                return parseMatchExpr();
            case IDENT:
                i++;
                return new IdentExpr(t.text(), t);
            default: {
                Token t2 = peek();
                String hint = null;
                if (t2.kind() == TokenKind.TYPE) {
                    hint = "use 'type' para declarações; expressões não começam com 'type'";
                } else if (t2.kind() == TokenKind.EOF) {
                    hint = "expressão incompleta no fim do arquivo";
                }
                throw err("expressão inesperada: " + describe(t2), t2, "HYP007", hint);
            }
        }
    }

    private Expr parseMatchExpr() {
        Token start = expect(TokenKind.MATCH, "match");
        expect(TokenKind.LPAREN, "(");
        Expr subject = parseExpr();
        expect(TokenKind.RPAREN, ")");
        List<CaseArm> arms = parseMatchArms();
        MatchStmt stmt = new MatchStmt(subject, arms, start);
        return new MatchExpr(stmt, start);
    }

    /** Lookahead após '(' já consumido. */
    private boolean isLambdaAfterParen() {
        if (check(TokenKind.RPAREN)) {
            return peek(1).kind() == TokenKind.ARROW;
        }
        if (check(TokenKind.IDENT)) {
            TokenKind k1 = peek(1).kind();
            if (k1 == TokenKind.COLON || k1 == TokenKind.COMMA) return true;
            if (k1 == TokenKind.RPAREN && peek(2).kind() == TokenKind.ARROW) return true;
        }
        return false;
    }

    /** Parâmetros de lambda — '(' já consumido. */
    private List<Param> parseLambdaParamsAfterParen() {
        List<Param> params = new ArrayList<>();
        if (!check(TokenKind.RPAREN)) {
            do {
                Token name = expectIdent("parâmetro lambda");
                TypeRef type = null;
                if (match(TokenKind.COLON)) {
                    type = parseTypeRef();
                }
                params.add(new Param(name.text(), type, name));
            } while (match(TokenKind.COMMA));
        }
        expect(TokenKind.RPAREN, ")");
        return params;
    }
}
