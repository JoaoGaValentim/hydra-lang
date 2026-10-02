package hydra.compiler.ir;

import hydra.compiler.ast.Ast;
import hydra.compiler.ir.Ir.Method;
import hydra.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3-01 — IR a partir da AST Hydra.
 */
class IrTest {

    private static Ir.Module ir(String src) {
        Ast.Unit unit = Parser.parse(src);
        return new IrBuilder().build(unit, "Test");
    }

    private static Method method(Ir.Module m, String name) {
        return m.classes().get(0).methods().stream()
                .filter(x -> x.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("método não encontrado: " + name));
    }

    @Test
    void helloWorldShape() {
        Ir.Module m = ir("""
                main() {
                    println("Hello, Hydra")
                }
                """);
        assertEquals("Test", m.name());
        assertEquals(1, m.classes().size());
        Ir.Class main = m.classes().get(0);
        assertEquals("Main", main.name());
        assertEquals(1, main.methods().size());

        Method mainM = method(m, "main");
        assertTrue(mainM.returnType().isVoid());
        assertTrue(mainM.parameterTypes().isEmpty());

        String shape = Ir.shape(m);
        assertTrue(shape.contains("module(Test)"), shape);
        assertTrue(shape.contains("class(Main"), shape);
        assertTrue(shape.contains("method(main"), shape);
        assertTrue(shape.contains("loadLit(String; Hello, Hydra)"), shape);
        assertTrue(shape.contains("call(println; String; ret=Void)"), shape);
        assertTrue(shape.contains("return(Void)"), shape);
    }

    @Test
    void valVarAndAssign() {
        Ir.Module m = ir("""
                main() {
                    val x = 10
                    var y = x + 5
                    y = y * 2
                    println(y)
                }
                """);
        Method mainM = method(m, "main");
        assertEquals(2, mainM.locals().size());
        assertEquals("x", mainM.locals().get(0).name());
        assertEquals("y", mainM.locals().get(1).name());
        assertTrue(Ir.Type.INT.equals(mainM.locals().get(0).type()));
        assertTrue(Ir.Type.INT.equals(mainM.locals().get(1).type()));

        String shape = Ir.shape(m);
        assertTrue(shape.contains("loadLit(Int; 10)"), shape);
        assertTrue(shape.contains("storeLocal(0; Int)"), shape);
        assertTrue(shape.contains("binary(+; Int)"), shape);
        assertTrue(shape.contains("binary(*; Int)"), shape);
        assertTrue(shape.contains("storeLocal(1; Int)"), shape);
        assertTrue(shape.contains("loadLocal(1; Int)"), shape);
    }

    @Test
    void functionWithParamsAndReturn() {
        Ir.Module m = ir("""
                dobro(x: Int): Int {
                    return x * 2
                }

                main() {
                    println(dobro(21))
                }
                """);
        Method dobro = method(m, "dobro");
        assertEquals(1, dobro.parameterTypes().size());
        assertTrue(Ir.Type.INT.equals(dobro.parameterTypes().get(0)));
        assertTrue(Ir.Type.INT.equals(dobro.returnType()));
        assertEquals(1, dobro.locals().size());
        assertEquals("x", dobro.locals().get(0).name());

        String shape = Ir.shape(m);
        assertTrue(shape.contains("method(dobro; Int; ret=Int)"), shape);
        assertTrue(shape.contains("loadLocal(0; Int)"), shape);
        assertTrue(shape.contains("loadLit(Int; 2)"), shape);
        assertTrue(shape.contains("binary(*; Int)"), shape);
        assertTrue(shape.contains("return(Int)"), shape);
        assertTrue(shape.contains("call(dobro; Int; ret=Int)"), shape);
    }

    @Test
    void ifStatementProducesJumps() {
        Ir.Module m = ir("""
                dividir(a: Int, b: Int): Int {
                    if (b == 0) {
                        throw "divisao por zero"
                    }
                    return a / b
                }

                main() {
                    println(dividir(1, 2))
                }
                """);
        Method div = method(m, "dividir");
        String shape = Ir.shape(m);
        assertTrue(shape.contains("binary(==; Int)"), shape);
        assertTrue(shape.contains("jumpIfFalse("), shape);
        assertTrue(shape.contains("throw"), shape);
        assertTrue(shape.contains("binary(/; Int)"), shape);
        assertTrue(div.blocks().size() >= 3, shape);
    }

    @Test
    void tryCatchLowersHandlers() {
        Ir.Module m = ir("""
                main() {
                    try {
                        println("x")
                    } catch (e) {
                        println(e)
                    }
                }
                """);
        String shape = Ir.shape(m);
        assertTrue(shape.contains("tryStart"), shape);
        assertTrue(shape.contains("tryEnd"), shape);
        assertTrue(shape.contains("catchStart("), shape);
        assertTrue(shape.contains("local"), shape);
        Method mainM = method(m, "main");
        assertTrue(mainM.locals().stream().anyMatch(l -> l.name().equals("e")), shape);
    }

    @Test
    void enumAndMatch() {
        Ir.Module m = ir("""
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
                """);
        // enum vira classe com casos
        assertTrue(m.classes().stream().anyMatch(c -> c.name().equals("Color")
                        && c.enumCases().contains("Red")),
                Ir.shape(m));
        String shape = Ir.shape(m);
        assertTrue(shape.contains("enum=Red,Green,Blue"), shape);
        assertTrue(shape.contains("loadEnum(Color.Red)"), shape);
        assertTrue(shape.contains("call(nome; Color; ret=String)"), shape);
    }

    @Test
    void typeDeclLowersFieldsAndMethods() {
        Ir.Module m = ir("""
                type User(var name: String, var age: Int) {
                    greet(): String {
                        return "Hello, " + name
                    }
                }

                main() {
                    println("ok")
                }
                """);
        Ir.Class user = m.classes().stream()
                .filter(c -> c.name().equals("User"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(Ir.shape(m)));
        assertEquals(2, user.fields().size());
        assertEquals(1, user.methods().size());
        assertEquals("greet", user.methods().get(0).name());
        assertTrue(user.fields().stream().anyMatch(f -> f.name().equals("name")));
        assertTrue(user.fields().stream().anyMatch(f -> f.name().equals("age")));
    }

    @Test
    void arityMismatchIsHonestError() {
        assertThrows(IllegalStateException.class, () -> ir("""
                dobro(x: Int): Int {
                    return x * 2
                }

                main() {
                    println(dobro())
                }
                """));
    }

    @Test
    void classicForLowersLoopBlocks() {
        Ir.Module m = ir("""
                main() {
                    for (var i = 0, i < 3, i = i + 1) {
                        println(i)
                    }
                }
                """);
        String shape = Ir.shape(m);
        assertTrue(shape.contains("jump("), shape);
        assertTrue(shape.contains("jumpIfFalse("), shape);
        assertTrue(shape.contains("binary(<; Int)"), shape);
        assertTrue(shape.contains("binary(+; Int)"), shape);
    }

    @Test
    void importsPreserved() {
        Ir.Module m = ir("""
                import hydra.stdlib.IO

                main() {
                    println("x")
                }
                """);
        assertTrue(m.imports().contains("hydra.stdlib.IO"), Ir.shape(m));
    }
}
