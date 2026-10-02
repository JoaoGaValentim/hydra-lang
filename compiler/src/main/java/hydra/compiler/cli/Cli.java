package hydra.compiler.cli;

import hydra.compiler.Compiler;
import hydra.compiler.migrate.MigrateDiagnostic;
import hydra.compiler.migrate.MigrateResult;
import hydra.compiler.migrate.Migrator;
import hydra.compiler.parser.SyntaxError;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * F6-01 — CLI Hydra.
 * Subcomandos: check · run · migrate · fmt · help.
 * new/test ficam para o próximo slice (BACKLOG); aqui só o que o pipeline
 * atual cobre de verdade.
 */
public final class Cli {

    private Cli() {}

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        System.exit(code);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            usage(out);
            return 2;
        }
        String cmd = args[0];
        switch (cmd) {
            case "help", "-h", "--help" -> {
                usage(out);
                return 0;
            }
            case "version", "--version" -> {
                out.println("hydra 0.1.0-SNAPSHOT");
                return 0;
            }
            case "check" -> {
                if (args.length < 2) {
                    err.println("uso: hydra check <arquivo.hy>");
                    return 2;
                }
                return check(Path.of(args[1]), out, err);
            }
            case "run" -> {
                if (args.length < 2) {
                    err.println("uso: hydra run <arquivo.hy> [--js]");
                    return 2;
                }
                boolean js = false;
                Path file = null;
                for (int i = 1; i < args.length; i++) {
                    if ("--js".equals(args[i])) js = true;
                    else file = Path.of(args[i]);
                }
                if (file == null) {
                    err.println("uso: hydra run <arquivo.hy> [--js]");
                    return 2;
                }
                return runFile(file, js, out, err);
            }
            case "migrate" -> {
                if (args.length < 2) {
                    err.println("uso: hydra migrate <arquivo.kf> [saida.hy]");
                    return 2;
                }
                Path in = Path.of(args[1]);
                Path outHy = args.length >= 3 ? Path.of(args[2]) : null;
                return migrate(in, outHy, out, err);
            }
            case "fmt" -> {
                if (args.length < 2) {
                    err.println("uso: hydra fmt <arquivo.hy> [saida.hy]");
                    return 2;
                }
                Path in = Path.of(args[1]);
                Path outHy = args.length >= 3 ? Path.of(args[2]) : null;
                return fmt(in, outHy, out, err);
            }
            default -> {
                err.println("comando desconhecido: " + cmd);
                usage(err);
                return 2;
            }
        }
    }

    static int fmt(Path in, Path outHy, PrintStream out, PrintStream err) {
        if (!Files.isRegularFile(in)) {
            err.println("arquivo não encontrado: " + in);
            return 1;
        }
        String src;
        try {
            src = Files.readString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("falha ao ler " + in + ": " + e.getMessage());
            return 1;
        }
        String formatted;
        try {
            formatted = hydra.compiler.fmt.Formatter.format(src);
        } catch (SyntaxError e) {
            err.println(in + ":" + e.line() + ":" + e.col() + " [" + e.code() + "] " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println(in + ": " + e.getMessage());
            return 1;
        }
        Path target = outHy != null ? outHy : in;
        boolean changed;
        try {
            String prev = Files.isRegularFile(target) ? Files.readString(target, StandardCharsets.UTF_8) : null;
            changed = prev == null || !prev.equals(formatted);
            Files.writeString(target, formatted, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("falha ao escrever " + target + ": " + e.getMessage());
            return 1;
        }
        out.println((changed ? "reformat " : "unchanged ") + target);
        return 0;
    }

    static int check(Path file, PrintStream out, PrintStream err) {
        if (!Files.isRegularFile(file)) {
            err.println("arquivo não encontrado: " + file);
            return 1;
        }
        try {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            hydra.compiler.parser.Parser.parse(src);
            out.println("ok " + file);
            return 0;
        } catch (SyntaxError e) {
            err.println(file + ":" + e.line() + ":" + e.col() + " [" + e.code() + "] " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("falha ao ler " + file + ": " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println(file + ": " + e.getMessage());
            return 1;
        }
    }

    static int runFile(Path file, boolean js, PrintStream out, PrintStream err) {
        if (!Files.isRegularFile(file)) {
            err.println("arquivo não encontrado: " + file);
            return 1;
        }
        String src;
        try {
            src = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("falha ao ler " + file + ": " + e.getMessage());
            return 1;
        }
        Path tmp;
        try {
            tmp = Files.createTempDirectory("hydra-run");
        } catch (IOException e) {
            err.println("falha ao criar dir temporário: " + e.getMessage());
            return 1;
        }
        try {
            if (js) {
                Compiler.compileToJs(src, tmp);
                Path mainJs = tmp.resolve("main.js");
                if (!Files.isRegularFile(mainJs)) {
                    err.println("js: main.js não gerado em " + tmp);
                    return 1;
                }
                if (!nodeAvailable()) {
                    err.println("js: node não encontrado no PATH");
                    return 1;
                }
                ProcessBuilder pb = new ProcessBuilder("node", mainJs.toString());
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                Process p = pb.start();
                boolean finished = p.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    err.println("js: processo node estourou timeout");
                    return 1;
                }
                return p.exitValue();
            }
            Compiler.compileTo(src, tmp);
            try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.toUri().toURL()},
                    Cli.class.getClassLoader())) {
                Class<?> main = cl.loadClass("Main");
                Method m = main.getMethod("main");
                m.invoke(null);
            }
            return 0;
        } catch (SyntaxError e) {
            err.println(file + ":" + e.line() + ":" + e.col() + " [" + e.code() + "] " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("falha de E/S: " + e.getMessage());
            return 1;
        } catch (ReflectiveOperationException e) {
            err.println("falha ao executar Main: " + e);
            return 1;
        } catch (RuntimeException e) {
            err.println("erro: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("interrompido");
            return 1;
        }
    }

    static int migrate(Path in, Path outHy, PrintStream out, PrintStream err) {
        if (!Files.isRegularFile(in)) {
            err.println("arquivo não encontrado: " + in);
            return 1;
        }
        String src;
        try {
            src = Files.readString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("falha ao ler " + in + ": " + e.getMessage());
            return 1;
        }
        MigrateResult r = Migrator.migrate(src, in.getFileName().toString());
        for (MigrateDiagnostic d : r.diagnostics()) {
            err.println(d.toString());
        }
        Path target = outHy != null ? outHy : in.resolveSibling(stripExt(in.getFileName().toString()) + ".hy");
        try {
            Files.writeString(target, r.hydraSource(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("falha ao escrever " + target + ": " + e.getMessage());
            return 1;
        }
        if (!r.ok()) {
            out.println("parcial " + target);
            return 1;
        }
        out.println("ok " + target);
        return 0;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void usage(PrintStream s) {
        s.println("hydra — linguagem derivada do Kof");
        s.println();
        s.println("uso:");
        s.println("  hydra check  <arquivo.hy>           parseia e reporta erros");
        s.println("  hydra run    <arquivo.hy> [--js]    compila e executa (JVM; --js usa node)");
        s.println("  hydra migrate <arquivo.kf> [out.hy] Kof → Hydra");
        s.println("  hydra fmt    <arquivo.hy> [out.hy]  formata em canônico (reprint da AST)");
        s.println("  hydra version | help");
        s.println();
        s.println("limites v1: sem new/test; fmt preserva // por linha; try/catch JS na v2; Native BLQ-02.");
    }
}
