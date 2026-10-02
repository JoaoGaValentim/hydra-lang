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
        emit(module, outputDir, false);
    }

    /**
     * Modo teste (`hydra test --js`): omite o guard de entrada `main()`;
     * o runner anexa o harness e chama cada função `test_*` diretamente.
     */
    public void emitForTests(Ir.Module module, Path outputDir) throws IOException {
        emit(module, outputDir, true);
    }

    private void emit(Ir.Module module, Path outputDir, boolean testMode) throws IOException {
        Files.createDirectories(outputDir);
        StringBuilder sb = new StringBuilder();
        sb.append("// Hydra → JS (F3-03)\n");
        sb.append("'use strict';\n\n");
        sb.append("function println(x) { console.log(x === undefined || x === null ? '' : x); }\n");
        sb.append("function print(x) { process.stdout.write(String(x === undefined || x === null ? '' : x)); }\n");
        sb.append("function _str(x) { return String(x); }\n");
        sb.append("function _strF(x) { return Number.isInteger(x) ? x.toFixed(1) : String(x); }\n");
        sb.append("function _b(x) { return x ? 'true' : 'false'; }\n");
        sb.append("let _stdin = null;\n");
        sb.append("function _readLine() {\n");
        sb.append("  if (_stdin === null) {\n");
        sb.append("    const fs = require('fs');\n");
        sb.append("    try { _stdin = fs.readFileSync(0, 'utf8'); } catch (_e) { _stdin = ''; }\n");
        sb.append("  }\n");
        sb.append("  const nl = _stdin.indexOf('\\n');\n");
        sb.append("  if (nl < 0) { const line = _stdin; _stdin = ''; return line; }\n");
        sb.append("  const line = _stdin.slice(0, nl); _stdin = _stdin.slice(nl + 1); return line;\n");
        sb.append("}\n\n");

        for (Ir.Class c : module.classes()) {
            if (c.enumCases() != null && !c.enumCases().isEmpty()) continue;
            if (!"Main".equals(c.name()) && c.methods().isEmpty()) continue;
            for (Ir.Method m : c.methods()) {
                emitMethod(sb, m);
                sb.append('\n');
            }
        }
        if (testMode) {
            // o runner chama o harness sintetizado diretamente
            sb.append("main();\n");
        } else if (hasMain(module)) {
            sb.append("if (typeof require !== 'undefined' && require.main === module) {\n");
            sb.append("  main();\n");
            sb.append("}\n");
        }
        Files.writeString(outputDir.resolve("main.js"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static boolean hasMain(Ir.Module module) {
        for (Ir.Class c : module.classes()) {
            for (Ir.Method m : c.methods()) {
                if ("main".equals(m.name())) return true;
            }
        }
        return false;
    }
    private void emitMethod(StringBuilder sb, Ir.Method m) {
        List<String> params = new ArrayList<>();
        for (int i = 0; i < m.parameterTypes().size(); i++) {
            params.add(localName(m, i));
        }
        List<Ir.Local> extra = new ArrayList<>();
        for (Ir.Local l : m.locals()) {
            if (l.index() >= m.parameterTypes().size()) extra.add(l);
        }

        // try/catch: handler do CatchStart recebe o valor de erro como String
        java.util.Map<Integer, Integer> handlerByStartBlock = handlerMap(m);

        sb.append("function ").append(Ir.safeName(m.name())).append('(').append(String.join(", ", params)).append(") {\n");
        sb.append("  const stack = [];\n");
        for (Ir.Local l : extra) {
            sb.append("  let ").append(localName(m, l.index())).append(";\n");
        }
        sb.append("  let pc = 0;\n");
        sb.append("  let _err = null;\n");
        sb.append("  while (true) {\n");
        sb.append("    switch (pc) {\n");

        List<Ir.Block> blocks = m.blocks();
        for (int bi = 0; bi < blocks.size(); bi++) {
            Ir.Block b = blocks.get(bi);
            sb.append("      case ").append(b.index()).append(": {\n");
            Integer handler = handlerByStartBlock.get(b.index());
            if (handler != null) {
                sb.append("        try {\n");
            }
            int opened = handler != null ? 1 : 0;
            for (Ir.Op op : b.ops()) {
                if (op instanceof Ir.TryEnd && opened > 0) {
                    sb.append("        } catch (_e) { pc = ").append(handler)
                            .append("; _err = String(_e && _e.message !== undefined ? _e.message : _e); break; }\n");
                    opened = 0;
                    continue;
                }
                if (op instanceof Ir.TryStart || op instanceof Ir.TryEnd) continue;
                emitOp(sb, op, m, bi, blocks);
            }
            if (opened > 0) {
                sb.append("        } catch (_e) { pc = ").append(handler)
                        .append("; _err = String(_e && _e.message !== undefined ? _e.message : _e); break; }\n");
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

    /**
     * Mapeia cada bloco dentro de uma região try → bloco do CatchStart.
     * O lowering emite TryStart…TryEnd no fluxo e o CatchStart no handler;
     * blocos internos (ex.: corpo de for dentro do try) também são protegidos.
     */
    private static java.util.Map<Integer, Integer> handlerMap(Ir.Method m) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        record Region(int start, int end) {}
        List<Region> regions = new ArrayList<>();
        int start = -1;
        for (Ir.Block b : m.blocks()) {
            for (Ir.Op op : b.ops()) {
                if (op instanceof Ir.TryStart) {
                    if (start < 0) start = b.index();
                } else if (op instanceof Ir.TryEnd) {
                    if (start >= 0) {
                        regions.add(new Region(start, b.index()));
                        start = -1;
                    }
                } else if (op instanceof Ir.CatchStart) {
                    if (!regions.isEmpty()) {
                        Region r = regions.remove(0);
                        for (int i = r.start(); i <= r.end(); i++) out.put(i, b.index());
                    }
                }
            }
        }
        return out;
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
        } else if (op instanceof Ir.NewList nl) {
            emitNewCollection(sb, nl.valueTypes(), true);
        } else if (op instanceof Ir.NewSet ns) {
            emitNewCollection(sb, ns.valueTypes(), false);
        } else if (op instanceof Ir.NewMap nm) {
            sb.append("        { const vals = [];\n");
            sb.append("          for (let i = 0; i < ").append(nm.keyTypes().size() * 2)
                    .append("; i++) vals.unshift(stack.pop());\n");
            sb.append("          const mp = new Map();\n");
            sb.append("          for (let i = 0; i < vals.length; i += 2) mp.set(vals[i], vals[i + 1]);\n");
            sb.append("          stack.push(mp); }\n");
        } else if (op instanceof Ir.IndexGet ig) {
            if (ig.collectionType().name().startsWith("Map<")) {
                sb.append("        { const k = stack.pop(); const c = stack.pop(); stack.push(c.get(k)); }\n");
            } else {
                sb.append("        { const idx = stack.pop(); const c = stack.pop(); stack.push(c[idx]); }\n");
            }
        } else if (op instanceof Ir.Length) {
            sb.append("        { const c = stack.pop(); stack.push(c.length); }\n");
        } else if (op instanceof Ir.Contains) {
            sb.append("        { const v = stack.pop(); const c = stack.pop(); ")
                    .append("stack.push(typeof c === 'string' ? (c.includes(v) ? 1 : 0) ")
                    .append(": (c.has ? (c.has(v) ? 1 : 0) : (c.includes(v) ? 1 : 0))); }\n");
        } else if (op instanceof Ir.IterInit ii) {
            if (ii.collectionType().name().startsWith("Map<")) {
                // for k in map: itera as chaves (igual Map.keySet no JVM)
                sb.append("        ").append(localName(m, ii.localIndex()))
                        .append(" = stack.pop().keys();\n");
            } else {
                sb.append("        ").append(localName(m, ii.localIndex()))
                        .append(" = stack.pop()[Symbol.iterator]();\n");
            }
        } else if (op instanceof Ir.IterNext in) {
            sb.append("        { const n = ").append(localName(m, in.localIndex())).append(".next();\n");
            sb.append("          if (n.done) { pc = ").append(in.exitBlock()).append("; break; }\n");
            sb.append("          ").append(localName(m, in.valueLocalIndex()))
                    .append(" = n.value; }\n");
        } else if (op instanceof Ir.ReadLine) {
            sb.append("        stack.push(_readLine());\n");
        } else if (op instanceof Ir.Jump j) {
            sb.append("        pc = ").append(j.targetBlock()).append(";\n");
            sb.append("        break;\n");
        } else if (op instanceof Ir.JumpIfFalse jf) {
            sb.append("        if (!stack.pop()) { pc = ").append(jf.targetBlock()).append("; break; }\n");
        } else if (op instanceof Ir.LoadEnum le) {
            sb.append("        stack.push('").append(le.enumName()).append('.').append(le.caseName()).append("');\n");
        } else if (op instanceof Ir.Throw) {
            sb.append("        throw stack.pop();\n");
        } else if (op instanceof Ir.ToString ts) {
            if (Ir.Type.BOOL.equals(ts.fromType())) {
                sb.append("        { const v = stack.pop(); stack.push(v ? 'true' : 'false'); }\n");
            } else if (Ir.Type.FLOAT.equals(ts.fromType())) {
                sb.append("        stack.push(_strF(stack.pop()));\n");
            } else {
                sb.append("        stack.push(String(stack.pop()));\n");
            }
        } else if (op instanceof Ir.Assert asrt) {
            sb.append("        if (!stack.pop()) throw new Error(")
                    .append(jsString(asrt.message() == null ? "assertion failed" : asrt.message()))
                    .append(");\n");
        } else if (op instanceof Ir.CatchStart cs) {
            // e é String na linguagem: o catch guarda a mensagem em _err
            sb.append("        ").append(localName(m, cs.localIndex())).append(" = _err;\n");
        } else if (op instanceof Ir.TryStart || op instanceof Ir.TryEnd) {
            // tratados no emitMethod (wrapping do caso por bloco)
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

    /** List/Set a partir de N valores na pilha (ordem preservada). */
    private void emitNewCollection(StringBuilder sb, List<Ir.Type> types, boolean list) {
        sb.append("        { const vals = [];\n");
        sb.append("          for (let i = 0; i < ").append(types.size())
                .append("; i++) vals.unshift(stack.pop());\n");
        if (list) {
            sb.append("          stack.push(vals); }\n");
        } else {
            sb.append("          stack.push(new Set(vals)); }\n");
        }
    }

    private void emitBinary(StringBuilder sb, Ir.Binary bin) {
        String op = bin.op();
        String p = "        ";
        boolean intOperands = Ir.Type.INT.equals(bin.operandType());
        switch (op) {
            case "+" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a + b); }\n");
            case "-" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a - b); }\n");
            case "*" -> sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a * b); }\n");
            case "/" -> {
                if (intOperands) {
                    // Int/Int é divisão inteira (igual ao JVM LDIV)
                    sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(Math.trunc(a / b)); }\n");
                } else {
                    sb.append(p).append("{ const b = stack.pop(); const a = stack.pop(); stack.push(a / b); }\n");
                }
            }
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
                Ir.Type at = call.parameterTypes().get(0);
                String arg = "a0";
                if (Ir.Type.BOOL.equals(at)) {
                    arg = "_b(a0)"; // JVM imprime true/false, não 1/0
                } else if (Ir.Type.FLOAT.equals(at)) {
                    arg = "_strF(a0)"; // JVM imprime 1.0, não 1
                }
                sb.append(p).append("{ const a0 = stack.pop(); ").append(name)
                        .append('(').append(arg).append("); }\n");
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
        sb.append("stack.push(").append(Ir.safeName(name)).append('(');
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
            return jsString(v == null ? "" : v.toString());
        }
        if (Ir.Type.BOOL.equals(t)) {
            return v instanceof Boolean b && b ? "1" : "0";
        }
        if (v == null) return "null";
        return v.toString();
    }

    private static String jsString(String s) {
        return "'" + escape(s) + "'";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    /**
     * Nome JS único por índice de local: catch (e) repetido vira e, e_2…
     * (a linguagem permite reusar o nome em catches distintos; JS não).
     */
    private static String localName(Ir.Method m, int index) {
        String base = null;
        for (Ir.Local l : m.locals()) {
            if (l.index() == index) {
                base = sanitize(l.name());
                break;
            }
        }
        if (base == null) base = "t" + index;
        int seen = 0;
        for (Ir.Local l : m.locals()) {
            if (l.index() == index) break;
            if (sanitize(l.name()).equals(base)) seen++;
        }
        return seen == 0 ? base : base + "_" + (seen + 1);
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) return "_";
        if (name.startsWith("$")) return "_" + name.substring(1);
        return name;
    }
}
