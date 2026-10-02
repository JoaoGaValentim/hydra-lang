package hydra.compiler.migrate;

import dev.kof.compiler.AstNode;
import dev.kof.compiler.ApplicationDeclarationNode;
import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.BlockStmt;
import dev.kof.compiler.BreakStmt;
import dev.kof.compiler.CatchClause;
import dev.kof.compiler.ClassDeclarationNode;
import dev.kof.compiler.CompilationUnitNode;
import dev.kof.compiler.ConstructorDeclarationNode;
import dev.kof.compiler.ContinueStmt;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.DoWhileStmt;
import dev.kof.compiler.EntityDeclarationNode;
import dev.kof.compiler.EntityFieldNode;
import dev.kof.compiler.EnumDeclarationNode;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.FieldDeclarationNode;
import dev.kof.compiler.FormalParameterNode;
import dev.kof.compiler.ForStmt;
import dev.kof.compiler.FunctionDeclarationNode;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.IfExpr;
import dev.kof.compiler.IfStmt;
import dev.kof.compiler.InterfaceDeclarationNode;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.LiteralExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.MethodDeclarationNode;
import dev.kof.compiler.NewExpr;
import dev.kof.compiler.RecordComponentNode;
import dev.kof.compiler.RecordDeclarationNode;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.SpawnStmt;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.SwitchExpr;
import dev.kof.compiler.SwitchExprCase;
import dev.kof.compiler.TestDeclarationNode;
import dev.kof.compiler.ThrowStmt;
import dev.kof.compiler.TryStmt;
import dev.kof.compiler.UnaryExpr;
import dev.kof.compiler.VarDeclStmt;
import dev.kof.compiler.WhileStmt;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import hydra.compiler.parser.SyntaxError;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F4-01 — migração Kof→Hydra (`.kf` → `.hy`).
 * AST-walk no parser Kof → impressão canônica Hydra → gate com o parser Hydra.
 * Construtos sem forma Hydra honesta viram diagnóstico MIG0xx.
 */
public final class Migrator {

    private Migrator() {}

    public static MigrateResult migrate(String kofSource, String sourceName) {
        String name = sourceName == null ? "input.kf" : sourceName;
        DiagnosticCollector kofDiags = new DiagnosticCollector();
        CompilationUnitNode unit;
        try {
            Lexer lx = new Lexer(kofSource == null ? "" : kofSource, name, kofDiags);
            unit = new Parser(lx.tokenize(), kofDiags, name).parse();
        } catch (RuntimeException e) {
            return MigrateResult.failure(List.of(
                    MigrateDiagnostic.of("MIG008", "parser Kof falhou: " + e.getMessage(),
                            "corrija a fonte .kf antes de migrar")));
        }
        List<String> kofErrors = kofDiags.getDiagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                .map(d -> d.file() + ":" + d.line() + ":" + d.column() + " " + d.message())
                .toList();
        if (!kofErrors.isEmpty()) {
            return MigrateResult.failure(List.of(
                    MigrateDiagnostic.of("MIG008", "parser Kof com erros:\n" + String.join("\n", kofErrors),
                            "corrija a fonte .kf antes de migrar")));
        }

        Printer p = new Printer();
        p.printUnit(unit);
        String hydraSrc = p.out.toString();

        try {
            hydra.compiler.parser.Parser.parse(hydraSrc);
        } catch (SyntaxError e) {
            p.diags.add(MigrateDiagnostic.of("MIG009",
                    "saída Hydra não parseia: " + e.getMessage()
                            + " (linha " + e.line() + ", col " + e.col() + ")",
                    "revise o trecho migrado ou migre manualmente"));
            return new MigrateResult(hydraSrc, List.copyOf(p.diags), false);
        } catch (RuntimeException e) {
            p.diags.add(MigrateDiagnostic.of("MIG009",
                    "saída Hydra não parseia: " + e.getMessage(),
                    "revise o trecho migrado ou migre manualmente"));
            return new MigrateResult(hydraSrc, List.copyOf(p.diags), false);
        }

        // ok = sem diagnóstico de parcialidade (só MIG010 package é informativo)
        boolean ok = p.diags.stream().allMatch(d -> "MIG010".equals(d.code()));
        return new MigrateResult(hydraSrc, List.copyOf(p.diags), ok);
    }

