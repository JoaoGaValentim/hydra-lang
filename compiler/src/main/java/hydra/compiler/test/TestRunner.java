package hydra.compiler.test;

import hydra.compiler.ast.Ast;
import hydra.compiler.backend.JsBackend;
import hydra.compiler.backend.JvmBackend;
import hydra.compiler.ir.Ir;
import hydra.compiler.ir.IrBuilder;
import hydra.compiler.parser.Parser;
import hydra.compiler.token.Token;
import hydra.compiler.token.TokenKind;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * F6-01b — runner de `hydra test`: sintetiza um harness em IR a partir das
 * declarações `test "nome" { … }` (mesma semântica do Kof: main original é
 * substituído, PASS/FAIL por teste, falha = exit != 0).
 *
 * O harness é construído na AST antes do lowering — nada de sintaxe nova na
 * linguagem; o runner usa só nós que o pipeline já cobre (try/catch, throw,
 * println, assert).
 */
public final class TestRunner {

    private TestRunner() {}

    /** Um teste descoberto: nome de exibição e função sintetizada. */
    public record Case(String name, String function) {}

    /** Resultado: contadores do harness; total == 0 = arquivo sem testes. */
    public record Result(int passed, int failed, int total) {
        public boolean ok() {
            return total > 0 && failed == 0;
        }
    }

    /**
     * Compila o harness e executa no alvo pedido. `out` recebe a saída do
     * harness (linhas PASS/FAIL + resumo); `err` recebe falhas de infraestrutura.
     */
    public static Result run(String source, boolean js, Path tmp, PrintStream out, PrintStream err)
            throws IOException, InterruptedException {
        List<Case> cases = new ArrayList<>();
        Ast.Unit unit = Parser.parse(source);
        Ast.Unit harness = harness(unit, cases);
        if (cases.isEmpty()) {
            return new Result(0, 0, 0);
        }
        Ir.Module module = new IrBuilder().build(harness, "Main");
        String text = js ? runJs(module, tmp, err) : runJvm(module, tmp, out, err);
        if (text == null || text.isEmpty()) {
            return new Result(0, cases.size(), cases.size());
        }
        out.print(text);
        Integer failed = parseSummary(text);
        if (failed == null) {
            err.println("harness: resumo ausente na saída");
            return new Result(0, cases.size(), cases.size());
        }
        return new Result(cases.size() - failed, failed, cases.size());
    }

    /** Nome do arquivo com harness (útil para diagnósticos); vazio se sem testes. */
    public static List<Case> discover(String source) {
        List<Case> cases = new ArrayList<>();
        harness(Parser.parse(source), cases);
        return cases;
    }

    // ---- harness AST ----

    private static Ast.Unit harness(Ast.Unit unit, List<Case> casesOut) {
        Token t = new Token(TokenKind.IDENT, ".harness", 1, 1);
        List<Ast.Decl> decls = new ArrayList<>();
        for (Ast.Decl d : unit.decls()) {
            if (d instanceof Ast.FunDecl f && f.name().startsWith("test:")) {
                String fn = "test_" + casesOut.size();
                casesOut.add(new Case(f.name().substring("test:".length()), fn));
                decls.add(new Ast.FunDecl(fn, f.params(), f.returnType(), f.body(), f.pos()));
            } else if (d instanceof Ast.FunDecl f && "main".equals(f.name())) {
                // `hydra test` roda só os testes (main original fica de fora)
            } else {
                decls.add(d);
            }
        }
        decls.add(harnessMain(casesOut, t));
        return new Ast.Unit(unit.imports(), decls, unit.pos());
    }

