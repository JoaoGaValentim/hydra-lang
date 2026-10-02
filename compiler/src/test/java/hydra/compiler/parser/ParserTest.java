package hydra.compiler.parser;

import hydra.compiler.ast.Ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private static Unit parse(String src) {
        return Parser.parse(src);
    }

    @Test
    void parsesHello() {
        Unit u = parse("""
                main() {
                    println("Hello, Hydra")
                }
                """);
        assertEquals(1, u.decls().size());
        FunDecl main = (FunDecl) u.decls().get(0);
        assertEquals("main", main.name());
        assertTrue(main.params().isEmpty());
        assertEquals(1, main.body().stmts().size());
        ExprStmt stmt = (ExprStmt) main.body().stmts().get(0);
        CallExpr call = (CallExpr) stmt.expr();
        assertEquals("println", ((IdentExpr) call.callee()).name());
        assertEquals(1, call.args().size());
        assertEquals("Hello, Hydra", ((StringLit) call.args().get(0)).value());
    }

    @Test
    void parsesValVar() {
        Unit u = parse("""
                main() {
                    val x = 10
                    var y = x + 5
                    y = y * 2
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        VarDecl v1 = (VarDecl) main.body().stmts().get(0);
        assertFalse(v1.mutable());
        assertEquals("x", v1.name());
        VarDecl v2 = (VarDecl) main.body().stmts().get(1);
        assertTrue(v2.mutable());
        AssignExpr as = (AssignExpr) ((ExprStmt) main.body().stmts().get(2)).expr();
        assertEquals("=", as.op());
    }

    @Test
    void parsesFunctionsWithTypes() {
        Unit u = parse("""
                dobro(x: Int): Int {
                    return x * 2
                }

                saudar(nome: String): String = "Hello, " + nome
                """);
        assertEquals(2, u.decls().size());
        FunDecl d = (FunDecl) u.decls().get(0);
        assertEquals("dobro", d.name());
        assertEquals("Int", d.params().get(0).type().name());
        assertEquals("Int", d.returnType().name());
        FunDecl s = (FunDecl) u.decls().get(1);
        assertEquals("saudar", s.name());
        assertEquals("String", s.returnType().name());
    }

    @Test
    void parsesTypeImmutable() {
        Unit u = parse("""
                type Point(x: Int, y: Int)
                """);
        TypeDecl t = (TypeDecl) u.decls().get(0);
        assertEquals("Point", t.name());
        assertNull(t.extendsType());
        assertEquals(2, t.fields().size());
        assertEquals("x", t.fields().get(0).name());
        assertEquals("Int", t.fields().get(0).type().name());
    }

    @Test
    void parsesTypeWithMethodsAndExtends() {
        Unit u = parse("""
                type Animal(name: String) {
                    som(): String {
                        return "..."
                    }
                }

                type Dog(name: String) extends Animal {
                    som(): String {
                        return "au"
                    }
                }
                """);
        TypeDecl dog = (TypeDecl) u.decls().get(1);
        assertEquals("Dog", dog.name());
        assertEquals("Animal", dog.extendsType().name());
        assertEquals(1, dog.methods().size());
    }

    @Test
    void parsesForIn() {
        Unit u = parse("""
                main() {
                    val xs = listOf(1, 2, 3)
                    for x in xs {
                        println(x)
                    }
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        ForStmt f = (ForStmt) main.body().stmts().get(1);
        ForInHead head = (ForInHead) f.head();
        assertEquals("x", head.name());
        assertEquals("xs", ((IdentExpr) head.iter()).name());
    }

    @Test
    void parsesForClassicWithCommas() {
        Unit u = parse("""
                main() {
                    for (var i = 0, i < 3, i += 1) {
                        println(i)
                    }
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        ForStmt f = (ForStmt) main.body().stmts().get(0);
        ForClassicHead head = (ForClassicHead) f.head();
        assertEquals("i", head.init().name());
        assertNotNull(head.cond());
        assertNotNull(head.update());
        assertEquals("+=", ((AssignExpr) head.update()).op());
    }

    @Test
    void parsesForCond() {
        Unit u = parse("""
                main() {
                    var i = 0
                    for (i < 3) {
                        i += 1
                    }
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        ForStmt f = (ForStmt) main.body().stmts().get(1);
        assertInstanceOf(ForCondHead.class, f.head());
    }

    @Test
    void parsesMatch() {
        Unit u = parse("""
                main() {
                    val n = match (2) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        VarDecl v = (VarDecl) main.body().stmts().get(0);
        MatchExpr m = (MatchExpr) v.init();
        assertEquals(3, m.stmt().cases().size());
        assertEquals("2", m.stmt().cases().get(1).patternType());
    }

    @Test
    void parsesMatchBindingAndEnum() {
        Unit u = parse("""
                main() {
                    match (c) {
                        case Color.Red -> "red"
                        case String s -> s
                        default -> "?"
                    }
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        ExprStmt stmt = (ExprStmt) main.body().stmts().get(0);
        MatchExpr m = (MatchExpr) stmt.expr();
        assertEquals("Color.Red", m.stmt().cases().get(0).patternType());
        assertEquals("String", m.stmt().cases().get(1).patternType());
        assertEquals("s", m.stmt().cases().get(1).patternName());
    }

    @Test
    void parsesNullableAndIf() {
        Unit u = parse("""
                main() {
                    val s: String? = find()
                    if (s != null) {
                        println(s.length)
                    }
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        VarDecl v = (VarDecl) main.body().stmts().get(0);
        assertTrue(v.type().nullable());
        IfStmt ifs = (IfStmt) main.body().stmts().get(1);
        BinaryExpr cond = (BinaryExpr) ifs.cond();
        assertEquals("!=", cond.op());
    }

    @Test
    void parsesErrors() {
        Unit u = parse("""
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
                """);
        FunDecl div = (FunDecl) u.decls().get(0);
        IfStmt ifs = (IfStmt) div.body().stmts().get(0);
        assertInstanceOf(ThrowStmt.class, ifs.thenBlock().stmts().get(0));
        FunDecl main = (FunDecl) u.decls().get(1);
        TryStmt t = (TryStmt) main.body().stmts().get(0);
        assertEquals(1, t.catches().size());
        assertEquals("e", t.catches().get(0).name());
    }

    @Test
    void parsesLambda() {
        Unit u = parse("""
                main() {
                    val dobro = (x: Int) -> x * 2
                }
                """);
        FunDecl main = (FunDecl) u.decls().get(0);
        VarDecl v = (VarDecl) main.body().stmts().get(0);
        LambdaExpr lam = (LambdaExpr) v.init();
        assertEquals(1, lam.params().size());
        assertNotNull(lam.exprBody());
    }

    @Test
    void parsesEnumDeclAsType() {
        // enum ainda não é implementado no parser; garante erro claro
        assertThrows(SyntaxError.class, () -> parse("enum Color { Red }"));
    }

    @Test
    void syntaxErrorHasPosition() {
        SyntaxError e = assertThrows(SyntaxError.class, () -> parse("main( {\n}"));
        assertTrue(e.line() >= 1);
        assertTrue(e.col() >= 1);
        assertTrue(e.getMessage().contains("("));
    }

    @Test
    void parsesImport() {
        Unit u = parse("""
                import kof.io

                main() {
                    println(readLine())
                }
                """);
        assertEquals(1, u.imports().size());
        assertEquals(List.of("kof", "io"), u.imports().get(0).parts());
    }

    @Test
    void parsesAllSimpleExamples() throws Exception {
        var dir = java.nio.file.Path.of("../hydra/exemplos");
        var files = java.util.stream.Stream.of(
                        "01-hello.hy", "02-val-var.hy", "03-funcoes.hy",
                        "04-tipos-immutable.hy", "11-for-colecao.hy",
                        "12-for-contagem.hy", "13-for-condicao.hy",
                        "14-strings.hy", "16-erros.hy", "17-lambdas.hy",
                        "27-mat-comparacoes.hy", "28-assign-composto.hy",
                        "33-funcao-expressao.hy", "34-type-mutavel.hy",
                        "38-comentarios.hy", "39-bitwise.hy"
                )
                .map(dir::resolve)
                .toList();
        for (var f : files) {
            String src = java.nio.file.Files.readString(f);
            Unit u = Parser.parse(src);
            assertNotNull(u, f.toString());
            assertFalse(u.decls().isEmpty(), f.toString());
        }
    }
}
