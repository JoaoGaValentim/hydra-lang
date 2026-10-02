package hydra.compiler.backend;

import hydra.compiler.ir.Ir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * F3-03 — backend JS mínimo a partir de Ir.Module.
 * CFG arbitrário via máquina de estados `switch (pc)` (JS não tem goto).
 * Subset alinhado ao JVM E2E: funções, val/var, aritmética, if, for,
 * chamadas, println, return.
 */
public final class JsBackend {

    public void emit(Ir.Module module, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        StringBuilder sb = new StringBuilder();
        sb.append("// Hydra → JS (F3-03)\n");
        sb.append("'use strict';\n\n");
        sb.append("function println(x) { console.log(x === undefined || x === null ? '' : x); }\n");
        sb.append("function print(x) { process.stdout.write(String(x === undefined || x === null ? '' : x)); }\n\n");

        for (Ir.Class c : module.classes()) {
            if (c.enumCases() != null && !c.enumCases().isEmpty()) continue;
            if (!"Main".equals(c.name()) && c.methods().isEmpty()) continue;
            for (Ir.Method m : c.methods()) {
                emitMethod(sb, m);
                sb.append('\n');
            }
        }
        sb.append("if (typeof require !== 'undefined' && require.main === module) {\n");
        sb.append("  main();\n");
        sb.append("}\n");
        Files.writeString(outputDir.resolve("main.js"), sb.toString(), StandardCharsets.UTF_8);
    }

    private void emitMethod(StringBuilder sb, Ir.Method m) {
        List<String> params = new ArrayList<>();
        for (int i = 0; i < m.parameterTypes().size(); i++) {
            String name = "p" + i;
            for (Ir.Local l : m.locals()) {
                if (l.index() == i) {
                    name = sanitize(l.name());
                    break;
                }
            }
            params.add(name);
        }
        List<Ir.Local> extra = new ArrayList<>();
        for (Ir.Local l : m.locals()) {
            if (l.index() >= m.parameterTypes().size()) extra.add(l);
        }

        sb.append("function ").append(m.name()).append('(').append(String.join(", ", params)).append(") {\n");
        sb.append("  const stack = [];\n");
        for (Ir.Local l : extra) {
            sb.append("  let ").append(sanitize(l.name())).append(";\n");
        }
        sb.append("  let pc = 0;\n");
        sb.append("  while (true) {\n");
        sb.append("    switch (pc) {\n");

        List<Ir.Block> blocks = m.blocks();
        for (int bi = 0; bi < blocks.size(); bi++) {
            Ir.Block b = blocks.get(bi);
            sb.append("      case ").append(b.index()).append(": {\n");
            for (Ir.Op op : b.ops()) {
                emitOp(sb, op, m, bi, blocks);
            }
            // fall-through para o próximo bloco se não terminou com jump/return
            if (!endsWithControl(b)) {
                int next = bi + 1 < blocks.size() ? blocks.get(bi + 1).index() : -1;
                if (next >= 0) {
                    sb.append("        pc = ").append(next).append(";\n");
                    sb.append("        break;\n");
                } else {
                    sb.append("        return;\n");
                }
            }
            sb.append("      }\n");
        }

        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
    }

    private static boolean endsWithControl(Ir.Block b) {
        if (b.ops().isEmpty()) return false;
        Ir.Op last = b.ops().get(b.ops().size() - 1);
        return last instanceof Ir.Jump
                || last instanceof Ir.Return
                || last instanceof Ir.Throw
                || last instanceof Ir.JumpIfFalse;
    }