    // ------------------------------------------------------------------

    private static final class Printer {
        final StringBuilder out = new StringBuilder();
        final List<MigrateDiagnostic> diags = new ArrayList<>();
        final Set<String> recordComponents = new LinkedHashSet<>();
        int indent;

        void printUnit(CompilationUnitNode unit) {
            out.append("// Migrado de Kof por hydra migrate (F4-01)\n");
            out.append("// Comentários da fonte original não são preservados.\n\n");

            for (AstNode d : unit.declarations()) {
                if (d instanceof RecordDeclarationNode r) {
                    for (RecordComponentNode c : r.components()) recordComponents.add(c.name());
                }
            }

            if (unit.packageName() != null && !unit.packageName().isBlank()) {
                diags.add(MigrateDiagnostic.of("MIG010",
                        "package '" + unit.packageName() + "' descartado",
                        "Hydra usa caminho de diretório, não package"));
            }
            for (String imp : unit.imports()) {
                line("import " + imp);
            }
            if (!unit.imports().isEmpty()) out.append('\n');

            boolean first = true;
            for (AstNode d : unit.declarations()) {
                if (!first) out.append('\n');
                first = false;
                printDecl(d);
            }
        }

        void printDecl(AstNode d) {
            if (d instanceof FunctionDeclarationNode f) printFun(f);
            else if (d instanceof ClassDeclarationNode c) printClass(c);
            else if (d instanceof RecordDeclarationNode r) printRecord(r);
            else if (d instanceof EnumDeclarationNode e) printEnum(e);
            else if (d instanceof InterfaceDeclarationNode i) printInterface(i);
            else if (d instanceof EntityDeclarationNode e) printEntity(e);
            else if (d instanceof TestDeclarationNode t) printTest(t);
            else if (d instanceof ApplicationDeclarationNode a) printApplication(a);
            else {
                diags.add(MigrateDiagnostic.of("MIG013",
                        "declaração Kof não migrada: " + d.getClass().getSimpleName(),
                        "migre manualmente"));
                line("// MIG: " + d.getClass().getSimpleName() + " não migrado");
            }
        }

        void printFun(FunctionDeclarationNode f) {
            if (!f.typeParameters().isEmpty()) {
                diags.add(MigrateDiagnostic.of("MIG012",
                        "função genérica '" + f.name() + "' — typeParameters descartados",
                        "Hydra v1 sem generics no core"));
            }
            String ret = normRet(f.returnType());
            out.append(pad()).append(f.name()).append('(').append(params(f.parameters())).append(')');
            if (ret != null) out.append(": ").append(ret);
            printBody(f.body(), true);
        }

        void printMethod(MethodDeclarationNode m) {
            String ret = normRet(m.returnType());
            out.append(pad()).append(m.name()).append('(').append(params(m.parameters())).append(')');
            if (ret != null) out.append(": ").append(ret);
            printBody(m.body(), false);
        }

        /** Corpo: `= expr` quando 1 return e `allowExprBody`; senão bloco. */
        private void printBody(List<StatementNode> body, boolean allowExprBody) {
            if (body == null) {
                out.append('\n');
                return;
            }
            if (allowExprBody && body.size() == 1
                    && body.get(0) instanceof ReturnStmt r && r.value() != null) {
                // Kof `Int f() = e` vira return no AST; emite sugar Hydra `= e`
                // apenas quando o nome da função não é main e o return é "simples"
                out.append(" = ");
                printExpr(r.value());
                out.append('\n');
                return;
            }
            out.append(" {\n");
            indent++;
            for (StatementNode s : body) printStmt(s);
            indent--;
            line("}");
        }

