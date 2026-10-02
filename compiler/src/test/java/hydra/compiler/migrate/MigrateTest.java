package hydra.compiler.migrate;

import hydra.compiler.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F4-01 — migração Kof→Hydra.
 * Saída deve: (1) parsear no parser Hydra; (2) preservar a intenção do subconjunto;
 * (3) emitir diagnósticos honestos no que não converte.
 */
class MigrateTest {

    @TempDir
    Path out;

    @Test
    void helloMigratesCleanly() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    println("Hello, World!");
                }
                """, "hello.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        assertTrue(r.hydraSource().contains("println(\"Hello, World!\")"), r.hydraSource());
        assertFalse(r.hydraSource().contains(";"), "Hydra não usa ';':\n" + r.hydraSource());
        assertDoesNotThrow(() -> Parser.parse(r.hydraSource()));
    }

    @Test
    void functionsMigrateToHydraSignatures() {
        MigrateResult r = Migrator.migrate("""
                String saudacao(String nome) {
                    return "ola " + nome;
                }
                Int soma(Int a, Int b) = a + b
                main() {
                    println(saudacao("kof"));
                    println(soma(20, 22));
                }
                """, "functions.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("saudacao(nome: String): String"), src);
        assertTrue(src.contains("soma(a: Int, b: Int): Int"), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void recordBecomesTypeAndAccessorsBecomeFields() {
        MigrateResult r = Migrator.migrate("""
                record Point(Int x, Int y)
                main() {
                    var p = Point(3, 7);
                    println(p.x() + p.y());
                }
                """, "records.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("type Point(var x: Int, var y: Int)"), src);
        assertTrue(src.contains("p.x"), src);
        assertFalse(src.contains("p.x()"), "accessor de record deve virar campo:\n" + src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void classWithPrimaryCtorBecomesType() {
        MigrateResult r = Migrator.migrate("""
                class User(String name, Int age) {
                    greet(): String {
                        return "Hello, " + name;
                    }
                }
                main() {
                    var u = User("Mel", 26);
                    println(u.greet());
                }
                """, "classes.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("type User(var name: String, var age: Int)"), src);
        assertTrue(src.contains("greet(): String"), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void newExprLosesNewKeyword() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    var u = new User("Kof", 30);
                    println(u.greet());
                }
                class User(String name, Int age) {
                    greet(): String {
                        return "ola " + name;
                    }
                }
                """, "new.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("User(\"Kof\", 30)"), src);
        assertFalse(src.contains("new "), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void forClassicAndWhileBecomeForHeads() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    var total = 0;
                    for (var i = 0; i < 4; i = i + 1) {
                        total = total + i;
                    }
                    while (total > 3) {
                        total = total - 1;
                    }
                    for (var item in listOf("x", "y")) {
                        println(item);
                    }
                    println(total);
                }
                """, "control.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("for (var i = 0,"), src);
        assertTrue(src.contains("i < 4"), src);
        assertTrue(src.contains("i = (i + 1)") || src.contains("i = i + 1"), src);
        assertTrue(src.contains("for (") && src.contains("total > 3"), src);
        assertTrue(src.contains("for var item in listOf(\"x\", \"y\")"), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void switchBecomesMatchAndCatchLosesType() {
        MigrateResult r = Migrator.migrate("""
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
                    try {
                        println(nome(Color.Red));
                    } catch (String e) {
                        println("erro: " + e);
                    }
                }
                """, "switch.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("enum Color"), src);
        assertTrue(src.contains("Red"), src);
        assertTrue(src.contains("match (c)"), src);
        assertTrue(src.contains("catch (e)"), src);
        assertFalse(src.contains("catch (String"), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void finallyIsDiagnosedNotSilentlyKept() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    try {
                        throw "boom";
                    } catch (String e) {
                        println("caught: " + e);
                    } finally {
                        println("finally");
                    }
                }
                """, "exceptions.kf");
        assertFalse(r.ok(), "finally deve gerar diagnóstico de parcialidade");
        assertTrue(r.diagnostics().stream().anyMatch(d -> "MIG002".equals(d.code())),
                r.diagnosticsText());
        assertTrue(r.hydraSource().contains("catch (e)"), r.hydraSource());
        assertDoesNotThrow(() -> Parser.parse(r.hydraSource()));
    }

    @Test
    void doWhileIsHonestDiagnostic() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    var i = 0;
                    do {
                        i = i + 1;
                    } while (i < 3);
                    println(i);
                }
                """, "dowhile.kf");
        assertFalse(r.ok());
        assertTrue(r.diagnostics().stream().anyMatch(d -> "MIG001".equals(d.code())),
                r.diagnosticsText());
        assertDoesNotThrow(() -> Parser.parse(r.hydraSource()));
    }

    @Test
    void inheritanceAndInterfacePartial() {
        MigrateResult r = Migrator.migrate("""
                class Animal {
                    String name;
                    public constructor(String name) {
                        this.name = name;
                    }
                    public speak(): String {
                        return "animal";
                    }
                }
                class Dog extends Animal {
                    public constructor(String name) {
                        super(name);
                    }
                    public speak(): String {
                        return "dog";
                    }
                }
                class Speaker {
                    speak(): String;
                }
                class Cat extends Animal implements Speaker {
                    public constructor(String name) {
                        super(name);
                    }
                    public speak(): String {
                        return "meow";
                    }
                }
                main() {
                    var dog = new Dog("Rex");
                    println(dog.speak());
                }
                """, "inheritance.kf");
        String src = r.hydraSource();
        assertTrue(src.contains("type Animal(var name: String)"), src);
        assertTrue(src.contains("extends Animal"), src);
        assertTrue(src.contains("type Speaker"), src);
        assertTrue(r.diagnostics().stream().anyMatch(d -> "MIG007".equals(d.code())),
                "implements de interface deve ser diagnosticado:\n" + r.diagnosticsText());
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void thisFieldAccessLosesThis() {
        MigrateResult r = Migrator.migrate("""
                class Counter {
                    Int value;
                    constructor() {
                        this.value = 0;
                    }
                    inc(): Int {
                        this.value = this.value + 1;
                        return this.value;
                    }
                }
                main() {
                    var c = Counter();
                    println(c.inc());
                }
                """, "this.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertFalse(src.contains("this."), "Hydra não tem this:\n" + src);
        assertTrue(src.contains("value = (value + 1)") || src.contains("value = value + 1"), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void writesHydraFile() throws Exception {
        MigrateResult r = Migrator.migrate("""
                main() {
                    println("file");
                }
                """, "file.kf");
        Path hy = out.resolve("file.hy");
        Files.writeString(hy, r.hydraSource(), StandardCharsets.UTF_8);
        assertTrue(Files.exists(hy));
        String text = Files.readString(hy);
        assertTrue(text.contains("main()"), text);
    }

    @Test
    void brokenKofSourceFailsHonest() {
        MigrateResult r = Migrator.migrate("main( {", "broken.kf");
        assertFalse(r.ok());
        assertTrue(r.diagnostics().stream().anyMatch(d -> "MIG008".equals(d.code())),
                r.diagnosticsText());
    }

    @Test
    void ifExpressionMigrates() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    var v = if (5 > 3) "maior" else "menor";
                    println(v);
                }
                """, "ifexpr.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("\"maior\" else \"menor\""), src);
        assertTrue(src.contains("if ("), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }

    @Test
    void typedLocalBecomesVarWithAnnotation() {
        MigrateResult r = Migrator.migrate("""
                main() {
                    Int x = 1;
                    val y = 2;
                    var z = 3;
                    println(x + y + z);
                }
                """, "locals.kf");
        assertTrue(r.ok(), r.diagnosticsText());
        String src = r.hydraSource();
        assertTrue(src.contains("var x: Int = 1"), src);
        assertTrue(src.contains("val y = 2"), src);
        assertTrue(src.contains("var z = 3"), src);
        assertDoesNotThrow(() -> Parser.parse(src));
    }
}
