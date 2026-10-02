package hydra.compiler.ir;

import hydra.compiler.ast.Ast;
import hydra.compiler.ir.Ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F3-01 — lowering AST Hydra → IR (contrato estável, shape Kof-like).
 * Subconjunto v1: funções, val/var, if, return, chamadas, bin/unary,
 * literals, type-decl, enum, try/catch/throw, match, for, assign.
 */
public final class IrBuilder {

    private final Map<String, Ir.Type> typeOf = new HashMap<>();
    private final Map<String, List<Ir.Type>> funParams = new HashMap<>();
    private final Map<String, Ir.Type> funRet = new HashMap<>();
    private String currentTypeOwner;

    public Ir.Module build(Ast.Unit unit, String moduleName) {
        for (Ast.Decl d : unit.decls()) {
            if (d instanceof Ast.FunDecl f) {
                funParams.put(f.name(), paramTypes(f.params()));
                funRet.put(f.name(), retType(f.returnType()));
            } else if (d instanceof Ast.TypeDecl t) {
                for (Ast.Field fl : t.fields()) {
                    typeOf.put(t.name() + "." + fl.name(), irType(fl.type()));
                }
                typeOf.put(t.name(), new Ir.Type(t.name()));
            } else if (d instanceof Ast.EnumDecl e) {
                typeOf.put(e.name(), new Ir.Type(e.name()));
            }
        }

        List<Ir.Class> classes = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        for (Ast.ImportDecl imp : unit.imports()) {
            imports.add(String.join(".", imp.parts()));
        }

        List<Ir.Field> topFields = new ArrayList<>();
        List<Ir.Method> topMethods = new ArrayList<>();
        List<String> enumCases = new ArrayList<>();

        for (Ast.Decl d : unit.decls()) {
            if (d instanceof Ast.FunDecl f) {
                if (f.body() != null) topMethods.add(lowerFun(f));
            } else if (d instanceof Ast.TypeDecl t) {
                classes.add(lowerType(t));
            } else if (d instanceof Ast.EnumDecl e) {
                enumCases.addAll(e.cases());
                classes.add(new Ir.Class(e.name(), "Enum", List.of(), List.of(), List.copyOf(e.cases())));
            }
        }

        classes.add(0, new Ir.Class("Main", "Object", topFields, topMethods, enumCases));
        return new Ir.Module(moduleName, List.copyOf(classes), imports, null);
    }

    private Ir.Method lowerFun(Ast.FunDecl f) {
        MethodCtx ctx = new MethodCtx(retType(f.returnType()), paramTypes(f.params()));
        for (Ast.Param p : f.params()) {
            ctx.declare(p.name(), irType(p.type()));
        }
        String prevOwner = currentTypeOwner;
        lowerBlock(f.body(), ctx);
        currentTypeOwner = prevOwner;
        ctx.ensureTerminated();
        return new Ir.Method(
                f.name(),
                ctx.returnType,
                ctx.paramTypes,
                List.copyOf(ctx.locals),
                List.copyOf(ctx.blocks));
    }

    private Ir.Class lowerType(Ast.TypeDecl t) {
        List<Ir.Field> fields = new ArrayList<>();
        for (Ast.Field fl : t.fields()) {
            fields.add(new Ir.Field(fl.name(), irType(fl.type()), fl.mutable(), null));
        }
        List<Ir.Method> methods = new ArrayList<>();
        String prevOwner = currentTypeOwner;
        currentTypeOwner = t.name();
        for (Ast.FunDecl m : t.methods()) {
            if (m.body() != null) methods.add(lowerFun(m));
        }
        currentTypeOwner = prevOwner;
        String superName = t.extendsType() == null ? "Object" : t.extendsType().name();
        return new Ir.Class(t.name(), superName, fields, methods, List.of());
    }

    private void lowerBlock(Ast.Block block, MethodCtx ctx) {
        for (Ast.Stmt s : block.stmts()) lowerStmt(s, ctx);
    }