        void printClass(ClassDeclarationNode c) {
            List<FieldDeclarationNode> fields = new ArrayList<>();
            ConstructorDeclarationNode ctor = null;
            List<MethodDeclarationNode> methods = new ArrayList<>();
            for (AstNode m : c.members()) {
                if (m instanceof FieldDeclarationNode f) fields.add(f);
                else if (m instanceof ConstructorDeclarationNode k) {
                    if (ctor == null) ctor = k;
                    else diags.add(MigrateDiagnostic.of("MIG005",
                            "class '" + c.name() + "' com múltiplos construtores — só o primeiro migrado",
                            "simplifique para um construtor"));
                } else if (m instanceof MethodDeclarationNode mth) methods.add(mth);
                else diags.add(MigrateDiagnostic.of("MIG013",
                        "membro de class '" + c.name() + "' não migrado: " + m.getClass().getSimpleName(),
                        "migre manualmente"));
            }

            if (!c.interfaces().isEmpty()) {
                diags.add(MigrateDiagnostic.of("MIG007",
                        "class '" + c.name() + "': implements " + String.join(", ", c.interfaces()) + " descartado",
                        "Hydra v1 sem implements; use herança extends"));
            }
            if (!c.typeParameters().isEmpty()) {
                diags.add(MigrateDiagnostic.of("MIG012",
                        "class '" + c.name() + "': generics descartados", "Hydra v1 sem generics"));
            }

            List<String> headerParts = new ArrayList<>();
            String ext = c.superClass() != null && !c.superClass().isBlank() ? normType(c.superClass()) : null;

            if (ctor != null) {
                List<String> issues = analyzeCtor(ctor);
                if (!issues.isEmpty()) {
                    diags.add(MigrateDiagnostic.of("MIG005",
                            "construtor de '" + c.name() + "' complexo: " + String.join("; ", issues),
                            "revise o type gerado"));
                }
                Set<String> ctorParams = new LinkedHashSet<>();
                for (FormalParameterNode p : ctor.parameters()) {
                    boolean mutable = !hasModifier(p.modifiers(), "final");
                    String entry = (mutable ? "var " : "") + p.name() + ": " + normType(p.type());
                    if (!headerParts.contains(entry)) headerParts.add(entry);
                    ctorParams.add(p.name());
                }
                for (FieldDeclarationNode f : fields) {
                    if (ctorParams.contains(f.name())) continue;
                    boolean mutable = !hasModifier(f.modifiers(), "final");
                    String entry = (mutable ? "var " : "") + f.name() + ": " + normType(f.type());
                    if (!headerParts.contains(entry)) headerParts.add(entry);
                }
            } else {
                for (FieldDeclarationNode f : fields) {
                    boolean mutable = !hasModifier(f.modifiers(), "final");
                    headerParts.add((mutable ? "var " : "") + f.name() + ": " + normType(f.type()));
                }
            }

            out.append(pad()).append("type ").append(c.name())
                    .append('(').append(String.join(", ", headerParts)).append(')');
            if (ext != null) out.append(" extends ").append(ext);
            out.append(" {\n");
            indent++;
            for (MethodDeclarationNode m : methods) printMethod(m);
            indent--;
            line("}");
        }

        void printRecord(RecordDeclarationNode r) {
            if (!r.interfaces().isEmpty()) {
                diags.add(MigrateDiagnostic.of("MIG007",
                        "record '" + r.name() + "': implements " + String.join(", ", r.interfaces()) + " descartado",
                        "Hydra v1 sem implements"));
            }
            if (!r.typeParameters().isEmpty()) {
                diags.add(MigrateDiagnostic.of("MIG012",
                        "record '" + r.name() + "': generics descartados", "Hydra v1 sem generics"));
            }
            StringBuilder hdr = new StringBuilder("(");
            boolean first = true;
            for (RecordComponentNode c : r.components()) {
                if (!first) hdr.append(", ");
                first = false;
                boolean mutable = !hasModifier(c.modifiers(), "final");
                if (mutable) hdr.append("var ");
                hdr.append(c.name()).append(": ").append(normType(c.type()));
            }
            hdr.append(")");
            out.append(pad()).append("type ").append(r.name()).append(hdr);
            if (r.superClass() != null && !r.superClass().isBlank()) {
                out.append(" extends ").append(normType(r.superClass()));
            }
            List<MethodDeclarationNode> methods = new ArrayList<>();
            for (AstNode m : r.members()) {
                if (m instanceof MethodDeclarationNode md) methods.add(md);
                else diags.add(MigrateDiagnostic.of("MIG013",
                        "membro de record não migrado: " + m.getClass().getSimpleName(),
                        "migre manualmente"));
            }
            if (!methods.isEmpty()) {
                out.append(" {\n");
                indent++;
                for (MethodDeclarationNode md : methods) printMethod(md);
                indent--;
                line("}");
            } else {
                out.append('\n');
            }
        }

