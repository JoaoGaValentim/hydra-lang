package hydra.compiler;

import hydra.compiler.ast.Ast;
import hydra.compiler.backend.JsBackend;
import hydra.compiler.backend.JvmBackend;
import hydra.compiler.ir.Ir;
import hydra.compiler.ir.IrBuilder;
import hydra.compiler.parser.Parser;

import java.io.IOException;
import java.nio.file.Path;

/** Pipeline mínimo Hydra: fonte → AST → IR → alvo (F3-02/03). */
public final class Compiler {

    private Compiler() {}

    public static Ir.Module ir(String source) {
        Ast.Unit unit = Parser.parse(source);
        return new IrBuilder().build(unit, "Main");
    }

    public static void compileTo(String source, Path outputDir) throws IOException {
        new JvmBackend().emit(ir(source), outputDir);
    }

    public static void compileToJs(String source, Path outputDir) throws IOException {
        new JsBackend().emit(ir(source), outputDir);
    }
}