    private void lowerStmt(Ast.Stmt s, MethodCtx ctx) {
        if (s instanceof Ast.VarDecl v) {
            Ir.Type t = v.type() != null
                    ? irType(v.type())
                    : v.init() != null ? typeOfExpr(v.init(), ctx) : Ir.Type.ANY;
            if (v.init() != null) lowerExpr(v.init(), ctx);
            else ctx.emit(new Ir.LoadLiteral(t, defaultFor(t)));
            int idx = ctx.declare(v.name(), t);
            ctx.emit(new Ir.StoreLocal(idx, t));
        } else if (s instanceof Ast.Block b) {
            lowerBlock(b, ctx);
        } else if (s instanceof Ast.ReturnStmt r) {
            if (r.value() != null) {
                lowerExpr(r.value(), ctx);
                ctx.emit(new Ir.Return(typeOfExpr(r.value(), ctx)));
            } else {
                ctx.emit(new Ir.Return(Ir.Type.VOID));
            }
        } else if (s instanceof Ast.IfStmt i) {
            lowerIf(i.cond(), i.thenBlock(), i.elseBlock(), ctx);
        } else if (s instanceof Ast.ExprStmt e) {
            lowerExpr(e.expr(), ctx);
            if (!typeOfExpr(e.expr(), ctx).isVoid()) ctx.emit(new Ir.Pop());
        } else if (s instanceof Ast.ThrowStmt t) {
            lowerExpr(t.value(), ctx);
            ctx.emit(new Ir.Throw());
        } else if (s instanceof Ast.TryStmt t) {
            lowerTry(t, ctx);
        } else if (s instanceof Ast.ForStmt f) {
            lowerFor(f, ctx);
        } else if (s instanceof Ast.SpawnStmt sp) {
            lowerExpr(sp.value(), ctx);
            ctx.emit(new Ir.Pop());
        } else if (s instanceof Ast.MatchStmt m) {
            lowerMatch(m, ctx, null);
        } else {
            throw new IllegalStateException("IR: stmt não suportado: " + s.getClass().getSimpleName());
        }
    }

    private void lowerIf(Ast.Expr cond, Ast.Block thenB, Ast.Block elseB, MethodCtx ctx) {
        lowerExpr(cond, ctx);
        int thenIdx = ctx.reserveBlock();
        int endIdx = ctx.reserveBlock();
        int elseIdx = elseB != null ? ctx.reserveBlock() : endIdx;

        ctx.emit(new Ir.JumpIfFalse(elseIdx));

        ctx.openBlock(thenIdx);
        lowerBlock(thenB, ctx);
        ctx.emit(new Ir.Jump(endIdx));

        if (elseB != null) {
            ctx.openBlock(elseIdx);
            lowerBlock(elseB, ctx);
            ctx.emit(new Ir.Jump(endIdx));
        }

        ctx.openBlock(endIdx);
    }

    private void lowerTry(Ast.TryStmt t, MethodCtx ctx) {
        ctx.emit(new Ir.TryStart());
        lowerBlock(t.body(), ctx);
        ctx.emit(new Ir.TryEnd());
        int endIdx = ctx.reserveBlock();
        ctx.emit(new Ir.Jump(endIdx));

        for (Ast.CatchClause c : t.catches()) {
            int handler = ctx.reserveBlock();
            int local = ctx.declare(c.name(), Ir.Type.STRING);
            ctx.openBlock(handler);
            ctx.emit(new Ir.CatchStart(local, Ir.Type.STRING));
            lowerBlock(c.body(), ctx);
            ctx.emit(new Ir.Jump(endIdx));
        }
        ctx.openBlock(endIdx);
    }

