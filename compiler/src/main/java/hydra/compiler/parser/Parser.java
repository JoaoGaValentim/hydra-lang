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
    private int i;

    private Parser(List<Token> tokens) {
        this.toks = tokens;
    }

    public static Unit parse(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        for (Token t : tokens) {
            if (t.kind() == TokenKind.ERROR) {
                throw new SyntaxError(t.text(), t.line(), t.col());
            }
        }
        return new Parser(tokens).parseUnit();
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
            throw new SyntaxError("esperava " + what + ", achou " + t.kind(), t.line(), t.col());
        }
        return toks.get(i++);
    }

    private Token expectIdent(String what) {
        return expect(TokenKind.IDENT, what);
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
        throw new SyntaxError("esperava '(' para função, 'type' ou 'enum'", nameTok.line(), nameTok.col());
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
            throw new SyntaxError("enum sem casos: " + name, start.line(), start.col());
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
                expect(TokenKind.COLON, ":");
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
                throw new SyntaxError("esperava 'in' no for-in", inTok.line(), inTok.col());
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
                    throw new SyntaxError("padrão inválido em case", t.line(), t.col());
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
                throw new SyntaxError("esperava case/default no match", t.line(), t.col());
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
                e = new CallExpr(new IdentExpr("get", rb), List.of(idx), rb); // simplificado
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
            default:
                throw new SyntaxError("expressão inesperada: " + t.kind(), t.line(), t.col());
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