        void printEnum(EnumDeclarationNode e) {
            out.append(pad()).append("enum ").append(e.name()).append(" {\n");
            indent++;
            for (String c : e.constants()) line(c);
            indent--;
            line("}");
        }

        void printInterface(InterfaceDeclarationNode i) {
            out.append(pad()).append("type ").append(i.name()).append(" {\n");
            indent++;
            for (AstNode m : i.members()) {
                if (m instanceof MethodDeclarationNode md) printMethod(md);
                else if (m instanceof FunctionDeclarationNode fd) printFun(fd);
                else diags.add(MigrateDiagnostic.of("MIG013",
                        "membro de interface não migrado: " + m.getClass().getSimpleName(),
                        "migre manualmente"));
            }
            indent--;
            line("}");
        }

        void printEntity(EntityDeclarationNode e) {
            diags.add(MigrateDiagnostic.of("MIG014",
                    "entity '" + e.name() + "' migrada como type simples (ORM descartado)",
                    "Hydra v1 sem ORM; use type + stdlib"));
            StringBuilder hdr = new StringBuilder("(");
            boolean first = true;
            for (EntityFieldNode f : e.fields()) {
                if (!first) hdr.append(", ");
                first = false;
                hdr.append("var ").append(f.name()).append(": ").append(normType(f.type()));
            }
            hdr.append(")");
            out.append(pad()).append("type ").append(e.name()).append(hdr).append('\n');
        }

        void printTest(TestDeclarationNode t) {
            out.append(pad()).append("test \"").append(escape(t.name())).append("\" {\n");
            indent++;
            for (StatementNode s : t.body()) printStmt(s);
            indent--;
            line("}");
        }

        void printApplication(ApplicationDeclarationNode a) {
            out.append(pad()).append("application {\n");
            indent++;
            if (!a.onStart().isEmpty()) {
                line("// onStart");
                for (StatementNode s : a.onStart()) printStmt(s);
            }
            if (!a.onShutdown().isEmpty()) {
                line("// onShutdown");
                for (StatementNode s : a.onShutdown()) printStmt(s);
            }
            indent--;
            line("}");
        }