    private void lowerFor(Ast.ForStmt f, MethodCtx ctx) {
        Ast.ForHead head = f.head();
        if (head instanceof Ast.ForClassicHead ch) {
            lowerStmt(ch.init(), ctx);
            int loopIdx = ctx.reserveBlock();
            int bodyIdx = ctx.reserveBlock();
            int endIdx = ctx.reserveBlock();
            ctx.emit(new Ir.Jump(loopIdx));

            ctx.openBlock(loopIdx);
            if (ch.cond() != null) {
                lowerExpr(ch.cond(), ctx);
                ctx.emit(new Ir.JumpIfFalse(endIdx));
            }
            ctx.emit(new Ir.Jump(bodyIdx));

            ctx.openBlock(bodyIdx);
            lowerBlock(f.body(), ctx);
            if (ch.update() != null) {
                lowerExpr(ch.update(), ctx);
                if (!typeOfExpr(ch.update(), ctx).isVoid()) ctx.emit(new Ir.Pop());
            }
            ctx.emit(new Ir.Jump(loopIdx));

            ctx.openBlock(endIdx);
        } else if (head instanceof Ast.ForInHead in) {
            lowerExpr(in.iter(), ctx);
            ctx.emit(new Ir.Pop());
            lowerBlock(f.body(), ctx);
        } else if (head instanceof Ast.ForCondHead ch) {
            int loopIdx = ctx.reserveBlock();
            int bodyIdx = ctx.reserveBlock();
            int endIdx = ctx.reserveBlock();
            ctx.emit(new Ir.Jump(loopIdx));

            ctx.openBlock(loopIdx);
            lowerExpr(ch.cond(), ctx);
            ctx.emit(new Ir.JumpIfFalse(endIdx));
            ctx.emit(new Ir.Jump(bodyIdx));

            ctx.openBlock(bodyIdx);
            lowerBlock(f.body(), ctx);
            ctx.emit(new Ir.Jump(loopIdx));

            ctx.openBlock(endIdx);
        }
    }

    private void lowerMatch(Ast.MatchStmt m, MethodCtx ctx, Integer resultLocal) {
        String subjectLocal = null;
        if (m.subject() instanceof Ast.IdentExpr id && ctx.tryIndex(id.name()) != null) {
            subjectLocal = id.name();
        } else {
            lowerExpr(m.subject(), ctx);
        }

        int endIdx = ctx.reserveBlock();
        int n = m.cases().size();
        int[] armIdx = new int[n];
        int[] nextIdx = new int[n];
        for (int i = 0; i < n; i++) armIdx[i] = ctx.reserveBlock();

        int defaultBlock = endIdx;
        for (int i = 0; i < n; i++) {
            if (isDefaultArm(m.cases().get(i))) defaultBlock = armIdx[i];
        }
        for (int i = 0; i < n; i++) {
            nextIdx[i] = (i + 1 < n) ? armIdx[i + 1] : defaultBlock;
        }

        for (int i = 0; i < n; i++) {
            Ast.CaseArm arm = m.cases().get(i);
            if (isDefaultArm(arm)) continue;

            if (subjectLocal != null) {
                ctx.emit(new Ir.LoadLocal(ctx.indexOf(subjectLocal), typeOf(subjectLocal)));
            } else if (i > 0) {
                lowerExpr(m.subject(), ctx);
            }
            lowerCaseTest(arm, ctx);
            ctx.emit(new Ir.JumpIfFalse(nextIdx[i]));
            ctx.openBlock(armIdx[i]);
            emitArmBody(arm, resultLocal, ctx);
            ctx.emit(new Ir.Jump(endIdx));
        }

        if (defaultBlock != endIdx) {
            ctx.openBlock(defaultBlock);
            for (Ast.CaseArm arm : m.cases()) {
                if (isDefaultArm(arm)) emitArmBody(arm, resultLocal, ctx);
            }
            ctx.emit(new Ir.Jump(endIdx));
        }

        ctx.openBlock(endIdx);
    }

    private void emitArmBody(Ast.CaseArm arm, Integer resultLocal, MethodCtx ctx) {
        if (arm.result() != null) {
            lowerExpr(arm.result(), ctx);
            if (resultLocal != null) {
                ctx.emit(new Ir.StoreLocal(resultLocal, Ir.Type.ANY));
            }
        }
    }

    private static boolean isDefaultArm(Ast.CaseArm arm) {
        return arm.patternType() == null
                || arm.patternType().isEmpty()
                || "default".equals(arm.patternType());
    }

