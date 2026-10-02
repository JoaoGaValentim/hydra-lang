package hydra.compiler.backend;

import hydra.compiler.ir.Ir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * F3-02 — backend JVM mínimo a partir de Ir.Module.
 * Subconjunto v1: funções estáticas em Main, val/var, aritmética, if/for,
 * chamadas, println/print, return. Campo/enum/objeto: v2.
 */
public final class JvmBackend {

    /** Emite .class para cada classe não-enum do módulo. */
    public void emit(Ir.Module module, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (Ir.Class c : module.classes()) {
            if (c.enumCases() != null && !c.enumCases().isEmpty()) continue;
            if (c.methods().isEmpty() && c.fields().isEmpty() && !"Main".equals(c.name())) continue;
            byte[] bytes = emitClass(c);
            Files.write(outputDir.resolve(c.name() + ".class"), bytes);
        }
    }

    public byte[] emitClass(Ir.Class cls) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String internal = cls.name();
        String superName = cls.superName() == null || cls.superName().isEmpty()
                || "Object".equals(cls.superName())
                ? "java/lang/Object"
                : cls.superName().replace('.', '/');
        cw.visit(V21, ACC_PUBLIC | ACC_SUPER, internal, null, superName, null);

        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        if ("Main".equals(internal)) {
            cw.visitField(ACC_PRIVATE | ACC_STATIC, "$stdin",
                    "Ljava/io/BufferedReader;", null, null).visitEnd();
        }

        for (Ir.Method m : cls.methods()) {
            emitMethod(cw, internal, m);
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void emitMethod(ClassWriter cw, String owner, Ir.Method m) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, Ir.safeName(m.name()),
                methodDesc(m), null, null);
        mv.visitCode();

        Map<Integer, Label> blockLabels = new HashMap<>();
        for (Ir.Block b : m.blocks()) {
            blockLabels.put(b.index(), new Label());
        }

        int[] localSlots = new int[Math.max(m.locals().size(), 1)];
        int cursor = 0;
        for (Ir.Type t : m.parameterTypes()) cursor += slots(t);
        for (Ir.Local l : m.locals()) {
            if (l.index() < m.parameterTypes().size()) {
                int s = 0;
                for (int i = 0; i < l.index(); i++) s += slots(m.parameterTypes().get(i));
                localSlots[l.index()] = s;
            } else {
                localSlots[l.index()] = cursor;
                cursor += slots(l.type());
            }
        }
        int scratch = cursor;
        int scratchSlots = 0;
        for (Ir.Block b : m.blocks()) {
            for (Ir.Op op : b.ops()) {
                if (op instanceof Ir.NewMap nm) scratchSlots = Math.max(scratchSlots, 4 * nm.keyTypes().size());
                if (op instanceof Ir.NewList nl) scratchSlots = Math.max(scratchSlots, 2 * nl.valueTypes().size());
                if (op instanceof Ir.NewSet ns) scratchSlots = Math.max(scratchSlots, 2 * ns.valueTypes().size());
            }
        }
        int maxLocals = Math.max(cursor + scratchSlots + 2, 3);

        // try-catch: regiões fechadas (TryStart…TryEnd) pareiam, em ordem, com
        // os CatchStart seguintes — o layout de blocos do IR não é linear
        // (handler do try#1 pode vir depois do try#2), então nada de associar
        // pelo "try ativo" no momento do CatchStart.
        record TryPair(Label start, Label end, Label handler) {}
        List<TryPair> tries = new java.util.ArrayList<>();
        java.util.ArrayDeque<Label[]> closedRegions = new java.util.ArrayDeque<>();
        Label tryStart = null;
        Label tryEnd = null;