    private static Ast.FunDecl harnessMain(List<Case> cases, Token t) {
        List<Ast.Stmt> body = new ArrayList<>();
        body.add(new Ast.VarDecl(false, "failed", new Ast.TypeRef("Int", false, t), intLit(0, t), t));

        for (Case c : cases) {
            List<Ast.Stmt> run = new ArrayList<>();
            run.add(new Ast.ExprStmt(call(c.function(), List.of(), t), t));
            run.add(new Ast.ExprStmt(call("println", List.of(stringLit("PASS " + c.name(), t)), t), t));

            List<Ast.Stmt> handler = new ArrayList<>();
            handler.add(new Ast.ExprStmt(call("println", List.of(
                    new Ast.BinaryExpr("+",
                            new Ast.BinaryExpr("+", stringLit("FAIL " + c.name() + ": ", t),
                                    new Ast.IdentExpr("e", t), t),
                            stringLit("", t), t)), t), t));
            handler.add(new Ast.ExprStmt(new Ast.AssignExpr("=",
                    new Ast.IdentExpr("failed", t),
                    new Ast.BinaryExpr("+", new Ast.IdentExpr("failed", t), intLit(1, t), t), t), t));

            body.add(new Ast.TryStmt(
                    new Ast.Block(run, t),
                    List.of(new Ast.CatchClause("e", new Ast.Block(handler, t), t)),
                    t));
        }

        body.add(new Ast.ExprStmt(call("println", List.of(
                new Ast.BinaryExpr("+",
                        new Ast.BinaryExpr("+", stringLit("hydra test: ", t),
                                new Ast.IdentExpr("failed", t), t),
                        stringLit(" failed of " + cases.size() + " tests", t), t)), t), t));
        body.add(new Ast.IfStmt(
                new Ast.BinaryExpr(">", new Ast.IdentExpr("failed", t), intLit(0, t), t),
                new Ast.Block(List.of(new Ast.ThrowStmt(stringLit("tests failed", t), t)), t),
                null, t));
        return new Ast.FunDecl("main", List.of(), null, new Ast.Block(body, t), t);
    }

    private static Ast.Expr call(String name, List<Ast.Expr> args, Token t) {
        return new Ast.CallExpr(new Ast.IdentExpr(name, t), args, t);
    }

    private static Ast.Expr intLit(long v, Token t) {
        return new Ast.IntLit(v, t);
    }

    private static Ast.Expr stringLit(String v, Token t) {
        return new Ast.StringLit(v, t);
    }

    // ---- execução ----

    private static String runJvm(Ir.Module module, Path tmp, PrintStream out, PrintStream err)
            throws IOException {
        new JvmBackend().emit(module, tmp);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(buf, true, StandardCharsets.UTF_8);
        PrintStream old = System.out;
        System.setOut(capture);
        Throwable thrown = null;
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.toUri().toURL()},
                TestRunner.class.getClassLoader())) {
            cl.loadClass("Main").getMethod("main").invoke(null);
        } catch (InvocationTargetException ite) {
            thrown = ite.getCause();
        } catch (ReflectiveOperationException e) {
            thrown = e;
        } finally {
            System.setOut(old);
        }
        String text = buf.toString(StandardCharsets.UTF_8);
        if (thrown != null && parseSummary(text) == null) {
            err.println("harness: " + thrown.getMessage());
        }
        return text;
    }

    private static String runJs(Ir.Module module, Path tmp, PrintStream err)
            throws IOException, InterruptedException {
        new JsBackend().emitForTests(module, tmp);
        Path js = tmp.resolve("main.js");
        Process p;
        try {
            // stderr separado: o throw final do harness ("tests failed") vira
            // stack trace no node; o resumo parseável vive no stdout.
            p = new ProcessBuilder("node", js.toString()).start();
        } catch (IOException e) {
            err.println("js: node não encontrado no PATH");
            return null;
        }
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        Thread drain = Thread.ofVirtual().start(() -> {
            try {
                p.getErrorStream().transferTo(errBuf);
            } catch (IOException ignored) {
                // stream fechado com o processo
            }
        });
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            err.println("js: timeout de 30s");
        }
        drain.join();
        if (p.exitValue() != 0 && parseSummary(text) == null) {
            String detail = errBuf.toString(StandardCharsets.UTF_8).strip();
            err.println("js: node saiu com " + p.exitValue()
                    + (detail.isEmpty() ? "" : "\n" + detail));
        }
        return text;
    }

    /** Última linha `hydra test: <failed> failed of <n> tests`. */
    private static Integer parseSummary(String text) {
        Integer failed = null;
        for (String line : text.split("\n")) {
            int idx = line.indexOf("hydra test: ");
            if (idx < 0) continue;
            String rest = line.substring(idx + "hydra test: ".length()).trim();
            String[] parts = rest.split("\\s+");
            if (parts.length >= 1) {
                try {
                    failed = Integer.parseInt(parts[0]);
                } catch (NumberFormatException ignored) {
                    // linha de saída de um teste com o mesmo prefixo: ignora
                }
            }
        }
        return failed;
    }
}