    private void lowerCaseTest(Ast.CaseArm arm, MethodCtx ctx) {
        String pt = arm.patternType();
        if (pt == null || pt.isEmpty()) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.BOOL, true));
            return;
        }
        if (pt.contains(".")) {
            int dot = pt.indexOf('.');
            ctx.emit(new Ir.LoadEnum(pt.substring(0, dot), pt.substring(dot + 1)));
            ctx.emit(new Ir.Binary("==", Ir.Type.ANY));
        } else {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.BOOL, true));
        }
    }

    private void lowerExpr(Ast.Expr e, MethodCtx ctx) {
        if (e instanceof Ast.IntLit i) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.INT, i.value()));
        } else if (e instanceof Ast.FloatLit f) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.FLOAT, f.value()));
        } else if (e instanceof Ast.StringLit s) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.STRING, s.value()));
        } else if (e instanceof Ast.BoolLit b) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.BOOL, b.value()));
        } else if (e instanceof Ast.NullLit) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.ANY, null));
        } else if (e instanceof Ast.IdentExpr id) {
            Integer idx = ctx.tryIndex(id.name());
            if (idx != null) {
                ctx.emit(new Ir.LoadLocal(idx, ctx.typeOf(id.name())));
            } else {
                // campo do tipo atual (sem this — D-HYD-014)
                Ir.Type ft = typeOf.getOrDefault(currentTypeOwner + "." + id.name(), null);
                if (ft != null && currentTypeOwner != null) {
                    ctx.emit(new Ir.LoadField(currentTypeOwner, id.name(), ft));
                } else if (typeOf.containsKey(id.name())) {
                    // tipo/enum qualificado? não resolve sozinho
                    throw new IllegalStateException("IR: identificador não resolvido: " + id.name());
                } else {
                    throw new IllegalStateException("IR: identificador não resolvido: " + id.name());
                }
            }
        } else if (e instanceof Ast.BinaryExpr b) {
            lowerExpr(b.left(), ctx);
            lowerExpr(b.right(), ctx);
            Ir.Type lt = typeOfExpr(b.left(), ctx);
            Ir.Type rt = typeOfExpr(b.right(), ctx);
            // Binary.operandType = tipo dos operandos (não o resultado)
            Ir.Type operand = operandType(b.op(), lt, rt);
            ctx.emit(new Ir.Binary(b.op(), operand));
        } else if (e instanceof Ast.UnaryExpr u) {
            lowerExpr(u.operand(), ctx);
            ctx.emit(new Ir.Unary(u.op(), typeOfExpr(u.operand(), ctx)));
        } else if (e instanceof Ast.CallExpr c) {
            lowerCall(c, ctx);
        } else if (e instanceof Ast.FieldExpr fe) {
            // Color.Red — enum constante sem receiver local
            if (fe.receiver() instanceof Ast.IdentExpr re && typeOf.containsKey(re.name())
                    && funParams.get(re.name()) == null && ctx.tryIndex(re.name()) == null) {
                ctx.emit(new Ir.LoadEnum(re.name(), fe.name()));
            } else {
                lowerExpr(fe.receiver(), ctx);
                Ir.Type recv = typeOfExpr(fe.receiver(), ctx);
                Ir.Type ft = typeOf.getOrDefault(recv.name() + "." + fe.name(), Ir.Type.ANY);
                ctx.emit(new Ir.LoadField(recv.name(), fe.name(), ft));
            }
        } else if (e instanceof Ast.AssignExpr a) {
            lowerAssign(a, ctx);
        } else if (e instanceof Ast.IfExpr ie) {
            int temp = ctx.declareTemp(typeOfExpr(ie.thenExpr(), ctx));
            lowerExprInto(ie.cond(), ie.thenExpr(), ie.elseExpr(), temp, ctx);
            ctx.emit(new Ir.LoadLocal(temp, typeOfExpr(ie.thenExpr(), ctx)));
        } else if (e instanceof Ast.MatchExpr me) {
            int temp = ctx.declareTemp(Ir.Type.ANY);
            lowerMatch(me.stmt(), ctx, temp);
            ctx.emit(new Ir.LoadLocal(temp, Ir.Type.ANY));
        } else if (e instanceof Ast.LambdaExpr) {
            throw new IllegalStateException("IR: lambda na v2 (F3-02)");
        } else {
            throw new IllegalStateException("IR: expr não suportada: " + e.getClass().getSimpleName());
        }
    }

    private void lowerExprInto(Ast.Expr cond, Ast.Expr thenE, Ast.Expr elseE, int destLocal, MethodCtx ctx) {
        lowerExpr(cond, ctx);
        int thenIdx = ctx.reserveBlock();
        int endIdx = ctx.reserveBlock();
        int elseIdx = elseE != null ? ctx.reserveBlock() : endIdx;
        ctx.emit(new Ir.JumpIfFalse(elseIdx));

        ctx.openBlock(thenIdx);
        lowerExpr(thenE, ctx);
        ctx.emit(new Ir.StoreLocal(destLocal, typeOfExpr(thenE, ctx)));
        ctx.emit(new Ir.Jump(endIdx));

        if (elseE != null) {
            ctx.openBlock(elseIdx);
            lowerExpr(elseE, ctx);
            ctx.emit(new Ir.StoreLocal(destLocal, typeOfExpr(elseE, ctx)));
            ctx.emit(new Ir.Jump(endIdx));
        }
        ctx.openBlock(endIdx);
    }

    private void lowerCall(Ast.CallExpr c, MethodCtx ctx) {
        if (c.callee() instanceof Ast.IdentExpr id) {
            String name = id.name();
            List<Ir.Type> argTs = new ArrayList<>();
            for (Ast.Expr a : c.args()) {
                lowerExpr(a, ctx);
                argTs.add(typeOfExpr(a, ctx));
            }
            if (funParams.containsKey(name)) {
                List<Ir.Type> expected = funParams.get(name);
                if (expected.size() != c.args().size()) {
                    throw new IllegalStateException("IR: aridade de " + name + ": esperava "
                            + expected.size() + ", achou " + c.args().size());
                }
                ctx.emit(new Ir.Call(name, expected, funRet.getOrDefault(name, Ir.Type.VOID), false));
            } else if ("println".equals(name) || "print".equals(name)) {
                ctx.emit(new Ir.Call(name, argTs, Ir.Type.VOID, false));
            } else if ("listOf".equals(name) || "mapOf".equals(name) || "setOf".equals(name)) {
                String rt = "listOf".equals(name) ? "List" : "Map";
                ctx.emit(new Ir.Call(name, argTs, new Ir.Type(rt), false));
            } else {
                ctx.emit(new Ir.Call(name, argTs, Ir.Type.ANY, false));
            }
        } else if (c.callee() instanceof Ast.FieldExpr fe) {
            lowerExpr(fe.receiver(), ctx);
            List<Ir.Type> argTs = new ArrayList<>();
            for (Ast.Expr a : c.args()) {
                lowerExpr(a, ctx);
                argTs.add(typeOfExpr(a, ctx));
            }
            ctx.emit(new Ir.Call(fe.name(), argTs, Ir.Type.ANY, true));
        } else if (c.callee() instanceof Ast.CallExpr inner) {
            lowerExpr(inner, ctx);
            List<Ir.Type> argTs = new ArrayList<>();
            for (Ast.Expr a : c.args()) {
                lowerExpr(a, ctx);
                argTs.add(typeOfExpr(a, ctx));
            }
            ctx.emit(new Ir.Call("<dynamic>", argTs, Ir.Type.ANY, false));
        } else {
            throw new IllegalStateException("IR: callee não suportado");
        }
    }

    private void lowerAssign(Ast.AssignExpr a, MethodCtx ctx) {
        String op = a.op();
        Ast.Expr target = a.target();
        if (target instanceof Ast.IdentExpr id) {
            Integer idx = ctx.tryIndex(id.name());
            if (idx == null) throw new IllegalStateException("IR: atribuição a não-local: " + id.name());
            Ir.Type t = ctx.typeOf(id.name());
            if ("=".equals(op)) {
                lowerExpr(a.value(), ctx);
            } else {
                ctx.emit(new Ir.LoadLocal(idx, t));
                lowerExpr(a.value(), ctx);
                ctx.emit(new Ir.Binary(op.substring(0, op.length() - 1), t));
            }
            ctx.emit(new Ir.StoreLocal(idx, t));
        } else if (target instanceof Ast.FieldExpr fe) {
            if (!"=".equals(op)) {
                throw new IllegalStateException("IR: compound assign em campo na v2: " + op);
            }
            lowerExpr(fe.receiver(), ctx);
            lowerExpr(a.value(), ctx);
            Ir.Type recv = typeOfExpr(fe.receiver(), ctx);
            Ir.Type ft = typeOf.getOrDefault(recv.name() + "." + fe.name(), Ir.Type.ANY);
            ctx.emit(new Ir.StoreField(recv.name(), fe.name(), ft));
        } else {
            throw new IllegalStateException("IR: alvo de atribuição não suportado");
        }
    }

    private Ir.Type typeOfExpr(Ast.Expr e, MethodCtx ctx) {
        if (e instanceof Ast.IntLit) return Ir.Type.INT;
        if (e instanceof Ast.FloatLit) return Ir.Type.FLOAT;
        if (e instanceof Ast.StringLit) return Ir.Type.STRING;
        if (e instanceof Ast.BoolLit) return Ir.Type.BOOL;
        if (e instanceof Ast.NullLit) return Ir.Type.ANY;
        if (e instanceof Ast.IdentExpr id) {
            Ir.Type local = ctx.tryType(id.name());
            if (local != null) return local;
            if (funRet.containsKey(id.name())) return funRet.get(id.name());
            Ir.Type ft = typeOf.getOrDefault(currentTypeOwner + "." + id.name(), null);
            if (ft != null) return ft;
            return Ir.Type.ANY;
        }
        if (e instanceof Ast.BinaryExpr b) {
            return binType(b.op(), typeOfExpr(b.left(), ctx), typeOfExpr(b.right(), ctx));
        }
        if (e instanceof Ast.UnaryExpr u) return typeOfExpr(u.operand(), ctx);
        if (e instanceof Ast.CallExpr c) {
            if (c.callee() instanceof Ast.IdentExpr id) {
                if (funRet.containsKey(id.name())) return funRet.get(id.name());
                return Ir.Type.VOID;
            }
            return Ir.Type.ANY;
        }
        if (e instanceof Ast.FieldExpr fe) {
            if (fe.receiver() instanceof Ast.IdentExpr re && typeOf.containsKey(re.name())
                    && ctx.tryIndex(re.name()) == null) {
                return new Ir.Type(re.name());
            }
            Ir.Type recv = typeOfExpr(fe.receiver(), ctx);
            return typeOf.getOrDefault(recv.name() + "." + fe.name(), Ir.Type.ANY);
        }
        if (e instanceof Ast.AssignExpr a) return Ir.Type.VOID; // store não deixa valor
        if (e instanceof Ast.IfExpr ie) return typeOfExpr(ie.thenExpr(), ctx);
        if (e instanceof Ast.MatchExpr) return Ir.Type.ANY;
        if (e instanceof Ast.LambdaExpr) return new Ir.Type("Function");
        return Ir.Type.ANY;
    }

    private Ir.Type typeOf(String name) {
        return typeOf.getOrDefault(name, Ir.Type.ANY);
    }

    private static Ir.Type binType(String op, Ir.Type l, Ir.Type r) {
        return switch (op) {
            case "+", "-", "*", "/", "%", "&", "|", "^", "<<", ">>" -> {
                if (Ir.Type.STRING.equals(l) || Ir.Type.STRING.equals(r)) yield Ir.Type.STRING;
                if (Ir.Type.FLOAT.equals(l) || Ir.Type.FLOAT.equals(r)) yield Ir.Type.FLOAT;
                yield Ir.Type.INT;
            }
            case "==", "!=", "<", ">", "<=", ">=", "&&", "||" -> Ir.Type.BOOL;
            default -> Ir.Type.ANY;
        };
    }

    /** Tipo dos operandos para a op (para o backend JVM emitir os opcodes certos). */
    private static Ir.Type operandType(String op, Ir.Type l, Ir.Type r) {
        if ("&&".equals(op) || "||".equals(op)) return Ir.Type.BOOL;
        if (Ir.Type.STRING.equals(l) || Ir.Type.STRING.equals(r)) return Ir.Type.STRING;
        if (Ir.Type.FLOAT.equals(l) || Ir.Type.FLOAT.equals(r)) return Ir.Type.FLOAT;
        if (Ir.Type.BOOL.equals(l) && Ir.Type.BOOL.equals(r)) return Ir.Type.BOOL;
        return Ir.Type.INT;
    }

    private static Ir.Type irType(Ast.TypeRef t) {
        if (t == null) return Ir.Type.ANY;
        return switch (t.name()) {
            case "Int" -> Ir.Type.INT;
            case "Float" -> Ir.Type.FLOAT;
            case "String" -> Ir.Type.STRING;
            case "Bool" -> Ir.Type.BOOL;
            default -> new Ir.Type(t.name());
        };
    }

    private static Ir.Type retType(Ast.TypeRef t) {
        return t == null ? Ir.Type.VOID : irType(t);
    }

    private static List<Ir.Type> paramTypes(List<Ast.Param> params) {
        List<Ir.Type> out = new ArrayList<>();
        for (Ast.Param p : params) out.add(irType(p.type()));
        return out;
    }

    private static Object defaultFor(Ir.Type t) {
        if (Ir.Type.INT.equals(t)) return 0L;
        if (Ir.Type.FLOAT.equals(t)) return 0.0;
        if (Ir.Type.STRING.equals(t)) return "";
        if (Ir.Type.BOOL.equals(t)) return false;
        return null;
    }

    private static final class MethodCtx {
        final Ir.Type returnType;
        final List<Ir.Type> paramTypes;
        final List<Ir.Local> locals = new ArrayList<>();
        final List<Ir.Block> blocks = new ArrayList<>();
        private final Map<String, Integer> indexByName = new HashMap<>();
        private final Map<String, Ir.Type> typeByName = new HashMap<>();
        private int currentBlock;
        private int nextBlockIdx;
        private int tempSeq;

        MethodCtx(Ir.Type returnType, List<Ir.Type> paramTypes) {
            this.returnType = returnType;
            this.paramTypes = paramTypes;
            blocks.add(new Ir.Block(0, new ArrayList<>()));
            currentBlock = 0;
            nextBlockIdx = 1;
        }

        int declare(String name, Ir.Type t) {
            int idx = locals.size();
            locals.add(new Ir.Local(idx, name, t));
            indexByName.put(name, idx);
            typeByName.put(name, t);
            return idx;
        }

        int declareTemp(Ir.Type t) {
            return declare("$t" + (tempSeq++), t);
        }

        Integer tryIndex(String name) {
            return indexByName.get(name);
        }

        int indexOf(String name) {
            Integer i = indexByName.get(name);
            if (i == null) throw new IllegalStateException("IR: local não declarado: " + name);
            return i;
        }

        Ir.Type typeOf(String name) {
            return typeByName.getOrDefault(name, Ir.Type.ANY);
        }

        Ir.Type tryType(String name) {
            return typeByName.get(name);
        }

        @SuppressWarnings("unchecked")
        void emit(Ir.Op op) {
            Ir.Block b = blocks.get(currentBlock);
            if (b.index() != currentBlock) {
                throw new IllegalStateException("IR: bloco atual inconsistente: list="
                        + b.index() + " current=" + currentBlock);
            }
            List<Ir.Op> ops = (List<Ir.Op>) b.ops();
            ops.add(op);
        }

        /** Reserva o próximo índice de bloco e cria placeholder na lista. */
        int reserveBlock() {
            int idx = nextBlockIdx++;
            blocks.add(new Ir.Block(idx, new ArrayList<>()));
            return idx;
        }

        void openBlock(int idx) {
            if (idx >= blocks.size()) {
                blocks.add(new Ir.Block(idx, new ArrayList<>()));
            } else if (blocks.get(idx).index() != idx) {
                // lista com buracos: reconstrói na posição certa
                while (blocks.size() <= idx) {
                    blocks.add(new Ir.Block(blocks.size(), new ArrayList<>()));
                }
                blocks.set(idx, new Ir.Block(idx, new ArrayList<>()));
            }
            currentBlock = idx;
        }

        void ensureTerminated() {
            List<Ir.Op> ops = blocks.get(currentBlock).ops();
            if (ops.isEmpty() || !(last(ops) instanceof Ir.Return || last(ops) instanceof Ir.Jump)) {
                ops.add(new Ir.Return(returnType));
            }
        }

        private static Ir.Op last(List<Ir.Op> ops) {
            return ops.get(ops.size() - 1);
        }
    }
}