        for (Ir.Block b : m.blocks()) {
            Label blockLabel = blockLabels.get(b.index());
            mv.visitLabel(blockLabel);
            for (Ir.Op op : b.ops()) {
                if (op instanceof Ir.TryStart) {
                    tryStart = new Label();
                    mv.visitLabel(tryStart);
                } else if (op instanceof Ir.TryEnd) {
                    tryEnd = new Label();
                    mv.visitLabel(tryEnd);
                    if (tryStart != null) {
                        closedRegions.addLast(new Label[]{tryStart, tryEnd});
                    }
                    tryStart = null;
                    tryEnd = null;
                } else if (op instanceof Ir.CatchStart cs) {
                    if (!closedRegions.isEmpty()) {
                        Label[] region = closedRegions.removeFirst();
                        tries.add(new TryPair(region[0], region[1], blockLabel));
                    }
                    // catch (e) binding: e é String na linguagem; o throw do
                    // Hydra carrega RuntimeException(msg) — extrai a mensagem.
                    unwrapExceptionMessage(mv, localSlots[cs.localIndex()]);
                } else {
                    emitOp(mv, op, blockLabels, localSlots, scratch, m, owner);
                }
            }
        }

        for (TryPair t : tries) {
            mv.visitTryCatchBlock(t.start(), t.end(), t.handler(), "java/lang/Throwable");
        }

