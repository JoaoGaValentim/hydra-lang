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
        int maxLocals = Math.max(cursor + 2, 1);

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
                    emitOp(mv, op, blockLabels, localSlots, scratch, m);
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
                        int[] localSlots, int scratch, Ir.Method m) {
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
    /** Pilha: [valor] → [String.valueOf(valor)]. */
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
            String pd = switch (at.name()) {
                case "Int" -> "(J)V";
                case "Float" -> "(D)V";
                case "Bool" -> "(Z)V";
                default -> "(Ljava/lang/String;)V";
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
        if (Ir.Type.STRING.equals(t) || Ir.Type.ANY.equals(t)) return ALOAD;
        return ILOAD;
    }

    private static int storeOpcode(Ir.Type t) {
        if (Ir.Type.INT.equals(t)) return LSTORE;
        if (Ir.Type.FLOAT.equals(t)) return DSTORE;
        if (Ir.Type.STRING.equals(t) || Ir.Type.ANY.equals(t)) return ASTORE;
        return ISTORE;
    }

    private static int returnOpcode(Ir.Type t) {
        if (Ir.Type.INT.equals(t)) return LRETURN;
        if (Ir.Type.FLOAT.equals(t)) return DRETURN;
        if (Ir.Type.STRING.equals(t) || Ir.Type.ANY.equals(t)) return ARETURN;
        return IRETURN;
    }
}
