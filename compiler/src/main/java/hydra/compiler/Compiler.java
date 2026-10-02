package hydra.compiler;

import hydra.compiler.ast.Ast;
import hydra.compiler.backend.JvmBackend;
import hydra.compiler.ir.Ir;
import hydra.compiler.ir.IrBuilder;
import hydra.compiler.parser.Parser;

import java.io.IOException;
import java.nio.file.Path;

/** Pipeline mínimo Hydra: fonte → AST → IR → .class (F3-02). */
public final class Compiler {

    private Compiler() {}

    public static Ir.Module ir(String source) {
        Ast.Unit unit = Parser.parse(source);
        return new IrBuilder().build(unit, "Main");
    }

    public static void compileTo(String source, Path outputDir) throws IOException {
        Ir.Module module = ir(source);
        new JvmBackend().emit(module, outputDir);
    }
}