        mv.visitMaxs(0, maxLocals);
        mv.visitEnd();
    }

    /** Pilha: [Throwable] → slot recebe getMessage() (ou toString se null). */
    private static void unwrapExceptionMessage(MethodVisitor mv, int slot) {
        mv.visitVarInsn(ASTORE, slot);
        Label nullMsg = new Label();
        Label done = new Label();
        mv.visitVarInsn(ALOAD, slot);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "getMessage",
                "()Ljava/lang/String;", false);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, nullMsg);
        mv.visitVarInsn(ASTORE, slot);
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(nullMsg);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, slot);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString",
                "()Ljava/lang/String;", false);
        mv.visitVarInsn(ASTORE, slot);
        mv.visitLabel(done);
    }

    private void emitOp(MethodVisitor mv, Ir.Op op, Map<Integer, Label> blockLabels,
                        int[] localSlots, int scratch, Ir.Method m, String owner) {
        if (op instanceof Ir.LoadLiteral lit) {
            emitLiteral(mv, lit);
        } else if (op instanceof Ir.LoadLocal ll) {
            mv.visitVarInsn(loadOpcode(ll.type()), localSlots[ll.index()]);
        } else if (op instanceof Ir.StoreLocal sl) {
            mv.visitVarInsn(storeOpcode(sl.type()), localSlots[sl.index()]);
        } else if (op instanceof Ir.Binary bin) {
            emitBinary(mv, bin);
        } else if (op instanceof Ir.Unary un) {
            emitUnary(mv, un);
        } else if (op instanceof Ir.Call call) {
            emitCall(mv, call, scratch);
        } else if (op instanceof Ir.Return ret) {
            if (ret.returnType().isVoid()) mv.visitInsn(RETURN);
            else mv.visitInsn(returnOpcode(ret.returnType()));
        } else if (op instanceof Ir.Pop p) {
            mv.visitInsn(slots(p.type()) == 2 ? POP2 : POP);
        } else if (op instanceof Ir.NewList nl) {
            emitNewList(mv, nl, scratch);
        } else if (op instanceof Ir.NewSet ns) {
            emitNewSet(mv, ns, scratch);
        } else if (op instanceof Ir.NewMap nm) {
            emitNewMap(mv, nm, scratch);
        } else if (op instanceof Ir.IndexGet ig) {
            emitIndexGet(mv, ig);
        } else if (op instanceof Ir.Length len) {
            emitLength(mv, len);
        } else if (op instanceof Ir.Contains c) {
            emitContains(mv, c);
        } else if (op instanceof Ir.IterInit ii) {
            if (ii.collectionType().name().startsWith("Map<")) {
                // for k in map: itera as chaves (ordem de inserção)
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "keySet",
                        "()Ljava/util/Set;", true);
            }
            mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator",
                    "()Ljava/util/Iterator;", true);
            mv.visitVarInsn(ASTORE, localSlots[ii.localIndex()]);
        } else if (op instanceof Ir.IterNext in) {
            emitIterNext(mv, in, localSlots, blockLabels);
        } else if (op instanceof Ir.ReadLine) {
            // BufferedReader estático cacheado: várias readLine() no mesmo
            // programa não podem recriar o reader (perde buffer)
            Label ready = new Label();
            mv.visitFieldInsn(GETSTATIC, owner, "$stdin", "Ljava/io/BufferedReader;");
            mv.visitJumpInsn(IFNONNULL, ready);
            mv.visitTypeInsn(NEW, "java/io/BufferedReader");
            mv.visitInsn(DUP);
            mv.visitTypeInsn(NEW, "java/io/InputStreamReader");
            mv.visitInsn(DUP);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
            mv.visitMethodInsn(INVOKESPECIAL, "java/io/InputStreamReader", "<init>",
                    "(Ljava/io/InputStream;)V", false);
            mv.visitMethodInsn(INVOKESPECIAL, "java/io/BufferedReader", "<init>",
                    "(Ljava/io/Reader;)V", false);
            mv.visitFieldInsn(PUTSTATIC, owner, "$stdin", "Ljava/io/BufferedReader;");
            mv.visitLabel(ready);
            mv.visitFieldInsn(GETSTATIC, owner, "$stdin", "Ljava/io/BufferedReader;");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/BufferedReader", "readLine",
                    "()Ljava/lang/String;", false);
            Label nn = new Label();
            mv.visitInsn(DUP);
            mv.visitJumpInsn(IFNONNULL, nn);
            mv.visitInsn(POP);
            mv.visitLdcInsn("");
            mv.visitLabel(nn);
        } else if (op instanceof Ir.Jump j) {
            mv.visitJumpInsn(GOTO, blockLabels.get(j.targetBlock()));
        } else if (op instanceof Ir.JumpIfFalse jf) {
            // condições v1 resultam em int 0/1
            mv.visitJumpInsn(IFEQ, blockLabels.get(jf.targetBlock()));
        } else if (op instanceof Ir.LoadEnum le) {
            mv.visitLdcInsn(le.enumName() + "." + le.caseName());
        } else if (op instanceof Ir.Throw) {
            // pilha: [String msg] → RuntimeException(msg)
            mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            mv.visitInsn(DUP_X1);
            mv.visitInsn(SWAP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>",
                    "(Ljava/lang/String;)V", false);
            mv.visitInsn(ATHROW);
        } else if (op instanceof Ir.Assert asrt) {
            emitAssert(mv, asrt);
        } else if (op instanceof Ir.ToString ts) {
            emitToString(mv, ts.fromType());
        } else if (op instanceof Ir.TryStart || op instanceof Ir.TryEnd) {
            // tratado no emitMethod (try-catch table)
        } else if (op instanceof Ir.CatchStart) {
            // tratado no emitMethod (ASTORE do handler)
        } else if (op instanceof Ir.LoadField lf) {
            throw new IllegalStateException("JVM v1: LoadField " + lf.typeName() + "." + lf.fieldName());
        } else if (op instanceof Ir.StoreField sf) {
            throw new IllegalStateException("JVM v1: StoreField " + sf.typeName() + "." + sf.fieldName());
        } else if (op instanceof Ir.NewObject) {
            throw new IllegalStateException("JVM v1: NewObject (v2)");
        } else {
            throw new IllegalStateException("JVM: op não suportada: " + op.getClass().getSimpleName());
        }
    }

    /**
     * Assert: pilha [cond(int)]; se 0, lança Throwable("<message>").
     * O Throwable permite que try/catch na fonte Hydra capture e siga.
     */
    /**
     * NewList/NewSet/NewMap: os N valores estão na pilha;
     * guarda em slots scratch pareados (2 slots cada) e chama add/put.
     */
    private void emitNewList(MethodVisitor mv, Ir.NewList nl, int scratchBase) {
        int n = nl.valueTypes().size();
        for (int i = n - 1; i >= 0; i--) {
            boxBalance(mv, nl.valueTypes().get(i));
            mv.visitVarInsn(ASTORE, scratchBase + 2 * i);
        }
        mv.visitTypeInsn(NEW, "java/util/ArrayList");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        for (int i = 0; i < n; i++) {
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, scratchBase + 2 * i);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Collection", "add",
                    "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(POP);
        }
    }

    /** Converte primitivo no topo para wrapper (referência); no-op para refs. */
    private static void boxBalance(MethodVisitor mv, Ir.Type t) {
        if (Ir.Type.INT.equals(t)) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf",
                    "(J)Ljava/lang/Long;", false);
        } else if (Ir.Type.FLOAT.equals(t)) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf",
                    "(D)Ljava/lang/Double;", false);
        } else if (Ir.Type.BOOL.equals(t)) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                    "(Z)Ljava/lang/Boolean;", false);
        }
    }

    private void emitNewSet(MethodVisitor mv, Ir.NewSet ns, int scratchBase) {
        int n = ns.valueTypes().size();
        for (int i = n - 1; i >= 0; i--) {
            boxBalance(mv, ns.valueTypes().get(i));
            mv.visitVarInsn(ASTORE, scratchBase + 2 * i);
        }
        mv.visitTypeInsn(NEW, "java/util/LinkedHashSet");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashSet", "<init>", "()V", false);
        for (int i = 0; i < n; i++) {
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, scratchBase + 2 * i);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Collection", "add",
                    "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(POP);
        }
    }

    private void emitNewMap(MethodVisitor mv, Ir.NewMap nm, int scratchBase) {
        int n = nm.keyTypes().size();
        // pilha: k0 v0 k1 v1 … (vn-1 no topo) → slots 4i (k), 4i+2 (v)
        for (int i = n - 1; i >= 0; i--) {
            boxBalance(mv, nm.valueTypes().get(i));
            mv.visitVarInsn(ASTORE, scratchBase + 4 * i + 2);
            boxBalance(mv, nm.keyTypes().get(i));
            mv.visitVarInsn(ASTORE, scratchBase + 4 * i);
        }
        mv.visitTypeInsn(NEW, "java/util/LinkedHashMap");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
        for (int i = 0; i < n; i++) {
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, scratchBase + 4 * i);
            mv.visitVarInsn(ALOAD, scratchBase + 4 * i + 2);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitInsn(POP);
        }
    }

    private void emitIndexGet(MethodVisitor mv, Ir.IndexGet ig) {
        if (ig.collectionType().name().startsWith("Map<")) {
            // [map, key] → box(key) → Map.get(Object)
            boxBalance(mv, ig.collectionType().keyType());
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        } else {
            // [list, index(long)] → get(int)
            mv.visitInsn(L2I);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get",
                    "(I)Ljava/lang/Object;", true);
        }
        unbox(mv, ig.resultType());
    }

    private void unbox(MethodVisitor mv, Ir.Type t) {
        if (t == null || Ir.Type.ANY.equals(t) || Ir.Type.VOID.equals(t)) return;
        if (Ir.Type.INT.equals(t)) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (Ir.Type.FLOAT.equals(t)) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else if (Ir.Type.BOOL.equals(t)) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (Ir.Type.STRING.equals(t)) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
        }
    }

    private void emitLength(MethodVisitor mv, Ir.Length l) {
        if (Ir.Type.STRING.equals(l.subjectType())) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        } else if (l.subjectType().name().startsWith("Map<")) {
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "size", "()I", true);
        } else {
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Collection", "size", "()I", true);
        }
        mv.visitInsn(I2L);
    }

    private void emitContains(MethodVisitor mv, Ir.Contains c) {
        if (Ir.Type.STRING.equals(c.collectionType())) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z", false);
        } else if (c.collectionType().name().startsWith("Map<")) {
            boxBalance(mv, c.valueType());
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "containsKey",
                    "(Ljava/lang/Object;)Z", true);
        } else {
            boxBalance(mv, c.valueType());
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);
        }
    }

    private void emitIterNext(MethodVisitor mv, Ir.IterNext in, int[] localSlots,
                              Map<Integer, Label> blockLabels) {
        Label has = new Label();
        mv.visitVarInsn(ALOAD, localSlots[in.localIndex()]);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        mv.visitJumpInsn(IFNE, has);
        mv.visitJumpInsn(GOTO, blockLabels.get(in.exitBlock()));
        mv.visitLabel(has);
        mv.visitVarInsn(ALOAD, localSlots[in.localIndex()]);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next",
                "()Ljava/lang/Object;", true);
        unbox(mv, in.elementType());
        mv.visitVarInsn(storeOpcode(in.elementType()), localSlots[in.valueLocalIndex()]);
    }

    private static void pushInt(MethodVisitor mv, int n) {
        if (n >= 0 && n <= 5) mv.visitInsn(ICONST_0 + n);
        else if (n <= Byte.MAX_VALUE) mv.visitIntInsn(BIPUSH, n);
        else mv.visitLdcInsn(n);
    }

    private void emitToString(MethodVisitor mv, Ir.Type from) {
        String desc = switch (from.name()) {
            case "Int" -> "(J)Ljava/lang/String;";
            case "Float" -> "(D)Ljava/lang/String;";
            case "Bool" -> "(Z)Ljava/lang/String;";
            default -> "(Ljava/lang/Object;)Ljava/lang/String;";
        };
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", desc, false);
    }

    private void emitAssert(MethodVisitor mv, Ir.Assert asrt) {
        Label ok = new Label();
        mv.visitJumpInsn(IFNE, ok);
        mv.visitTypeInsn(NEW, "java/lang/Throwable");
        mv.visitInsn(DUP);
        mv.visitLdcInsn(asrt.message() == null ? "assertion failed" : asrt.message());
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Throwable", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
        mv.visitLabel(ok);
    }

    private void emitLiteral(MethodVisitor mv, Ir.LoadLiteral lit) {        Ir.Type t = lit.type();
        Object v = lit.value();
        if (Ir.Type.INT.equals(t)) {
            mv.visitLdcInsn(v == null ? 0L : ((Number) v).longValue());
        } else if (Ir.Type.FLOAT.equals(t)) {
            mv.visitLdcInsn(v == null ? 0.0 : ((Number) v).doubleValue());
        } else if (Ir.Type.STRING.equals(t)) {
            mv.visitLdcInsn(v == null ? "" : v.toString());
        } else if (Ir.Type.BOOL.equals(t)) {
            mv.visitInsn(v instanceof Boolean b && b ? ICONST_1 : ICONST_0);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
    }

    private void emitBinary(MethodVisitor mv, Ir.Binary bin) {
        String op = bin.op();
        Ir.Type t = bin.operandType();

        // string +: pilha [a, b] → a.concat(b)
        if ("+".equals(op) && Ir.Type.STRING.equals(t)) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);
            return;
        }

        // String: == / != por conteúdo (a linguagem não expõe identidade)
        if (Ir.Type.STRING.equals(t) && ("==".equals(op) || "!=".equals(op))) {
            Label t1 = new Label();
            Label e1 = new Label();
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn("==".equals(op) ? IFNE : IFEQ, t1);
            mv.visitInsn(ICONST_0);
            mv.visitJumpInsn(GOTO, e1);
            mv.visitLabel(t1);
            mv.visitInsn(ICONST_1);
            mv.visitLabel(e1);
            return;
        }

        if (Ir.Type.FLOAT.equals(t)) {
            switch (op) {
                case "+" -> mv.visitInsn(DADD);
                case "-" -> mv.visitInsn(DSUB);
                case "*" -> mv.visitInsn(DMUL);
                case "/" -> mv.visitInsn(DDIV);
                case "%" -> mv.visitInsn(DREM);
                case "==" -> doubleBool(mv, true);
                case "!=" -> doubleBool(mv, false);
                case "<" -> doubleBranch(mv, IFGE);
                case ">" -> doubleBranch(mv, IFLE);
                case "<=" -> doubleBranch(mv, IFGT);
                case ">=" -> doubleBranch(mv, IFLT);
                default -> throw new IllegalStateException("JVM: op float: " + op);
            }
        } else if (Ir.Type.BOOL.equals(t) && ("&&".equals(op) || "||".equals(op))) {
            mv.visitInsn("&&".equals(op) ? IAND : IOR);
        } else if (Ir.Type.BOOL.equals(t)) {
            emitIntBinary(mv, op);
        } else if (Ir.Type.ANY.equals(t) || "Object".equals(t.name())) {
            // referências (null em nullable/type-test): identidade
            switch (op) {
                case "==" -> refBool(mv, true);
                case "!=" -> refBool(mv, false);
                default -> throw new IllegalStateException("JVM: op ref: " + op);
            }
        } else {
            // Int (long)
            emitLongBinary(mv, op);
        }
    }

    private void refBool(MethodVisitor mv, boolean eq) {
        Label t = new Label();
        Label e = new Label();
        mv.visitJumpInsn(eq ? IF_ACMPEQ : IF_ACMPNE, t);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, e);
        mv.visitLabel(t);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(e);
    }

    private void emitIntBinary(MethodVisitor mv, String op) {
        switch (op) {
            case "+" -> mv.visitInsn(IADD);
            case "-" -> mv.visitInsn(ISUB);
            case "*" -> mv.visitInsn(IMUL);
            case "/" -> mv.visitInsn(IDIV);
            case "%" -> mv.visitInsn(IREM);
            case "&" -> mv.visitInsn(IAND);
            case "|" -> mv.visitInsn(IOR);
            case "^" -> mv.visitInsn(IXOR);
            case "==" -> intBool(mv, true);
            case "!=" -> intBool(mv, false);
            case "<" -> intBranch(mv, IFGE);
            case ">" -> intBranch(mv, IFLE);
            case "<=" -> intBranch(mv, IFGT);
            case ">=" -> intBranch(mv, IFLT);
            case "&&" -> mv.visitInsn(IAND);
            case "||" -> mv.visitInsn(IOR);
            default -> throw new IllegalStateException("JVM: op int: " + op);
        }
    }

    private void intBool(MethodVisitor mv, boolean eq) {
        Label t = new Label();
        Label e = new Label();
        mv.visitJumpInsn(eq ? IF_ICMPEQ : IF_ICMPNE, t);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, e);
        mv.visitLabel(t);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(e);
    }

    private void intBranch(MethodVisitor mv, int skip) {
        Label t = new Label();
        Label e = new Label();
        mv.visitJumpInsn(skip, e);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(GOTO, t);
        mv.visitLabel(e);
        mv.visitInsn(ICONST_0);
        mv.visitLabel(t);
    }

    private void doubleBool(MethodVisitor mv, boolean eq) {
        Label t = new Label();
        Label e = new Label();
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "compare", "(DD)I", false);
        mv.visitJumpInsn(eq ? IFEQ : IFNE, t);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, e);
        mv.visitLabel(t);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(e);
    }

    private void doubleBranch(MethodVisitor mv, int skip) {
        Label t = new Label();
        Label e = new Label();
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "compare", "(DD)I", false);
        mv.visitJumpInsn(skip, e);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(GOTO, t);
        mv.visitLabel(e);
        mv.visitInsn(ICONST_0);
        mv.visitLabel(t);
    }

    private void emitLongBinary(MethodVisitor mv, String op) {
        switch (op) {
            case "+" -> mv.visitInsn(LADD);
            case "-" -> mv.visitInsn(LSUB);
            case "*" -> mv.visitInsn(LMUL);
            case "/" -> mv.visitInsn(LDIV);
            case "%" -> mv.visitInsn(LREM);
            case "&" -> mv.visitInsn(LAND);
            case "|" -> mv.visitInsn(LOR);
            case "^" -> mv.visitInsn(LXOR);
            case "==" -> longBool(mv, true);
            case "!=" -> longBool(mv, false);
            case "<" -> longBranch(mv, IFGE);
            case ">" -> longBranch(mv, IFLE);
            case "<=" -> longBranch(mv, IFGT);
            case ">=" -> longBranch(mv, IFLT);
            default -> throw new IllegalStateException("JVM: op long: " + op);
        }
    }

    private void longBool(MethodVisitor mv, boolean eq) {
        Label t = new Label();
        Label e = new Label();
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(eq ? IFEQ : IFNE, t);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, e);
        mv.visitLabel(t);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(e);
    }

    private void longBranch(MethodVisitor mv, int skip) {
        Label t = new Label();
        Label e = new Label();
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(skip, e);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(GOTO, t);
        mv.visitLabel(e);
        mv.visitInsn(ICONST_0);
        mv.visitLabel(t);
    }

    private void emitUnary(MethodVisitor mv, Ir.Unary un) {
        if ("-".equals(un.op())) {
            mv.visitInsn(Ir.Type.FLOAT.equals(un.operandType()) ? DNEG : LNEG);
        } else if ("!".equals(un.op())) {
            Label t = new Label();
            Label e = new Label();
            mv.visitJumpInsn(IFNE, e);
            mv.visitInsn(ICONST_1);
            mv.visitJumpInsn(GOTO, t);
            mv.visitLabel(e);
            mv.visitInsn(ICONST_0);
            mv.visitLabel(t);
        } else {
            throw new IllegalStateException("JVM: unary: " + un.op());
        }
    }

    private void emitCall(MethodVisitor mv, Ir.Call call, int scratch) {
        String name = call.name();
        if ("println".equals(name) || "print".equals(name)) {
            int n = call.parameterTypes().size();
            if (n == 0) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
                return;
            }
            if (n != 1) {
                throw new IllegalStateException("JVM v1: println com " + n + " args");
            }
            Ir.Type at = call.parameterTypes().get(0);
            boolean refToObject = !Ir.Type.STRING.equals(at) && !Ir.Type.INT.equals(at)
                    && !Ir.Type.FLOAT.equals(at) && !Ir.Type.BOOL.equals(at);
            String pd = switch (at.name()) {
                case "Int" -> "(J)V";
                case "Float" -> "(D)V";
                case "Bool" -> "(Z)V";
                default -> refToObject ? "(Ljava/lang/Object;)V" : "(Ljava/lang/String;)V";
            };
            // pilha: [arg] → [out, arg]
            if (Ir.Type.INT.equals(at) || Ir.Type.FLOAT.equals(at)) {
                int opc = Ir.Type.INT.equals(at) ? LSTORE : DSTORE;
                int lopc = Ir.Type.INT.equals(at) ? LLOAD : DLOAD;
                mv.visitVarInsn(opc, scratch);
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitVarInsn(lopc, scratch);
            } else {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitInsn(SWAP);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", pd, false);
            return;
        }
        if (call.hasReceiver()) {
            throw new IllegalStateException("JVM v1: call com receiver: " + name);
        }
        StringBuilder d = new StringBuilder("(");
        for (Ir.Type t : call.parameterTypes()) d.append(jvmDesc(t));
        d.append(')').append(jvmDesc(call.returnType()));
        mv.visitMethodInsn(INVOKESTATIC, "Main", Ir.safeName(name), d.toString(), false);
    }

    private static String methodDesc(Ir.Method m) {
        StringBuilder d = new StringBuilder("(");
        for (Ir.Type t : m.parameterTypes()) d.append(jvmDesc(t));
        return d.append(')').append(jvmDesc(m.returnType())).toString();
    }

    private static String jvmDesc(Ir.Type t) {
        if (t.isCollection()) {
            if (t.name().startsWith("List<")) return "Ljava/util/List;";
            if (t.name().startsWith("Set<")) return "Ljava/util/Set;";
            return "Ljava/util/Map;";
        }
        return switch (t.name()) {
            case "Int" -> "J";
            case "Float" -> "D";
            case "Bool" -> "Z";
            case "Void" -> "V";
            case "String" -> "Ljava/lang/String;";
            case "Any", "Object" -> "Ljava/lang/Object;";
            default -> "L" + t.name() + ";";
        };
    }

    private static int slots(Ir.Type t) {
        return Ir.Type.INT.equals(t) || Ir.Type.FLOAT.equals(t) ? 2 : 1;
    }

    private static int loadOpcode(Ir.Type t) {
        if (Ir.Type.INT.equals(t)) return LLOAD;
        if (Ir.Type.FLOAT.equals(t)) return DLOAD;
        if (Ir.Type.BOOL.equals(t)) return ILOAD;
        return ALOAD;
    }

    private static int storeOpcode(Ir.Type t) {
        if (Ir.Type.INT.equals(t)) return LSTORE;
        if (Ir.Type.FLOAT.equals(t)) return DSTORE;
        if (Ir.Type.BOOL.equals(t)) return ISTORE;
        return ASTORE;
    }

    private static int returnOpcode(Ir.Type t) {
        if (Ir.Type.INT.equals(t)) return LRETURN;
        if (Ir.Type.FLOAT.equals(t)) return DRETURN;
        if (Ir.Type.BOOL.equals(t)) return IRETURN;
        if (Ir.Type.VOID.equals(t)) return RETURN;
        return ARETURN;
    }
}
