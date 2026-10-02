package hydra.compiler.ir;

import java.util.List;

/**
 * IR do Hydra (F3-01) — contrato estável entre frontend e backends.
 * Shape espelha o IR do Kof (D-HYD-002): módulo → classes → métodos →
 * basic blocks → ops de pilha. Tipos são strings canônicas (Int, Float,
 * String, Bool, Void, nome de tipo) para o subset v1.
 */
public final class Ir {
    private Ir() {}

    /** Tipo canônico da IR. */
    public record Type(String name) {
        public static final Type INT = new Type("Int");
        public static final Type FLOAT = new Type("Float");
        public static final Type STRING = new Type("String");
        public static final Type BOOL = new Type("Bool");
        public static final Type VOID = new Type("Void");
        public static final Type ANY = new Type("Any");

        public boolean isVoid() {
            return "Void".equals(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public record Module(String name, List<Class> classes, List<String> imports, String sourceName) {}

    public record Class(
            String name,
            String superName,
            List<Field> fields,
            List<Method> methods,
            List<String> enumCases) {}

    public record Field(String name, Type type, boolean mutable, Object initialValue) {}

    public record Method(
            String name,
            Type returnType,
            List<Type> parameterTypes,
            List<Local> locals,
            List<Block> blocks) {}

    public record Local(int index, String name, Type type) {}

    public record Block(int index, List<Op> ops) {}

    /** Operação de pilha (espelha KofOperation). */
    public sealed interface Op {}

    public record LoadLiteral(Type type, Object value) implements Op {}

    public record LoadLocal(int index, Type type) implements Op {}

    public record StoreLocal(int index, Type type) implements Op {}

    public record Binary(String op, Type operandType) implements Op {}

    public record Unary(String op, Type operandType) implements Op {}

    /** Chamada de função top-level ou built-in (receiver sempre na pilha se houver). */
    public record Call(String name, List<Type> parameterTypes, Type returnType, boolean hasReceiver)
            implements Op {}

    public record Return(Type returnType) implements Op {}

    /** Descarta o topo da pilha; Type decide POP (1 slot) vs POP2 (2 slots). */
    public record Pop(Type type) implements Op {}
    /** Salto incondicional para bloco índice. */
    public record Jump(int targetBlock) implements Op {}

    /** Desempilha Bool; salta se falso. */
    public record JumpIfFalse(int targetBlock) implements Op {}

    /** Empilha constante de enum (Color.Red). */
    public record LoadEnum(String enumName, String caseName) implements Op {}

    /** Cria instância de tipo (User(...)) com N args na pilha. */
    public record NewObject(String typeName, List<Type> fieldTypes) implements Op {}

    public record LoadField(String typeName, String fieldName, Type fieldType) implements Op {}

    public record StoreField(String typeName, String fieldName, Type fieldType) implements Op {}

    /** Desempilha valor e empilha sua forma String ("1", "true", ...). */
    public record ToString(Type fromType) implements Op {}

    /** Desempilha valor de erro e lança. */
    public record Throw() implements Op {}

    /** Desempilha a condição Bool; se falsa, lança erro com a mensagem. */
    public record Assert(String message) implements Op {}

    /** Nome seguro para backends (JVM/JS): test "x y" → test_x_y. */
    public static String safeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        String s = sb.toString();
        if (s.isEmpty()) return "_";
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return "_" + s;
        return s;
    }

    /** Início de região try; endereço de handler é o próximo JumpIf... do catch. */
    public record TryStart() implements Op {}

    public record TryEnd() implements Op {}

    /** Captura exceção (String) em local index. */
    public record CatchStart(int localIndex, Type exceptionType) implements Op {}

    /** Forma canônica da IR para asserções de teste. */
    public static String shape(Module m) {
        StringBuilder sb = new StringBuilder();
        sb.append("module(").append(m.name()).append(")\n");
        if (m.imports() != null && !m.imports().isEmpty()) {
            sb.append("imports(").append(String.join(",", m.imports())).append(")\n");
        }
        for (Class c : m.classes()) shapeClass(c, sb, 0);
        return sb.toString();
    }

    private static void shapeClass(Class c, StringBuilder sb, int ind) {
        String pad = "  ".repeat(ind);
        sb.append(pad).append("class(").append(c.name());
        if (c.superName() != null && !c.superName().isEmpty()) {
            sb.append("; extends=").append(c.superName());
        }
        if (c.enumCases() != null && !c.enumCases().isEmpty()) {
            sb.append("; enum=").append(String.join(",", c.enumCases()));
        }
        sb.append(") {\n");
        for (Field f : c.fields()) {
            sb.append(pad).append("  field(").append(f.name()).append(':').append(f.type().name());
            if (f.initialValue() != null) sb.append("; init=").append(f.initialValue());
            sb.append(")\n");
        }
        for (Method m : c.methods()) shapeMethod(m, sb, ind + 1);
        sb.append(pad).append("}\n");
    }

    private static void shapeMethod(Method m, StringBuilder sb, int ind) {
        String pad = "  ".repeat(ind);
        sb.append(pad).append("method(").append(m.name());
        for (Type t : m.parameterTypes()) sb.append("; ").append(t.name());
        sb.append("; ret=").append(m.returnType().name()).append(") {\n");
        if (!m.locals().isEmpty()) {
            sb.append(pad).append("  locals:");
            for (Local l : m.locals()) {
                sb.append(' ').append(l.index()).append(':').append(l.name()).append(':').append(l.type().name());
            }
            sb.append('\n');
        }
        for (Block b : m.blocks()) {
            sb.append(pad).append("  block(").append(b.index()).append(") {\n");
            for (Op op : b.ops()) {
                sb.append(pad).append("    ").append(shapeOp(op)).append('\n');
            }
            sb.append(pad).append("  }\n");
        }
        sb.append(pad).append("}\n");
    }

    private static String shapeOp(Op op) {
        if (op instanceof LoadLiteral lit) {
            return "loadLit(" + lit.type().name() + "; " + lit.value() + ")";
        }
        if (op instanceof LoadLocal ll) {
            return "loadLocal(" + ll.index() + "; " + ll.type().name() + ")";
        }
        if (op instanceof StoreLocal sl) {
            return "storeLocal(" + sl.index() + "; " + sl.type().name() + ")";
        }
        if (op instanceof Binary b) {
            return "binary(" + b.op() + "; " + b.operandType().name() + ")";
        }
        if (op instanceof Unary u) {
            return "unary(" + u.op() + "; " + u.operandType().name() + ")";
        }
        if (op instanceof Call c) {
            StringBuilder sb = new StringBuilder("call(").append(c.name());
            for (Type t : c.parameterTypes()) sb.append("; ").append(t.name());
            sb.append("; ret=").append(c.returnType().name());
            if (c.hasReceiver()) sb.append("; recv");
            return sb.append(')').toString();
        }
        if (op instanceof Return r) {
            return "return(" + r.returnType().name() + ")";
        }
        if (op instanceof Pop p) return "pop(" + p.type().name() + ")";
        if (op instanceof Jump j) return "jump(" + j.targetBlock() + ")";
        if (op instanceof JumpIfFalse jf) return "jumpIfFalse(" + jf.targetBlock() + ")";
        if (op instanceof LoadEnum le) return "loadEnum(" + le.enumName() + "." + le.caseName() + ")";
        if (op instanceof NewObject n) {
            StringBuilder sb = new StringBuilder("new(").append(n.typeName());
            for (Type t : n.fieldTypes()) sb.append("; ").append(t.name());
            return sb.append(')').toString();
        }
        if (op instanceof LoadField lf) {
            return "loadField(" + lf.typeName() + "." + lf.fieldName() + "; " + lf.fieldType().name() + ")";
        }
        if (op instanceof StoreField sf) {
            return "storeField(" + sf.typeName() + "." + sf.fieldName() + "; " + sf.fieldType().name() + ")";
        }
        if (op instanceof Throw) return "throw";
        if (op instanceof ToString ts) return "toString(" + ts.fromType().name() + ")";
        if (op instanceof Assert a) return "assert(" + a.message() + ")";
        if (op instanceof TryStart) return "tryStart";
        if (op instanceof TryEnd) return "tryEnd";
        if (op instanceof CatchStart cs) {
            return "catchStart(local " + cs.localIndex() + "; " + cs.exceptionType().name() + ")";
        }
        return op.getClass().getSimpleName();
    }
}