    private void emitOp(StringBuilder sb, Ir.Op op, Ir.Method m, int blockIdx, List<Ir.Block> blocks) {
        if (op instanceof Ir.LoadLiteral lit) {
            sb.append("        stack.push(").append(jsLiteral(lit)).append(");\n");
        } else if (op instanceof Ir.LoadLocal ll) {
            sb.append("        stack.push(").append(localName(m, ll.index())).append(");\n");
        } else if (op instanceof Ir.StoreLocal sl) {
            sb.append("        ").append(localName(m, sl.index())).append(" = stack.pop();\n");
        } else if (op instanceof Ir.Binary bin) {
            emitBinary(sb, bin);
        } else if (op instanceof Ir.Unary un) {
            if ("-".equals(un.op())) sb.append("        stack.push(-stack.pop());\n");
            else if ("!".equals(un.op())) sb.append("        stack.push(!stack.pop());\n");
            else throw new IllegalStateException("JS: unary " + un.op());
        } else if (op instanceof Ir.Call call) {
            emitCall(sb, call, m);
        } else if (op instanceof Ir.Return ret) {
            if (ret.returnType().isVoid()) sb.append("        return;\n");
            else sb.append("        return stack.pop();\n");
        } else if (op instanceof Ir.Pop) {
            sb.append("        stack.pop();\n");
        } else if (op instanceof Ir.Jump j) {
            sb.append("        pc = ").append(j.targetBlock()).append(";\n");
            sb.append("        break;\n");
        } else if (op instanceof Ir.JumpIfFalse jf) {
            sb.append("        if (!stack.pop()) { pc = ").append(jf.targetBlock()).append("; break; }\n");
        } else if (op instanceof Ir.LoadEnum le) {
            sb.append("        stack.push('").append(le.enumName()).append('.').append(le.caseName()).append("');\n");
        } else if (op instanceof Ir.Throw) {
            sb.append("        throw stack.pop();\n");
        } else if (op instanceof Ir.TryStart || op instanceof Ir.TryEnd || op instanceof Ir.CatchStart) {
            // try/catch JS na v2
        } else if (op instanceof Ir.LoadField lf) {
            throw new IllegalStateException("JS v1: LoadField " + lf.typeName() + "." + lf.fieldName());
        } else if (op instanceof Ir.StoreField sf) {
            throw new IllegalStateException("JS v1: StoreField " + sf.typeName() + "." + sf.fieldName());
        } else if (op instanceof Ir.NewObject) {
            throw new IllegalStateException("JS v1: NewObject (v2)");
        } else {
            throw new IllegalStateException("JS: op não suportada: " + op.getClass().getSimpleName());
        }
    }

    private void emitBinary(StringBuilder sb, Ir.Binary bin) {
        String op = bin.op();
        String p = "        ";
        switch (op) {
            case "+" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a + b); }\n");
            case "-" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a - b); }\n");
            case "*" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a * b); }\n");
            case "/" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a / b); }\n");
            case "%" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a % b); }\n");
            case "==" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a === b ? 1 : 0); }\n");
            case "!=" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a !== b ? 1 : 0); }\n");
            case "<" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a < b ? 1 : 0); }\n");
            case ">" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a > b ? 1 : 0); }\n");
            case "<=" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a <= b ? 1 : 0); }\n");
            case ">=" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a >= b ? 1 : 0); }\n");
            case "&&" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push((a && b) ? 1 : 0); }\n");
            case "||" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push((a || b) ? 1 : 0); }\n");
            case "&" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a & b); }\n");
            case "|" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a | b); }\n");
            case "^" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a ^ b); }\n");
            default -> throw new IllegalStateException("JS: op " + op);
        }
    }

    private void emitCall(StringBuilder sb, Ir.Call call, Ir.Method m) {
        String name = call.name();
        int n = call.parameterTypes().size();
        String p = "        ";
        if ("println".equals(name) || "print".equals(name)) {
            if (n == 0) {
                sb.append(p).append(name).append("(undefined);\n");
            } else if (n == 1) {
                sb.append(p).append("{ const a0 = stack.pop(); ").append(name).append("(a0); }\n");
            } else {
                sb.append(p).append("{ ");
                for (int i = n - 1; i >= 0; i--) {
                    sb.append("const a").append(i).append(" = stack.pop(); ");
                }
                sb.append(name).append('(');
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append('a').append(i);
                }
                sb.append("); }\n");
            }
            return;
        }
        sb.append(p).append("{ ");
        for (int i = n - 1; i >= 0; i--) {
            sb.append("const a").append(i).append(" = stack.pop(); ");
        }
        sb.append("stack.push(").append(name).append('(');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append('a').append(i);
        }
        sb.append(")); }\n");
    }

    private static String jsLiteral(Ir.LoadLiteral lit) {
        Ir.Type t = lit.type();
        Object v = lit.value();
        if (Ir.Type.STRING.equals(t)) {
            return "'" + escape(v == null ? "" : v.toString()) + "'";
        }
        if (Ir.Type.BOOL.equals(t)) {
            return v instanceof Boolean b && b ? "1" : "0";
        }
        if (v == null) return "null";
        return v.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    private static String localName(Ir.Method m, int index) {
        for (Ir.Local l : m.locals()) {
            if (l.index() == index) return sanitize(l.name());
        }
        return "t" + index;
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) return "_";
        if (name.startsWith("$")) return "_" + name.substring(1);
        return name;
    }
}
