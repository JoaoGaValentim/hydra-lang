package hydra.compiler.parity;

import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.AstNode;
import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.BlockStmt;
import dev.kof.compiler.CatchClause;
import dev.kof.compiler.ClassDeclarationNode;
import dev.kof.compiler.CompilationUnitNode;
import dev.kof.compiler.ConstructorDeclarationNode;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.EnumDeclarationNode;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.FieldDeclarationNode;
import dev.kof.compiler.FormalParameterNode;
import dev.kof.compiler.FunctionDeclarationNode;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.IfStmt;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.LiteralExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.MethodDeclarationNode;
import dev.kof.compiler.RecordDeclarationNode;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.SwitchExpr;
import dev.kof.compiler.ThrowStmt;
import dev.kof.compiler.TryStmt;
import dev.kof.compiler.VarDeclStmt;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import hydra.compiler.ast.Ast;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F2-04 — paridade estrutural AST Hydra ↔ Kof.
 * Fonte pareada: mesma intenção, sintaxe de cada linguagem.
 * Comparação por shape canônico (ver hydra/PARITY.md).
 *
 * Normalizações (diferenças sintáticas conhecidas, não semânticas):
 * - chamada sem receiver: Kof `MethodCallExpr(null,…)` ↔ Hydra `CallExpr(IdentExpr,…)` → `call(name;…)`
 * - retorno ausente: Hydra `?` ↔ Kof `void` → `?`
 * - catch: só o nome do binding (Hydra não tem tipo; Kof tem `String`)
 * - type-decl: formas de campo/método diferem (header vs membros) — asserções estruturais, sem full-shape
 * - enum/match: `match` Hydra ↔ `switch` Kof — asserções de casos, sem full-shape
 */
class ParityTest {

    record Pair(String name, String hydraSrc, String kofSrc) {}

    static final List<Pair> PAIRS = List.of(
        new Pair("hello", """
                main() {
                    println("Hello, Hydra")
                }
                """, """
                main() {
                    println("Hello, Hydra");
                }
                """),
        new Pair("val-var", """
                main() {
                    val x = 10
                    var y = x + 5
                    y = y * 2
                    println(y)
                }
                """, """
                main() {
                    val x = 10;
                    var y = x + 5;
                    y = y * 2;
                    println(y);
                }
                """),
        new Pair("funcoes", """
                dobro(x: Int): Int {
                    return x * 2
                }

                saudar(nome: String): String {
                    return "Hello, " + nome
                }

                main() {
                    println(dobro(21))
                    println(saudar("Hydra"))
                }
                """, """
                Int dobro(Int x) {
                    return x * 2;
                }

                String saudar(String nome) {
                    return "Hello, " + nome;
                }

                main() {
                    println(dobro(21));
                    println(saudar("Hydra"));
                }
                """),
        new Pair("erros", """
                dividir(a: Int, b: Int): Int {
                    if (b == 0) {
                        throw "divisao por zero"
                    }
                    return a / b
                }

                main() {
                    try {
                        println(dividir(1, 0))
                    } catch (e) {
                        println("erro: " + e)
                    }
                }
                """, """
                Int dividir(Int a, Int b) {
                    if (b == 0) {
                        throw "divisao por zero";
                    }
                    return a / b;
                }

                main() {
                    try {
                        println(dividir(1, 0));
                    } catch (String e) {
                        println("erro: " + e);
                    }
                }
                """),
        new Pair("enum", """
                enum Color {
                    Red
                    Green
                    Blue
                }

                nome(c: Color): String {
                    match (c) {
                        case Color.Red -> "red"
                        default -> "?"
                    }
                }

                main() {
                    println(nome(Color.Red))
                }
                """, """
                enum Color {
                    Red, Green, Blue
                }

                String nome(Color c) {
                    return switch (c) {
                        case Color.Red -> "red"
                        default -> "?"
                    };
                }

                main() {
                    println(nome(Color.Red));
                }
                """),
        new Pair("type-membros", """
                type User(var name: String, var age: Int) {
                    greet(): String {
                        return "Hello, " + name
                    }
                }

                main() {
                    val u = User("Mel", 26)
                    println(u.greet())
                }
                """, """
                class User {
                    String name
                    Int age

                    constructor(String name, Int age) {
                        this.name = name
                        this.age = age
                    }

                    greet(): String {
                        return "Hello, " + name
                    }
                }

                main() {
                    val u = User("Mel", 26)
                    println(u.greet())
                }
                """),
        new Pair("lambdas", """
                aplicar(x: Int, f: (Int) -> Int): Int {
                    return f(x)
                }

                main() {
                    println(aplicar(5, (x: Int) -> x + 1))
                }
                """, """
                Int aplicar(Int x, Function1 f) {
                    return f.apply(x);
                }

                main() {
                    println(aplicar(5, (x: Int) -> x + 1));
                }
                """)
    );