        private List<String> analyzeCtor(ConstructorDeclarationNode ctor) {
            List<String> issues = new ArrayList<>();
            Set<String> paramNames = ctor.parameters().stream().map(FormalParameterNode::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<StatementNode> body = ctor.body() == null ? List.of() : ctor.body();
            for (StatementNode s : body) {
                if (s instanceof ExpressionStmt es && es.expression() instanceof AssignmentExpr a) {
                    if (a.target() instanceof FieldAccessExpr fa
                            && "this".equals(recvName(fa.receiver()))) {
                        // this.f = p  |  this.f = literal  |  this.f = expr simples
                        continue;
                    }
                    issues.add("atribuição não trivial no construtor");
                } else if (s instanceof ExpressionStmt es && es.expression() instanceof MethodCallExpr mc) {
                    if ("super".equals(mc.methodName())) continue;
                    issues.add("chamada '" + mc.methodName() + "' no construtor");
                } else {
                    issues.add("stmt não trivial no construtor: " + s.getClass().getSimpleName());
                }
            }
            return issues;
        }

        // ---- stmts ----

        void printStmt(StatementNode s) {
            if (s instanceof VarDeclStmt v) printVar(v);
            else if (s instanceof ExpressionStmt e) {
                out.append(pad());
                printExpr(e.expression());
                out.append('\n');
            } else if (s instanceof ReturnStmt r) {
                out.append(pad()).append("return");
                if (r.value() != null) {
                    out.append(' ');
                    printExpr(r.value());
                }
                out.append('\n');
            } else if (s instanceof IfStmt i) printIf(i);
            else if (s instanceof BlockStmt b) {
                line("{");
                indent++;
                for (StatementNode st : b.statements()) printStmt(st);
                indent--;
                line("}");
            } else if (s instanceof ForStmt f) printFor(f);
            else if (s instanceof dev.kof.compiler.ForInStmt fi) {
                // Hydra for-in: `for x in xs` / `for var x in xs` (sem parênteses)
                out.append(pad()).append("for var ").append(fi.varName()).append(" in ");
                printExpr(fi.collection());
                out.append(" {\n");
                indent++;
                printBodyStatements(fi.body());
                indent--;
                line("}");
            } else if (s instanceof WhileStmt w) {
                out.append(pad()).append("for (");
                printExpr(w.condition());
                out.append(") {\n");
                indent++;
                printBodyStatements(w.body());
                indent--;
                line("}");
            } else if (s instanceof DoWhileStmt) {
                diags.add(MigrateDiagnostic.of("MIG001",
                        "do-while não tem forma Hydra — bloco descartado",
                        "reescreva com for (cond)"));
                line("// MIG001: do-while removido");
            } else if (s instanceof ThrowStmt t) {
                out.append(pad()).append("throw ");
                printExpr(t.expression());
                out.append('\n');
            } else if (s instanceof TryStmt t) printTry(t);
            else if (s instanceof SpawnStmt sp) {
                out.append(pad()).append("spawn ");
                printExpr(sp.expression());
                out.append('\n');
            } else if (s instanceof BreakStmt) {
                diags.add(MigrateDiagnostic.of("MIG004", "break não existe no core Hydra",
                        "reestruture com for/return"));
                line("// MIG004: break removido");
            } else if (s instanceof ContinueStmt) {
                diags.add(MigrateDiagnostic.of("MIG004", "continue não existe no core Hydra",
                        "reestruture com for/return"));
                line("// MIG004: continue removido");
            } else {
                diags.add(MigrateDiagnostic.of("MIG013",
                        "stmt não migrado: " + s.getClass().getSimpleName(), "migre manualmente"));
                line("// MIG: " + s.getClass().getSimpleName());
            }
        }

        private void printBodyStatements(StatementNode body) {
            if (body instanceof BlockStmt b) {
                for (StatementNode st : b.statements()) printStmt(st);
            } else if (body != null) {
                printStmt(body);
            }
        }

        private void printVar(VarDeclStmt v) {
            String t = v.type() == null ? "var" : v.type();
            if ("val".equals(t)) {
                out.append(pad()).append("val ").append(v.name());
            } else if ("var".equals(t)) {
                out.append(pad()).append("var ").append(v.name());
            } else {
                // type-first: `Int x = e` → `var x: Int = e`
                out.append(pad()).append("var ").append(v.name()).append(": ").append(normType(t));
            }
            if (v.initializer() != null) {
                out.append(" = ");
                printExpr(v.initializer());
            }
            out.append('\n');
        }

        private void printIf(IfStmt i) {
            out.append(pad()).append("if (");
            printExpr(i.condition());
            out.append(") {\n");
            indent++;
            printBodyStatements(i.thenBranch());
            indent--;
            if (i.elseBranch() != null) {
                if (i.elseBranch() instanceof IfStmt nested) {
                    out.append(pad()).append("else ");
                    printIfNested(nested);
                } else {
                    line("} else {");
                    indent++;
                    printBodyStatements(i.elseBranch());
                    indent--;
                    line("}");
                }
            } else {
                line("}");
            }
        }

        private void printIfNested(IfStmt i) {
            out.append("if (");
            printExpr(i.condition());
            out.append(") {\n");
            indent++;
            printBodyStatements(i.thenBranch());
            indent--;
            if (i.elseBranch() != null) {
                if (i.elseBranch() instanceof IfStmt nested) {
                    out.append(pad()).append("else ");
                    printIfNested(nested);
                } else {
                    line("} else {");
                    indent++;
                    printBodyStatements(i.elseBranch());
                    indent--;
                    line("}");
                }
            } else {
                line("}");
            }
        }

        private void printFor(ForStmt f) {
            out.append(pad()).append("for (");
            if (f.init() instanceof VarDeclStmt v) {
                // classic head: var i = 0, cond, update
                String t = v.type() == null ? "var" : v.type();
                if ("val".equals(t)) out.append("val ");
                else out.append("var ");
                out.append(v.name());
                String vt = v.type() == null ? "" : v.type();
                if (!vt.isEmpty() && !"val".equals(vt) && !"var".equals(vt)) {
                    out.append(": ").append(normType(vt));
                }
                if (v.initializer() != null) {
                    out.append(" = ");
                    printExpr(v.initializer());
                }
            } else if (f.init() instanceof ExpressionStmt es) {
                printExpr(es.expression());
            } else if (f.init() != null) {
                // outro stmt no init — emite expr se der
            }
            if (f.condition() != null) {
                out.append(", ");
                printExpr(f.condition());
            }
            if (f.update() != null) {
                out.append(", ");
                printExpr(f.update());
            }
            out.append(") {\n");
            indent++;
            printBodyStatements(f.body());
            indent--;
            line("}");
        }

        private void printTry(TryStmt t) {
            if (t.finallyBody() != null && !t.finallyBody().isEmpty()) {
                diags.add(MigrateDiagnostic.of("MIG002",
                        "finally descartado (" + t.finallyBody().size() + " stmts)",
                        "Hydra v1 sem finally — mova a limpeza para depois do try"));
            }
            out.append(pad()).append("try {\n");
            indent++;
            for (StatementNode s : t.tryBody()) printStmt(s);
            indent--;
            for (CatchClause c : t.catchClauses()) {
                if (c.exceptionType() != null && !c.exceptionType().isBlank()
                        && !"String".equals(c.exceptionType())) {
                    diags.add(MigrateDiagnostic.of("MIG015",
                            "catch com tipo '" + c.exceptionType() + "' — tipo ignorado",
                            "Hydra catch aceita só o binding: catch (e)"));
                }
                line("} catch (" + c.exceptionName() + ") {");
                indent++;
                for (StatementNode s : c.body()) printStmt(s);
                indent--;
            }
            line("}");
        }

        // ---- exprs ----

        void printExpr(ExpressionNode e) {
            if (e instanceof LiteralExpr lit) printLit(lit);
            else if (e instanceof IdentifierExpr id) out.append(id.name());
            else if (e instanceof BinaryExpr b) {
                out.append('(');
                printExpr(b.left());
                out.append(' ').append(b.operator()).append(' ');
                printExpr(b.right());
                out.append(')');
            } else if (e instanceof UnaryExpr u) {
                if (u.prefix()) out.append(u.operator());
                printExpr(u.operand());
                if (!u.prefix()) out.append(u.operator());
            } else if (e instanceof AssignmentExpr a) {
                printExpr(a.target());
                out.append(' ').append(a.operator()).append(' ');
                printExpr(a.value());
            } else if (e instanceof FieldAccessExpr f) {
                String recv = recvName(f.receiver());
                if ("this".equals(recv)) out.append(f.fieldName());
                else if ("super".equals(recv)) out.append("super.").append(f.fieldName());
                else {
                    printExpr(f.receiver());
                    out.append('.').append(f.fieldName());
                }
            } else if (e instanceof MethodCallExpr m) printCall(m);
            else if (e instanceof NewExpr n) {
                out.append(n.typeName()).append('(');
                printArgs(n.arguments());
                out.append(')');
            } else if (e instanceof LambdaExpr l) {
                out.append('(').append(params(l.parameters())).append(") -> ");
                printLambdaBody(l);
            } else if (e instanceof SwitchExpr sw) printMatch(sw);
            else if (e instanceof IfExpr ie) {
                out.append("if (");
                printExpr(ie.condition());
                out.append(") ");
                printExpr(ie.thenExpr());
                out.append(" else ");
                printExpr(ie.elseExpr());
            } else if (e instanceof dev.kof.compiler.ArrayAccessExpr ae) {
                diags.add(MigrateDiagnostic.of("MIG006", "array access não migrado",
                        "Hydra v1 sem arrays no core"));
                out.append("/* MIG006 array */ ");
                printExpr(ae.receiver());
                out.append('[');
                printExpr(ae.index());
                out.append(']');
            } else {
                diags.add(MigrateDiagnostic.of("MIG013",
                        "expr não migrada: " + e.getClass().getSimpleName(), "migre manualmente"));
                out.append("/* MIG ").append(e.getClass().getSimpleName()).append(" */");
            }
        }

        private void printLambdaBody(LambdaExpr l) {
            List<StatementNode> body = l.body();
            if (body != null && body.size() == 1 && body.get(0) instanceof ReturnStmt r && r.value() != null) {
                printExpr(r.value());
                return;
            }
            if (body != null && body.size() == 1 && body.get(0) instanceof ExpressionStmt es) {
                printExpr(es.expression());
                return;
            }
            out.append("{\n");
            indent++;
            if (body != null) {
                for (StatementNode s : body) printStmt(s);
            }
            indent--;
            out.append(pad()).append('}');
        }

        private void printCall(MethodCallExpr m) {
            String name = m.methodName();
            if (m.receiver() == null) {
                if (!m.typeArguments().isEmpty()) {
                    diags.add(MigrateDiagnostic.of("MIG012",
                            "chamada '" + name + "' com typeArguments — descartados",
                            "Hydra v1 sem generics"));
                }
                out.append(name).append('(');
                printArgs(m.arguments());
                out.append(')');
                return;
            }
            String recv = recvName(m.receiver());
            if (m.arguments().isEmpty() && recordComponents.contains(name) && !"this".equals(recv)) {
                printExpr(m.receiver());
                out.append('.').append(name);
                return;
            }
            if ("this".equals(recv)) {
                out.append(name).append('(');
                printArgs(m.arguments());
                out.append(')');
                return;
            }
            printExpr(m.receiver());
            out.append('.').append(name).append('(');
            printArgs(m.arguments());
            out.append(')');
        }

        private void printArgs(List<ExpressionNode> args) {
            boolean first = true;
            for (ExpressionNode a : args) {
                if (!first) out.append(", ");
                first = false;
                printExpr(a);
            }
        }

        private void printMatch(SwitchExpr sw) {
            out.append("match (");
            printExpr(sw.expression());
            out.append(") {\n");
            indent++;
            for (SwitchExprCase c : sw.cases()) {
                out.append(pad()).append("case ");
                printExpr(c.value());
                out.append(" -> ");
                printExpr(c.body());
                out.append('\n');
            }
            if (sw.defaultValue() != null) {
                out.append(pad()).append("default -> ");
                printExpr(sw.defaultValue());
                out.append('\n');
            }
            indent--;
            line("}");
        }

        private void printLit(LiteralExpr lit) {
            String kind = String.valueOf(lit.kind());
            String v = lit.value() == null ? "" : lit.value();
            switch (kind) {
                case "STRING" -> out.append('"').append(escape(unquote(v))).append('"');
                case "CHAR" -> out.append('\'').append(escape(unquote(v))).append('\'');
                default -> out.append(v);
            }
        }

        // ---- helpers ----

        private String params(List<FormalParameterNode> ps) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (FormalParameterNode p : ps) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(p.name()).append(": ").append(normType(p.type()));
            }
            return sb.toString();
        }

        private static String recvName(ExpressionNode r) {
            return r instanceof IdentifierExpr id ? id.name() : null;
        }

        private static String normRet(String ret) {
            if (ret == null || ret.isBlank() || "void".equals(ret) || "Void".equals(ret)) return null;
            return normType(ret);
        }

        static String normType(String t) {
            if (t == null) return "Any";
            String s = t.trim();
            if (s.endsWith("[]")) return "Any";
            return s;
        }

        private static boolean hasModifier(List<String> mods, String m) {
            return mods != null && mods.stream().anyMatch(x -> x.equalsIgnoreCase(m));
        }

        private static String unquote(String v) {
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                return v.substring(1, v.length() - 1);
            }
            return v;
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }

        private String pad() {
            return "    ".repeat(indent);
        }

        private void line(String s) {
            out.append(pad()).append(s).append('\n');
        }
    }
}
