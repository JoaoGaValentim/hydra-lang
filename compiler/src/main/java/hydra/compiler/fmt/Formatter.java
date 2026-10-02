package hydra.compiler.fmt;

import hydra.compiler.ast.Ast.*;
import hydra.compiler.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F6-01b — formatter canônico Hydra.
 * Reimprime a AST com indentação de 4 espaços, preservando comentários //
 * pela linha original. Forma canônica estável: fmt(fmt(x)) == fmt(x).
 */
public final class Formatter {

    private Formatter() {}

    /** Formata fonte Hydra. Fonte inválida → SyntaxError (não inventa saída). */
    public static String format(String source) {
        String src = source == null ? "" : source;
        Unit unit = Parser.parse(src);
        List<Comment> comments = scanComments(src);
        Printer p = new Printer(src, comments);
        p.printUnit(unit);
        return p.finish();
    }

    private record Comment(int line, String text) {}

    static List<Comment> scanComments(String src) {
        List<Comment> out = new ArrayList<>();
        int i = 0;
        int line = 1;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (c == '"') {
                i++;
                while (i < src.length() && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\') i++;
                    if (i < src.length() && src.charAt(i) == '\n') line++;
                    i++;
                }
                if (i < src.length()) i++;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int start = i;
                while (i < src.length() && src.charAt(i) != '\n') i++;
                String text = src.substring(start, i);
                while (!text.isEmpty() && (text.charAt(text.length() - 1) == ' '
                        || text.charAt(text.length() - 1) == '\t'
                        || text.charAt(text.length() - 1) == '\r')) {
                    text = text.substring(0, text.length() - 1);
                }
                out.add(new Comment(line, text));
                continue;
            }
            i++;
        }
        return out;
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String fmtFloat(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.format(Locale.ROOT, "%.1f", d);
        }
        return Double.toString(d);
    }

    private static final class Printer {
        private final StringBuilder out = new StringBuilder();
        private final String src;
        private final List<Comment> comments;
        private int ci;
        private int indent;

        Printer(String src, List<Comment> comments) {
            this.src = src;
            this.comments = comments;
        }

        /** Linha do `}` que fecha o bloco cujo `{` está em openLine (ou depois). */
        int matchingBraceLine(int openLine) {
            int i = lineStart(openLine);
            int depth = 0;
            boolean started = false;
            int line = openLine;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c == '\n') {
                    line++;
                    i++;
                    continue;
                }
                if (c == '"') {
                    i++;
                    while (i < src.length() && src.charAt(i) != '"') {
                        if (src.charAt(i) == '\\') i++;
                        if (i < src.length() && src.charAt(i) == '\n') line++;
                        i++;
                    }
                    if (i < src.length()) i++;
                    continue;
                }
                if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                    while (i < src.length() && src.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '{') {
                    depth++;
                    started = true;
                } else if (c == '}') {
                    depth--;
                    if (started && depth == 0) {
                        return line;
                    }
                }
                i++;
            }
            return Integer.MAX_VALUE;
        }

        private int lineStart(int line) {
            int l = 1;
            int i = 0;
            while (i < src.length() && l < line) {
                if (src.charAt(i) == '\n') l++;
                i++;
            }
            return i;
        }

        String finish() {
            while (ci < comments.size()) {
                emitCommentsBefore(Integer.MAX_VALUE);
            }
            String s = out.toString();
            while (!s.isEmpty() && s.charAt(s.length() - 1) == '\n') {
                s = s.substring(0, s.length() - 1);
            }
            return s.isEmpty() ? "\n" : s + "\n";
        }

        private void emitCommentsBefore(int line) {
            while (ci < comments.size() && comments.get(ci).line < line) {
                pad();
                out.append(comments.get(ci).text).append('\n');
                ci++;
            }
        }

        private void emitTrailing(int line) {
            while (ci < comments.size() && comments.get(ci).line == line) {
                out.append(' ').append(comments.get(ci).text);
                ci++;
            }
        }

        private void pad() {
            out.append("    ".repeat(indent));
        }

        /** Comentários antes da linha do nó; conteúdo sem \\n; trailing; \\n. */
        private void node(int line, Runnable content) {
            emitCommentsBefore(line);
            content.run();
            emitTrailing(line);
            out.append('\n');
        }

        private void printUnit(Unit unit) {
            for (ImportDecl imp : unit.imports()) {
                node(imp.pos().line(), () -> {
                    pad();
                    out.append("import ").append(String.join(".", imp.parts()));
                });
            }
            if (!unit.imports().isEmpty() && !unit.decls().isEmpty()) {
                out.append('\n');
            }
            List<Decl> decls = unit.decls();
            for (int d = 0; d < decls.size(); d++) {
                printDecl(decls.get(d));
                if (d < decls.size() - 1) {
                    out.append('\n');
                }
            }
        }

        private void printDecl(Decl decl) {
            if (decl instanceof FunDecl fun) {
                printFun(fun);
            } else if (decl instanceof TypeDecl type) {
                printType(type);
            } else if (decl instanceof EnumDecl en) {
                printEnum(en);
            } else {
                pad();
                out.append("// formatter: decl desconhecida\n");
            }
        }

        private void printFun(FunDecl fun) {
            int line = fun.pos().line();
            if (fun.name().startsWith("test:")) {
                String name = fun.name().substring("test:".length());
                node(line, () -> {
                    pad();
                    out.append("test \"").append(escape(name)).append("\" ");
                    printBlockContent(fun.body(), line);
                });
                return;
            }
            if ("application".equals(fun.name())) {
                node(line, () -> {
                    pad();
                    out.append("application ");
                    printBlockContent(fun.body(), line);
                });
                return;
            }
            node(line, () -> {
                pad();
                out.append(fun.name());
                printParams(fun.params());
                if (fun.returnType() != null) {
                    out.append(": ").append(typeRef(fun.returnType()));
                }
                if (fun.body() == null) {
                    return;
                }
                if (isSugarReturn(fun)) {
                    out.append(" = ").append(expr(((ReturnStmt) fun.body().stmts().get(0)).value()));
                } else {
                    out.append(' ');
                    printBlockContent(fun.body(), line);
                }
            });
        }

        /** `= expr` vira Block(ReturnStmt) na mesma linha da declaração. */
        private static boolean isSugarReturn(FunDecl fun) {
            Block b = fun.body();
            return b != null
                    && b.stmts().size() == 1
                    && b.stmts().get(0) instanceof ReturnStmt
                    && fun.pos().line() == b.pos().line();
        }

        private void printType(TypeDecl type) {
            node(type.pos().line(), () -> {
                pad();
                out.append("type ").append(type.name());
                if (!type.fields().isEmpty()) {
                    out.append('(');
                    for (int i = 0; i < type.fields().size(); i++) {
                        if (i > 0) out.append(", ");
                        Field f = type.fields().get(i);
                        if (f.mutable()) out.append("var ");
                        out.append(f.name()).append(": ").append(typeRef(f.type()));
                    }
                    out.append(')');
                }
                if (type.extendsType() != null) {
                    out.append(" extends ").append(typeRef(type.extendsType()));
                }
                if (type.methods().isEmpty()) {
                    return;
                }
                out.append(" {\n");
                indent++;
                for (FunDecl m : type.methods()) {
                    emitCommentsBefore(m.pos().line());
                    pad();
                    out.append(m.name());
                    printParams(m.params());
                    if (m.returnType() != null) {
                        out.append(": ").append(typeRef(m.returnType()));
                    }
                    if (m.body() == null) {
                        emitTrailing(m.pos().line());
                        out.append('\n');
                        continue;
                    }
                    out.append(' ');
                    printBlockContent(m.body(), m.pos().line());
                    emitTrailing(m.pos().line());
                    out.append('\n');
                }
                indent--;
                pad();
                out.append('}');
            });
        }

        private void printEnum(EnumDecl en) {
            node(en.pos().line(), () -> {
                pad();
                out.append("enum ").append(en.name()).append(" {\n");
                indent++;
                for (String c : en.cases()) {
                    pad();
                    out.append(c).append('\n');
                }
                indent--;
                pad();
                out.append('}');
            });
        }

        private void printBlockContent(Block b, int openLine) {
            int end = matchingBraceLine(openLine);
            out.append("{\n");
            indent++;
            printStmts(b.stmts());
            emitCommentsBefore(end);
            indent--;
            pad();
            out.append('}');
        }

        private void printStmts(List<Stmt> stmts) {
            for (int i = 0; i < stmts.size(); i++) {
                Stmt s = stmts.get(i);
                if (s instanceof ExprStmt es && es.expr() instanceof IdentExpr id
                        && i + 1 < stmts.size() && stmts.get(i + 1) instanceof Block blk) {
                    node(es.pos().line(), () -> {
                        pad();
                        out.append(id.name()).append(' ');
                        printBlockContent(blk, es.pos().line());
                    });
                    i++;
                    continue;
                }
                printStmt(s);
            }
        }

        private void printStmt(Stmt s) {
            if (s instanceof VarDecl v) {
                node(v.pos().line(), () -> {
                    pad();
                    out.append(v.mutable() ? "var " : "val ");
                    out.append(v.name());
                    if (v.type() != null) out.append(": ").append(typeRef(v.type()));
                    if (v.init() != null) out.append(" = ").append(expr(v.init()));
                });
            } else if (s instanceof Block b) {
                node(b.pos().line(), () -> {
                    pad();
                    printBlockContent(b, b.pos().line());
                });
            } else if (s instanceof ReturnStmt r) {
                node(r.pos().line(), () -> {
                    pad();
                    out.append("return");
                    if (r.value() != null) out.append(' ').append(expr(r.value()));
                });
            } else if (s instanceof IfStmt ifst) {
                printIf(ifst);
            } else if (s instanceof ForStmt f) {
                printFor(f);
            } else if (s instanceof ExprStmt es) {
                node(es.pos().line(), () -> {
                    pad();
                    out.append(expr(es.expr()));
                });
            } else if (s instanceof ThrowStmt t) {
                node(t.pos().line(), () -> {
                    pad();
                    out.append("throw ").append(expr(t.value()));
                });
            } else if (s instanceof TryStmt t) {
                printTry(t);
            } else if (s instanceof MatchStmt m) {
                printMatch(m, m.pos().line());
            } else if (s instanceof SpawnStmt sp) {
                node(sp.pos().line(), () -> {
                    pad();
                    out.append("spawn ").append(expr(sp.value()));
                });
            } else {
                pad();
                out.append("// formatter: stmt desconhecida\n");
            }
        }

        private void printIf(IfStmt ifst) {
            node(ifst.pos().line(), () -> {
                pad();
                emitIfHead(ifst);
            });
        }

        /** `if (c) ... [else ...]` sem newline no fim. */
        private void emitIfHead(IfStmt ifst) {
            out.append("if (").append(expr(ifst.cond())).append(") ");
            int open = ifst.pos().line();
            printBlockContent(ifst.thenBlock(), open);
            Block elseB = ifst.elseBlock();
            if (elseB != null) {
                out.append(" else ");
                if (elseB.stmts().size() == 1 && elseB.stmts().get(0) instanceof IfStmt nested) {
                    emitIfHead(nested);
                } else {
                    int thenEnd = matchingBraceLine(open);
                    printBlockContent(elseB, thenEnd + 1);
                }
            }
        }

        private void printFor(ForStmt f) {
            node(f.pos().line(), () -> {
                pad();
                out.append("for ");
                ForHead head = f.head();
                if (head instanceof ForClassicHead ch) {
                    out.append('(');
                    VarDecl init = ch.init();
                    out.append(init.mutable() ? "var " : "val ").append(init.name());
                    if (init.init() != null) out.append(" = ").append(expr(init.init()));
                    if (ch.cond() != null) out.append(", ").append(expr(ch.cond()));
                    if (ch.update() != null) out.append(", ").append(expr(ch.update()));
                    out.append(')');
                } else if (head instanceof ForCondHead ch) {
                    out.append('(').append(expr(ch.cond())).append(')');
                } else if (head instanceof ForInHead ih) {
                    // forma canônica: `for x in xs` (val implícito) | `for var x in xs`
                    if (ih.mutable()) out.append("var ");
                    out.append(ih.name()).append(" in ").append(expr(ih.iter()));
                }
                out.append(' ');
                printBlockContent(f.body(), f.pos().line());
            });
        }

        private void printTry(TryStmt t) {
            node(t.pos().line(), () -> {
                pad();
                out.append("try ");
                int cursor = t.pos().line();
                printBlockContent(t.body(), cursor);
                cursor = matchingBraceLine(cursor);
                for (CatchClause c : t.catches()) {
                    out.append(" catch (").append(c.name()).append(") ");
                    printBlockContent(c.body(), cursor + 1);
                    cursor = matchingBraceLine(cursor + 1);
                }
            });
        }

        private void printMatch(MatchStmt m, int line) {
            node(line, () -> {
                pad();
                out.append("match (").append(expr(m.subject())).append(") {\n");
                indent++;
                for (CaseArm arm : m.cases()) {
                    emitCommentsBefore(arm.pos().line());
                    pad();
                    if (arm.patternType() == null) {
                        out.append("default");
                    } else {
                        out.append("case ").append(arm.patternType());
                        if (arm.patternName() != null && !arm.patternName().isEmpty()) {
                            out.append(' ').append(arm.patternName());
                        }
                        if (arm.guard() != null) {
                            out.append(" if ").append(expr(arm.guard()));
                        }
                    }
                    out.append(" -> ").append(expr(arm.result()));
                    emitTrailing(arm.pos().line());
                    out.append('\n');
                }
                indent--;
                pad();
                out.append('}');
            });
        }

        private String typeRef(TypeRef t) {
            return t.name() + (t.nullable() ? "?" : "");
        }

        private void printParams(List<Param> params) {
            out.append('(');
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) out.append(", ");
                Param p = params.get(i);
                out.append(p.name()).append(": ").append(typeRef(p.type()));
            }
            out.append(')');
        }

        private String expr(Expr e) {
            StringBuilder sb = new StringBuilder();
            printExpr(sb, e);
            return sb.toString();
        }

        private static void printExpr(StringBuilder sb, Expr e) {
            if (e instanceof IntLit n) {
                sb.append(n.value());
            } else if (e instanceof FloatLit f) {
                sb.append(fmtFloat(f.value()));
            } else if (e instanceof StringLit s) {
                sb.append('"').append(escape(s.value())).append('"');
            } else if (e instanceof BoolLit b) {
                sb.append(b.value());
            } else if (e instanceof NullLit) {
                sb.append("null");
            } else if (e instanceof IdentExpr id) {
                sb.append(id.name());
            } else if (e instanceof BinaryExpr b) {
                operand(sb, b.left());
                sb.append(' ').append(b.op()).append(' ');
                operand(sb, b.right());
            } else if (e instanceof UnaryExpr u) {
                sb.append(u.op());
                operand(sb, u.operand());
            } else if (e instanceof CallExpr c) {
                operand(sb, c.callee());
                sb.append('(');
                for (int i = 0; i < c.args().size(); i++) {
                    if (i > 0) sb.append(", ");
                    printExpr(sb, c.args().get(i));
                }
                sb.append(')');
            } else if (e instanceof FieldExpr f) {
                operand(sb, f.receiver());
                sb.append('.').append(f.name());
            } else if (e instanceof AssignExpr a) {
                operand(sb, a.target());
                sb.append(' ').append(a.op()).append(' ');
                operand(sb, a.value());
            } else if (e instanceof IfExpr ife) {
                sb.append("if (").append(exprStr(ife.cond())).append(") ")
                        .append(exprStr(ife.thenExpr())).append(" else ")
                        .append(exprStr(ife.elseExpr()));
            } else if (e instanceof LambdaExpr lam) {
                sb.append('(');
                for (int i = 0; i < lam.params().size(); i++) {
                    if (i > 0) sb.append(", ");
                    Param p = lam.params().get(i);
                    sb.append(p.name()).append(": ").append(p.type().name())
                            .append(p.type().nullable() ? "?" : "");
                }
                sb.append(") -> ");
                if (lam.exprBody() != null) {
                    printExpr(sb, lam.exprBody());
                } else {
                    printBlockInline(sb, lam.body());
                }
            } else if (e instanceof MatchExpr me) {
                printMatchInline(sb, me.stmt());
            } else {
                sb.append("/* expr desconhecida */");
            }
        }

        private static String exprStr(Expr e) {
            StringBuilder t = new StringBuilder();
            printExpr(t, e);
            return t.toString();
        }

        private static void operand(StringBuilder sb, Expr e) {
            boolean wrap = e instanceof BinaryExpr || e instanceof AssignExpr
                    || e instanceof IfExpr || e instanceof LambdaExpr || e instanceof MatchExpr;
            if (wrap) sb.append('(');
            printExpr(sb, e);
            if (wrap) sb.append(')');
        }

        /**
         * Bloco de lambda em expressão. Indentação canônica fixa:
         * corpo com 8 espaços, fechamento com 4 — estável sob reparse
         * (a lambda multi-linha reparseia com o mesmo corpo de stmts).
         */
        private static void printBlockInline(StringBuilder sb, Block b) {
            sb.append("{\n");
            for (Stmt s : b.stmts()) {
                sb.append("        ");
                printInlineStmt(sb, s);
                sb.append('\n');
            }
            sb.append("    }");
        }

        private static void printInlineStmt(StringBuilder sb, Stmt s) {
            if (s instanceof ReturnStmt r) {
                sb.append("return");
                if (r.value() != null) sb.append(' ').append(exprStr(r.value()));
            } else if (s instanceof VarDecl v) {
                sb.append(v.mutable() ? "var " : "val ").append(v.name());
                if (v.type() != null) sb.append(": ").append(v.type().name())
                        .append(v.type().nullable() ? "?" : "");
                if (v.init() != null) sb.append(" = ").append(exprStr(v.init()));
            } else if (s instanceof ExprStmt es) {
                printExpr(sb, es.expr());
            } else if (s instanceof Block inner) {
                printBlockInline(sb, inner);
            } else {
                sb.append("/* stmt em lambda */");
            }
        }

        private static void printMatchInline(StringBuilder sb, MatchStmt m) {
            sb.append("match (").append(exprStr(m.subject())).append(") {\n");
            for (CaseArm arm : m.cases()) {
                sb.append("        ");
                if (arm.patternType() == null) {
                    sb.append("default");
                } else {
                    sb.append("case ").append(arm.patternType());
                    if (arm.patternName() != null && !arm.patternName().isEmpty()) {
                        sb.append(' ').append(arm.patternName());
                    }
                    if (arm.guard() != null) sb.append(" if ").append(exprStr(arm.guard()));
                }
                sb.append(" -> ").append(exprStr(arm.result())).append('\n');
            }
            sb.append("    }");
        }
    }
}