    @Test
    void pairedSourcesParseOnBothSides() {
        for (Pair p : PAIRS) {
            Ast.Unit h = parseHydra(p.hydraSrc());
            assertFalse(h.decls().isEmpty(), p.name() + ": hydra vazio");
            CompilationUnitNode kof = parseKof(p.kofSrc());
            assertFalse(kof.declarations().isEmpty(), p.name() + ": kof vazio");
            String diags = kofErrors(p.kofSrc());
            assertTrue(diags.isEmpty(), p.name() + ": kof com erros: " + diags);
        }
    }

    @Test
    void helloParity() {
        assertParity("hello");
    }

    @Test
    void valVarParity() {
        assertParity("val-var");
    }

    @Test
    void funcoesParity() {
        assertParity("funcoes");
    }

    @Test
    void errosParity() {
        assertParity("erros");
    }

    @Test
    void enumParity() {
        Pair p = pair("enum");
        Ast.Unit h = parseHydra(p.hydraSrc());
        Ast.EnumDecl e = (Ast.EnumDecl) h.decls().get(0);
        assertEquals(List.of("Red", "Green", "Blue"), e.cases());
        CompilationUnitNode kof = parseKof(p.kofSrc());
        EnumDeclarationNode ke = kof.declarations().stream()
                .filter(EnumDeclarationNode.class::isInstance)
                .map(EnumDeclarationNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(e.cases(), ke.constants());
    }

    @Test
    void typeMembersParity() {
        Pair p = pair("type-membros");
        Ast.Unit h = parseHydra(p.hydraSrc());
        Ast.TypeDecl t = (Ast.TypeDecl) h.decls().get(0);
        assertEquals("User", t.name());
        assertEquals(2, t.fields().size());
        assertEquals(1, t.methods().size());
        assertEquals("greet", t.methods().get(0).name());
        assertTrue(t.fields().stream().anyMatch(f -> f.name().equals("name")));
        assertTrue(t.fields().stream().anyMatch(f -> f.name().equals("age")));

        CompilationUnitNode kof = parseKof(p.kofSrc());
        ClassDeclarationNode kc = kof.declarations().stream()
                .filter(ClassDeclarationNode.class::isInstance)
                .map(ClassDeclarationNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("User", kc.name());
        assertTrue(kc.members().stream()
                        .filter(FieldDeclarationNode.class::isInstance)
                        .map(FieldDeclarationNode.class::cast)
                        .anyMatch(f -> f.name().equals("name")));
        assertTrue(kc.members().stream()
                        .filter(FieldDeclarationNode.class::isInstance)
                        .map(FieldDeclarationNode.class::cast)
                        .anyMatch(f -> f.name().equals("age")));
        assertTrue(kc.members().stream()
                        .filter(MethodDeclarationNode.class::isInstance)
                        .map(MethodDeclarationNode.class::cast)
                        .anyMatch(m -> m.name().equals("greet")));
        assertTrue(kc.members().stream().anyMatch(ConstructorDeclarationNode.class::isInstance),
                "Kof class com ctor primário deve ter ConstructorDeclarationNode");
    }

    @Test
    void lambdasParity() {
        Pair p = pair("lambdas");
        Ast.Unit h = parseHydra(p.hydraSrc());
        Ast.FunDecl main = (Ast.FunDecl) h.decls().get(1);
        Ast.ExprStmt stmt = (Ast.ExprStmt) main.body().stmts().get(0);
        Ast.CallExpr println = (Ast.CallExpr) stmt.expr();
        Ast.CallExpr aplicar = (Ast.CallExpr) println.args().get(0);
        assertInstanceOf(Ast.LambdaExpr.class, aplicar.args().get(1));
        assertEquals(1, ((Ast.LambdaExpr) aplicar.args().get(1)).params().size());

        CompilationUnitNode kof = parseKof(p.kofSrc());
        FunctionDeclarationNode kMain = kof.declarations().stream()
                .filter(FunctionDeclarationNode.class::isInstance)
                .map(FunctionDeclarationNode.class::cast)
                .filter(f -> "main".equals(f.name()))
                .findFirst()
                .orElseThrow();
        ExpressionStmt kStmt = (ExpressionStmt) kMain.body().get(0);
        MethodCallExpr kPrintln = (MethodCallExpr) kStmt.expression();
        assertEquals("println", kPrintln.methodName());
        MethodCallExpr kAplicar = (MethodCallExpr) kPrintln.arguments().get(0);
        assertEquals("aplicar", kAplicar.methodName());
        assertInstanceOf(LambdaExpr.class, kAplicar.arguments().get(1));
        assertEquals(1, ((LambdaExpr) kAplicar.arguments().get(1)).parameters().size());
    }

    @Test
    void functionShapeParity() {
        Pair p = pair("funcoes");
        String hydraShape = hydraShapes(parseHydra(p.hydraSrc()));
        String kofShape = kofShapes(parseKof(p.kofSrc()));
        assertEquals(extractFuns(hydraShape), extractFuns(kofShape));
    }

    private static void assertParity(String name) {
        Pair p = pair(name);
        String hydraShape = hydraShapes(parseHydra(p.hydraSrc()));
        String kofShape = kofShapes(parseKof(p.kofSrc()));
        assertEquals(hydraShape, kofShape,
                "paridade de shape falhou para " + name + "\nHYDRA:\n" + hydraShape + "\nKOF:\n" + kofShape);
    }

    private static Pair pair(String name) {
        return PAIRS.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    private static Ast.Unit parseHydra(String src) {
        return hydra.compiler.parser.Parser.parse(src);
    }

    private static CompilationUnitNode parseKof(String src) {
        DiagnosticCollector diags = new DiagnosticCollector();
        Lexer lx = new Lexer(src, "Pair.kf", diags);
        return new Parser(lx.tokenize(), diags, "Pair.kf").parse();
    }

    private static String kofErrors(String src) {
        DiagnosticCollector diags = new DiagnosticCollector();
        Lexer lx = new Lexer(src, "Pair.kf", diags);
        new Parser(lx.tokenize(), diags, "Pair.kf").parse();
        return diags.getDiagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                .map(d -> d.file() + ":" + d.line() + ":" + d.column() + " " + d.message())
                .collect(Collectors.joining("\n"));
    }

    // ---------- shapes Kof ----------

    private static String kofShapes(CompilationUnitNode unit) {
        StringBuilder sb = new StringBuilder();
        for (AstNode d : unit.declarations()) shapeKof(d, sb, 0);
        return sb.toString();
    }

    private static void shapeKof(AstNode n, StringBuilder sb, int ind) {
        String pad = "  ".repeat(ind);
        if (n instanceof FunctionDeclarationNode f) {
            sb.append(pad).append("fun(").append(f.name());
            for (FormalParameterNode p : f.parameters()) {
                sb.append("; ").append(p.name()).append(':').append(p.type());
            }
            sb.append("; ret=").append(normRet(f.returnType())).append(") {\n");
            for (StatementNode s : f.body()) shapeKof(s, sb, ind + 1);
            sb.append(pad).append("}\n");
        } else if (n instanceof ClassDeclarationNode c) {
            sb.append(pad).append("class(").append(c.name());
            if (c.superClass() != null && !c.superClass().isEmpty()) {
                sb.append("; extends=").append(c.superClass());
            }
            sb.append(") {\n");
            for (AstNode m : c.members()) shapeKof(m, sb, ind + 1);
            sb.append(pad).append("}\n");
        } else if (n instanceof RecordDeclarationNode r) {
            sb.append(pad).append("record(").append(r.name()).append(") {\n");
            for (AstNode m : r.members()) shapeKof(m, sb, ind + 1);
            sb.append(pad).append("}\n");
        } else if (n instanceof EnumDeclarationNode e) {
            sb.append(pad).append("enum(").append(e.name()).append("; ")
                    .append(String.join(",", e.constants())).append(")\n");
        } else if (n instanceof FieldDeclarationNode f) {
            sb.append(pad).append("field(").append(f.name()).append(':').append(f.type()).append(")\n");
        } else if (n instanceof MethodDeclarationNode m) {
            sb.append(pad).append("fun(").append(m.name());
            for (FormalParameterNode p : m.parameters()) {
                sb.append("; ").append(p.name()).append(':').append(p.type());
            }
            sb.append("; ret=").append(normRet(m.returnType())).append(") {\n");
            if (m.body() != null) {
                for (StatementNode s : m.body()) shapeKof(s, sb, ind + 1);
            }
            sb.append(pad).append("}\n");
        } else if (n instanceof ConstructorDeclarationNode c) {
            sb.append(pad).append("ctor(").append(c.name());
            for (FormalParameterNode p : c.parameters()) {
                sb.append("; ").append(p.name()).append(':').append(p.type());
            }
            sb.append(") {\n");
            if (c.body() != null) {
                for (StatementNode s : c.body()) shapeKof(s, sb, ind + 1);
            }
            sb.append(pad).append("}\n");
        } else if (n instanceof ReturnStmt r) {
            sb.append(pad).append("return(");
            if (r.value() != null) shapeKofExpr(r.value(), sb);
            sb.append(")\n");
        } else if (n instanceof ExpressionStmt e) {
            sb.append(pad).append("expr(");
            shapeKofExpr(e.expression(), sb);
            sb.append(")\n");
        } else if (n instanceof VarDeclStmt v) {
            sb.append(pad).append("var(").append(v.type()).append("; ").append(v.name()).append("; ");
            if (v.initializer() != null) shapeKofExpr(v.initializer(), sb);
            else sb.append("null");
            sb.append(")\n");
        } else if (n instanceof IfStmt i) {
            sb.append(pad).append("if(");
            shapeKofExpr(i.condition(), sb);
            sb.append(") {\n");
            shapeKofBranch(i.thenBranch(), sb, ind + 1);
            sb.append(pad).append("}");
            if (i.elseBranch() != null) {
                sb.append(" else {\n");
                shapeKofBranch(i.elseBranch(), sb, ind + 1);
                sb.append(pad).append("}");
            }
            sb.append('\n');
        } else if (n instanceof ThrowStmt t) {
            sb.append(pad).append("throw(");
            shapeKofExpr(t.expression(), sb);
            sb.append(")\n");
        } else if (n instanceof TryStmt t) {
            sb.append(pad).append("try {\n");
            for (StatementNode s : t.tryBody()) shapeKof(s, sb, ind + 1);
            for (CatchClause c : t.catchClauses()) {
                // normaliza: só o nome (Hydra catch não tem tipo)
                sb.append(pad).append("} catch(").append(c.exceptionName()).append(") {\n");
                for (StatementNode s : c.body()) shapeKof(s, sb, ind + 1);
            }
            sb.append(pad).append("}\n");
        } else if (n instanceof BlockStmt b) {
            sb.append(pad).append("block {\n");
            for (StatementNode s : b.statements()) shapeKof(s, sb, ind + 1);
            sb.append(pad).append("}\n");
        } else {
            sb.append(pad).append("kof:").append(n.getClass().getSimpleName()).append("\n");
        }
    }

    private static void shapeKofBranch(StatementNode s, StringBuilder sb, int ind) {
        if (s instanceof BlockStmt b) {
            for (StatementNode st : b.statements()) shapeKof(st, sb, ind);
        } else {
            shapeKof(s, sb, ind);
        }
    }

    private static String normRet(String ret) {
        if (ret == null || ret.isEmpty() || "void".equals(ret) || "Void".equals(ret)) return "?";
        return ret;
    }

    private static void shapeKofExpr(ExpressionNode e, StringBuilder sb) {
        if (e instanceof IdentifierExpr id) {
            sb.append("id(").append(id.name()).append(')');
        } else if (e instanceof LiteralExpr lit) {
            sb.append("lit(").append(lit.kind()).append("; ").append(lit.value()).append(')');
        } else if (e instanceof BinaryExpr b) {
            sb.append("bin(").append(b.operator()).append("; ");
            shapeKofExpr(b.left(), sb);
            sb.append("; ");
            shapeKofExpr(b.right(), sb);
            sb.append(')');
        } else if (e instanceof AssignmentExpr a) {
            sb.append("assign(").append(a.operator()).append("; ");
            shapeKofExpr(a.target(), sb);
            sb.append("; ");
            shapeKofExpr(a.value(), sb);
            sb.append(')');
        } else if (e instanceof FieldAccessExpr f) {
            sb.append("field(");
            shapeKofExpr(f.receiver(), sb);
            sb.append("; ").append(f.fieldName()).append(')');
        } else if (e instanceof MethodCallExpr m) {
            sb.append("call(");
            if (m.receiver() == null) sb.append(m.methodName());
            else {
                shapeKofExpr(m.receiver(), sb);
                sb.append('.').append(m.methodName());
            }
            for (ExpressionNode a : m.arguments()) {
                sb.append("; ");
                shapeKofExpr(a, sb);
            }
            sb.append(')');
        } else if (e instanceof LambdaExpr l) {
            sb.append("lambda(");
            for (FormalParameterNode p : l.parameters()) {
                sb.append(p.name()).append(':').append(p.type()).append(',');
            }
            sb.append("body)");
        } else if (e instanceof SwitchExpr sw) {
            sb.append("switch(");
            shapeKofExpr(sw.expression(), sb);
            for (var c : sw.cases()) {
                sb.append("; case ");
                shapeKofExpr(c.value(), sb);
                sb.append(" -> ");
                shapeKofExpr(c.body(), sb);
            }
            if (sw.defaultValue() != null) {
                sb.append("; default -> ");
                shapeKofExpr(sw.defaultValue(), sb);
            }
            sb.append(')');
        } else {
            sb.append("kofexpr:").append(e.getClass().getSimpleName());
        }
    }

    // ---------- shapes Hydra ----------

    private static String hydraShapes(Ast.Unit unit) {
        StringBuilder sb = new StringBuilder();
        for (Ast.Decl d : unit.decls()) shapeHydra(d, sb, 0);
        return sb.toString();
    }

    private static void shapeHydra(Ast.Node n, StringBuilder sb, int ind) {
        String pad = "  ".repeat(ind);
        if (n instanceof Ast.FunDecl f) {
            sb.append(pad).append("fun(").append(f.name());
            for (Ast.Param p : f.params()) {
                sb.append("; ").append(p.name()).append(':').append(p.type().name());
            }
            sb.append("; ret=").append(f.returnType() == null ? "?" : f.returnType().name()).append(") {\n");
            if (f.body() != null) {
                for (Ast.Stmt s : f.body().stmts()) shapeHydra(s, sb, ind + 1);
            }
            sb.append(pad).append("}\n");
        } else if (n instanceof Ast.TypeDecl t) {
            sb.append(pad).append("class(").append(t.name());
            if (t.extendsType() != null) sb.append("; extends=").append(t.extendsType().name());
            sb.append(") {\n");
            for (Ast.Field f : t.fields()) shapeHydra(f, sb, ind + 1);
            for (Ast.FunDecl m : t.methods()) shapeHydra(m, sb, ind + 1);
            sb.append(pad).append("}\n");
        } else if (n instanceof Ast.EnumDecl e) {
            sb.append(pad).append("enum(").append(e.name()).append("; ")
                    .append(String.join(",", e.cases())).append(")\n");
        } else if (n instanceof Ast.Field f) {
            sb.append(pad).append("field(").append(f.name()).append(':').append(f.type().name()).append(")\n");
        } else if (n instanceof Ast.ReturnStmt r) {
            sb.append(pad).append("return(");
            if (r.value() != null) shapeHydraExpr(r.value(), sb);
            sb.append(")\n");
        } else if (n instanceof Ast.ExprStmt e) {
            sb.append(pad).append("expr(");
            shapeHydraExpr(e.expr(), sb);
            sb.append(")\n");
        } else if (n instanceof Ast.VarDecl v) {
            sb.append(pad).append("var(").append(v.mutable() ? "var" : "val").append("; ").append(v.name()).append("; ");
            if (v.init() != null) shapeHydraExpr(v.init(), sb);
            else sb.append("null");
            sb.append(")\n");
        } else if (n instanceof Ast.IfStmt i) {
            sb.append(pad).append("if(");
            shapeHydraExpr(i.cond(), sb);
            sb.append(") {\n");
            for (Ast.Stmt s : i.thenBlock().stmts()) shapeHydra(s, sb, ind + 1);
            sb.append(pad).append("}");
            if (i.elseBlock() != null) {
                sb.append(" else {\n");
                for (Ast.Stmt s : i.elseBlock().stmts()) shapeHydra(s, sb, ind + 1);
                sb.append(pad).append("}");
            }
            sb.append('\n');
        } else if (n instanceof Ast.ThrowStmt t) {
            sb.append(pad).append("throw(");
            shapeHydraExpr(t.value(), sb);
            sb.append(")\n");
        } else if (n instanceof Ast.TryStmt t) {
            sb.append(pad).append("try {\n");
            for (Ast.Stmt s : t.body().stmts()) shapeHydra(s, sb, ind + 1);
            for (Ast.CatchClause c : t.catches()) {
                sb.append(pad).append("} catch(").append(c.name()).append(") {\n");
                for (Ast.Stmt s : c.body().stmts()) shapeHydra(s, sb, ind + 1);
            }
            sb.append(pad).append("}\n");
        } else if (n instanceof Ast.Block b) {
            sb.append(pad).append("block {\n");
            for (Ast.Stmt s : b.stmts()) shapeHydra(s, sb, ind + 1);
            sb.append(pad).append("}\n");
        } else {
            sb.append(pad).append("hydra:").append(n.getClass().getSimpleName()).append("\n");
        }
    }

    private static void shapeHydraExpr(Ast.Expr e, StringBuilder sb) {
        if (e instanceof Ast.IdentExpr id) {
            sb.append("id(").append(id.name()).append(')');
        } else if (e instanceof Ast.IntLit i) {
            sb.append("lit(INT; ").append(i.value()).append(')');
        } else if (e instanceof Ast.FloatLit f) {
            sb.append("lit(FLOAT; ").append(f.value()).append(')');
        } else if (e instanceof Ast.StringLit s) {
            sb.append("lit(STRING; ").append(s.value()).append(')');
        } else if (e instanceof Ast.BoolLit b) {
            sb.append("lit(BOOLEAN; ").append(b.value()).append(')');
        } else if (e instanceof Ast.NullLit) {
            sb.append("lit(NULL; null)");
        } else if (e instanceof Ast.BinaryExpr b) {
            sb.append("bin(").append(b.op()).append("; ");
            shapeHydraExpr(b.left(), sb);
            sb.append("; ");
            shapeHydraExpr(b.right(), sb);
            sb.append(')');
        } else if (e instanceof Ast.AssignExpr a) {
            sb.append("assign(").append(a.op()).append("; ");
            shapeHydraExpr(a.target(), sb);
            sb.append("; ");
            shapeHydraExpr(a.value(), sb);
            sb.append(')');
        } else if (e instanceof Ast.FieldExpr f) {
            sb.append("field(");
            shapeHydraExpr(f.receiver(), sb);
            sb.append("; ").append(f.name()).append(')');
        } else if (e instanceof Ast.CallExpr c) {
            sb.append("call(");
            // normaliza: callee IdentExpr → nome cru (igual MethodCallExpr(null,…) do Kof)
            if (c.callee() instanceof Ast.IdentExpr id) {
                sb.append(id.name());
            } else {
                shapeHydraExpr(c.callee(), sb);
            }
            for (Ast.Expr a : c.args()) {
                sb.append("; ");
                shapeHydraExpr(a, sb);
            }
            sb.append(')');
        } else if (e instanceof Ast.LambdaExpr l) {
            sb.append("lambda(");
            for (Ast.Param p : l.params()) {
                sb.append(p.name()).append(':').append(p.type().name()).append(',');
            }
            sb.append("body)");
        } else if (e instanceof Ast.IfExpr i) {
            sb.append("if(");
            shapeHydraExpr(i.cond(), sb);
            sb.append("; ");
            shapeHydraExpr(i.thenExpr(), sb);
            sb.append("; ");
            if (i.elseExpr() != null) shapeHydraExpr(i.elseExpr(), sb);
            else sb.append("null");
            sb.append(')');
        } else {
            sb.append("hydraexpr:").append(e.getClass().getSimpleName());
        }
    }

    private static List<String> extractFuns(String shapes) {
        List<String> funs = new ArrayList<>();
        for (String line : shapes.split("\n")) {
            String t = line.trim();
            if (t.startsWith("fun(")) funs.add(t);
        }
        return funs;
    }
}
