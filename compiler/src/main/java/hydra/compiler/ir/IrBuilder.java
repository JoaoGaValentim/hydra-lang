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
    private final java.util.Set<String> enumNames = new java.util.HashSet<>();
    private String currentTypeOwner;

    public Ir.Module build(Ast.Unit unit, String moduleName) {
        // pass 0: nomes de enum (viram String no runtime — D-HYD-028)
        for (Ast.Decl d : unit.decls()) {
            if (d instanceof Ast.EnumDecl e) {
                enumNames.add(e.name());
            }
        }
        // pass 1: tipos conhecidos
        for (Ast.Decl d : unit.decls()) {
            if (d instanceof Ast.TypeDecl t) {
                for (Ast.Field fl : t.fields()) {
                    typeOf.put(t.name() + "." + fl.name(), irTypeDecl(fl.type()));
                }
                typeOf.put(t.name(), new Ir.Type(t.name()));
            } else if (d instanceof Ast.EnumDecl e) {
                typeOf.put(e.name(), Ir.Type.STRING);
            }
        }
        // pass 2: assinaturas (já sabem quais nomes são enum)
        for (Ast.Decl d : unit.decls()) {
            if (d instanceof Ast.FunDecl f) {
                funParams.put(f.name(), paramTypesDecl(f.params()));
                if (f.returnType() != null) {
                    funRet.put(f.name(), retTypeDecl(f.returnType()));
                }
            }
        }
        // pass 3: retorno de funções sem anotação, inferido da expressão/corpo
        // (`tripla(x: Int) = x * 3`); fixpoint curto para encadear chamadas
        for (int round = 0; round < 4; round++) {
            boolean changed = false;
            for (Ast.Decl d : unit.decls()) {
                if (d instanceof Ast.FunDecl f && f.returnType() == null) {
                    Ir.Type inferred = inferRetType(f);
                    if (!inferred.equals(funRet.get(f.name()))) {
                        funRet.put(f.name(), inferred);
                        changed = true;
                    }
                }
            }
            if (!changed) break;
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

        classes.add(0, new Ir.Class("Main", "Object", topFields, topMethods, List.of()));
        return new Ir.Module(moduleName, List.copyOf(classes), imports, null);
    }

    private Ir.Method lowerFun(Ast.FunDecl f) {
        Ir.Type ret = f.returnType() != null
                ? retTypeDecl(f.returnType())
                : funRet.getOrDefault(f.name(), Ir.Type.VOID);
        MethodCtx ctx = new MethodCtx(ret, paramTypesDecl(f.params()));
        for (Ast.Param p : f.params()) {
            ctx.declare(p.name(), irTypeDecl(p.type()));
        }
        String prevOwner = currentTypeOwner;
        lowerBlock(tailReturn(f.body(), ctx), ctx);
        currentTypeOwner = prevOwner;
        ctx.ensureTerminated();
        return new Ir.Method(
                f.name(),
                ctx.returnType,
                ctx.paramTypes,
                List.copyOf(ctx.locals),
                List.copyOf(ctx.blocks));
    }

    /**
     * Retorno de cauda: numa função não-void, a última expressão do corpo é o
     * resultado (mesma regra em toda função — exemplos canônicos 06/09/10/35).
     * `return` continua para saída antecipada; não há uma terceira forma.
     */
    /**
     * Tipo de retorno de função sem anotação: join dos `return` do corpo.
     * Aproximação estática do IR (sem checker): Int+Float→Float, qualquer
     * conflito com Void/Any→Any; sem return→Void.
     */
    private Ir.Type inferRetType(Ast.FunDecl f) {
        if (f.body() == null) return Ir.Type.VOID;
        MethodCtx probe = new MethodCtx(Ir.Type.VOID, paramTypesDecl(f.params()));
        for (Ast.Param p : f.params()) {
            probe.declare(p.name(), irTypeDecl(p.type()));
        }
        List<Ir.Type> found = new ArrayList<>();
        collectReturns(f.body().stmts(), probe, found, 0);
        Ir.Type joined = null;
        for (Ir.Type t : found) {
            if (joined == null) {
                joined = t;
            } else if (!joined.equals(t)) {
                if (Ir.Type.INT.equals(joined) && Ir.Type.FLOAT.equals(t)
                        || Ir.Type.FLOAT.equals(joined) && Ir.Type.INT.equals(t)) {
                    joined = Ir.Type.FLOAT;
                } else {
                    return Ir.Type.ANY;
                }
            }
        }
        return joined == null ? Ir.Type.VOID : joined;
    }

    private void collectReturns(List<Ast.Stmt> stmts, MethodCtx probe, List<Ir.Type> out, int depth) {
        if (depth > 16) return;
        for (int stmtIdx = 0; stmtIdx < stmts.size(); stmtIdx++) {
            Ast.Stmt s = stmts.get(stmtIdx);
            boolean tail = stmtIdx == stmts.size() - 1;
            if (s instanceof Ast.ReturnStmt r) {
                out.add(r.value() != null ? typeOfExpr(r.value(), probe) : Ir.Type.VOID);
            } else if (s instanceof Ast.VarDecl v) {
                Ir.Type t = v.type() != null
                        ? irTypeDecl(v.type())
                        : v.init() != null ? typeOfExpr(v.init(), probe) : Ir.Type.ANY;
                if (probe.tryIndex(v.name()) == null) probe.declare(v.name(), t);
            } else if (s instanceof Ast.Block b) {
                collectReturns(b.stmts(), probe, out, depth + 1);
            } else if (s instanceof Ast.IfStmt ifs) {
                collectReturns(ifs.thenBlock().stmts(), probe, out, depth + 1);
                if (ifs.elseBlock() != null) {
                    collectReturns(ifs.elseBlock().stmts(), probe, out, depth + 1);
                } else if (tail) {
                    out.add(Ir.Type.VOID);
                }
            } else if (s instanceof Ast.ForStmt fo) {
                collectReturns(fo.body().stmts(), probe, out, depth + 1);
            } else if (s instanceof Ast.TryStmt t) {
                collectReturns(t.body().stmts(), probe, out, depth + 1);
                for (Ast.CatchClause c : t.catches()) {
                    if (probe.tryIndex(c.name()) == null) probe.declare(c.name(), Ir.Type.STRING);
                    collectReturns(c.body().stmts(), probe, out, depth + 1);
                }
            } else if (s instanceof Ast.MatchStmt m) {
                for (Ast.CaseArm arm : m.cases()) {
                    if (arm.result() != null) out.add(typeOfExpr(arm.result(), probe));
                }
            } else if (s instanceof Ast.ExprStmt es && tail) {
                out.add(typeOfExpr(es.expr(), probe));
            }
        }
    }

    private Ast.Block tailReturn(Ast.Block body, MethodCtx ctx) {
        if (ctx.returnType.isVoid() || body.stmts().isEmpty()) return body;
        Ast.Stmt last = body.stmts().get(body.stmts().size() - 1);
        if (!(last instanceof Ast.ExprStmt es) || typeOfExpr(es.expr(), ctx).isVoid()) {
            return body;
        }
        List<Ast.Stmt> stmts = new ArrayList<>(body.stmts());
        stmts.set(stmts.size() - 1, new Ast.ReturnStmt(es.expr(), es.pos()));
        return new Ast.Block(stmts, body.pos());
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
                    ? irTypeDecl(v.type())
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
            AssertCall asrt = assertCall(e.expr());
            if (asrt != null) {
                lowerExpr(asrt.cond(), ctx);
                ctx.emit(new Ir.Assert(asrt.message()));
            } else {
                lowerExpr(e.expr(), ctx);
                Ir.Type et = typeOfExpr(e.expr(), ctx);
                if (!et.isVoid()) ctx.emit(new Ir.Pop(et));
            }
        } else if (s instanceof Ast.ThrowStmt t) {
            Ir.Type vt = typeOfExpr(t.value(), ctx);
            lowerExpr(t.value(), ctx);
            if (!Ir.Type.STRING.equals(vt) && !Ir.Type.ANY.equals(vt)) {
                ctx.emit(new Ir.ToString(vt));
            }
            ctx.emit(new Ir.Throw());
        } else if (s instanceof Ast.TryStmt t) {
            lowerTry(t, ctx);
        } else if (s instanceof Ast.ForStmt f) {
            lowerFor(f, ctx);
        } else if (s instanceof Ast.SpawnStmt sp) {
            lowerExpr(sp.value(), ctx);
            ctx.emit(new Ir.Pop(typeOfExpr(sp.value(), ctx)));
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
        ctx.emit(new Ir.Jump(thenIdx));

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
                Ir.Type ut = typeOfExpr(ch.update(), ctx);
                if (!ut.isVoid()) ctx.emit(new Ir.Pop(ut));
            }
            ctx.emit(new Ir.Jump(loopIdx));

            ctx.openBlock(endIdx);
        } else if (head instanceof Ast.ForInHead in) {
            Ir.Type iter = typeOfExpr(in.iter(), ctx);
            if (!iter.isCollection() && !Ir.Type.ANY.equals(iter) && !Ir.Type.STRING.equals(iter)) {
                throw new IllegalStateException("IR: for-in espera List/Set/Map/String, achou "
                        + iter.name());
            }
            Ir.Type elem = iter.name().startsWith("Map<") ? iter.keyType() : iter.elementType();
            lowerExpr(in.iter(), ctx);
            int iterLocal = ctx.declareTemp(Ir.Type.ANY);
            ctx.emit(new Ir.IterInit(iterLocal, iter));
            int valueLocal = ctx.declare(in.name(), elem);
            int loopIdx = ctx.reserveBlock();
            int endIdx = ctx.reserveBlock();
            ctx.emit(new Ir.Jump(loopIdx));
            ctx.openBlock(loopIdx);
            ctx.emit(new Ir.IterNext(iterLocal, valueLocal, elem, endIdx));
            lowerBlock(f.body(), ctx);
            ctx.emit(new Ir.Jump(loopIdx));
            ctx.openBlock(endIdx);
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
        // sujeito avaliado UMA vez num temp; testes em blocos dedicados
        // (o layout do IR não garante fall-through físico — D-HYD-027)
        Ir.Type subjType = typeOfExpr(m.subject(), ctx);
        int subjLocal = ctx.declareTemp(subjType);
        lowerExpr(m.subject(), ctx);
        ctx.emit(new Ir.StoreLocal(subjLocal, subjType));

        // default do resultado: todo caminho que chega ao fim do match tem um
        // valor bem formado para o verificador (enum exaustivo sem default).
        if (resultLocal != null) {
            Ir.Type rt = ctx.localType(resultLocal);
            ctx.emit(new Ir.LoadLiteral(rt, defaultFor(rt)));
            ctx.emit(new Ir.StoreLocal(resultLocal, rt));
        }

        int n = m.cases().size();
        int endIdx = ctx.reserveBlock();
        int[] testIdx = new int[n];
        int[] armIdx = new int[n];
        for (int i = 0; i < n; i++) {
            testIdx[i] = ctx.reserveBlock();
            armIdx[i] = ctx.reserveBlock();
        }

        ctx.emit(new Ir.Jump(testIdx[0]));

        for (int i = 0; i < n; i++) {
            Ast.CaseArm arm = m.cases().get(i);
            ctx.openBlock(testIdx[i]);
            if (isDefaultArm(arm)) {
                ctx.emit(new Ir.Jump(armIdx[i]));
            } else {
                lowerCaseTest(arm, subjLocal, subjType, ctx);
                int next = (i + 1 < n) ? testIdx[i + 1] : endIdx;
                ctx.emit(new Ir.JumpIfFalse(next));
                ctx.emit(new Ir.Jump(armIdx[i]));
            }
        }

        for (int i = 0; i < n; i++) {
            Ast.CaseArm arm = m.cases().get(i);
            ctx.openBlock(armIdx[i]);
            bind(arm, subjLocal, subjType, ctx);
            if (arm.guard() != null) {
                lowerExpr(arm.guard(), ctx);
                int next = (i + 1 < n) ? testIdx[i + 1] : endIdx;
                ctx.emit(new Ir.JumpIfFalse(next));
            }
            emitArmBody(arm, resultLocal, ctx);
            ctx.emit(new Ir.Jump(endIdx));
        }

        ctx.openBlock(endIdx);
    }

    /** Liga o(s) nome(s) do padrão ao sujeito; destructuring lê campos. */
    private void bind(Ast.CaseArm arm, int subjLocal, Ir.Type subjType, MethodCtx ctx) {
        if (isDefaultArm(arm) || arm.patternName() == null || arm.patternName().isEmpty()) return;
        String[] names = arm.patternName().split(" ");
        if (names.length == 1) {
            int idx = ctx.declare(names[0], subjType);
            ctx.emit(new Ir.LoadLocal(subjLocal, subjType));
            ctx.emit(new Ir.StoreLocal(idx, subjType));
            return;
        }
        // destructuring: cada nome vem do campo homônimo (v1: LoadField)
        for (String name : names) {
            if (name.isEmpty()) continue;
            Ir.Type ft = typeOf.getOrDefault(subjType.name() + "." + name, Ir.Type.ANY);
            ctx.emit(new Ir.LoadField(subjType.name(), name, ft));
            int idx = ctx.declare(name, ft);
            ctx.emit(new Ir.StoreLocal(idx, ft));
        }
    }

    private void emitArmBody(Ast.CaseArm arm, Integer resultLocal, MethodCtx ctx) {
        if (arm.result() == null) return;
        lowerExpr(arm.result(), ctx);
        if (resultLocal != null) {
            ctx.emit(new Ir.StoreLocal(resultLocal, ctx.localType(resultLocal)));
        } else {
            Ir.Type rt = typeOfExpr(arm.result(), ctx);
            if (!rt.isVoid()) ctx.emit(new Ir.Pop(rt));
        }
    }

    private static boolean isDefaultArm(Ast.CaseArm arm) {
        return arm.patternType() == null
                || arm.patternType().isEmpty()
                || "default".equals(arm.patternType());
    }

    /** Cada teste carrega o sujeito e deixa exatamente um Bool na pilha. */
    private void lowerCaseTest(Ast.CaseArm arm, int subjLocal, Ir.Type subjType, MethodCtx ctx) {
        String pt = arm.patternType();
        if (pt == null || pt.isEmpty() || "default".equals(pt)) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.BOOL, true));
            return;
        }
        ctx.emit(new Ir.LoadLocal(subjLocal, subjType));
        if (pt.contains(".")) {
            // enum: comparar a string "Tipo.Caso"
            int dot = pt.indexOf('.');
            ctx.emit(new Ir.LoadEnum(pt.substring(0, dot), pt.substring(dot + 1)));
            ctx.emit(new Ir.Binary("==", Ir.Type.STRING));
            return;
        }
        if (pt.startsWith("\"")) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.STRING, pt.substring(1, pt.length() - 1)));
            ctx.emit(new Ir.Binary("==", Ir.Type.STRING));
            return;
        }
        if ("true".equals(pt) || "false".equals(pt)) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.BOOL, Boolean.parseBoolean(pt)));
            ctx.emit(new Ir.Binary("==", Ir.Type.BOOL));
            return;
        }
        if (pt.matches("-?\\d+")) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.INT, Long.parseLong(pt)));
            ctx.emit(new Ir.Binary("==", Ir.Type.INT));
            return;
        }
        if ("null".equals(pt)) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.ANY, null));
            ctx.emit(new Ir.Binary("==", Ir.Type.ANY));
            return;
        }
        // teste de tipo: `case String s` — null não é String (igual a `!= null`)
        if (Ir.Type.STRING.equals(subjType) || Ir.Type.ANY.equals(subjType)) {
            ctx.emit(new Ir.LoadLiteral(Ir.Type.ANY, null));
            ctx.emit(new Ir.Binary("!=", Ir.Type.ANY));
            return;
        }
        if (pt.equals(subjType.name())) {
            // mesmo tipo estático: sempre casa
            ctx.emit(new Ir.Pop(subjType));
            ctx.emit(new Ir.LoadLiteral(Ir.Type.BOOL, true));
            return;
        }
        throw new IllegalStateException("IR: case de tipo não suportado na v1: " + pt
                + " (sujeito " + subjType.name() + ")");
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
            Ir.Type lt = typeOfExpr(b.left(), ctx);
            Ir.Type rt = typeOfExpr(b.right(), ctx);
            if ("+".equals(b.op()) && (Ir.Type.STRING.equals(lt) || Ir.Type.STRING.equals(rt))) {
                // concat: qualquer operando vira String (uma forma só — D-HYD-013)
                lowerExpr(b.left(), ctx);
                if (!Ir.Type.STRING.equals(lt)) ctx.emit(new Ir.ToString(lt));
                lowerExpr(b.right(), ctx);
                if (!Ir.Type.STRING.equals(rt)) ctx.emit(new Ir.ToString(rt));
                ctx.emit(new Ir.Binary("+", Ir.Type.STRING));
            } else {
                lowerExpr(b.left(), ctx);
                lowerExpr(b.right(), ctx);
                // Binary.operandType = tipo dos operandos (não o resultado)
                ctx.emit(new Ir.Binary(b.op(), operandType(b.op(), lt, rt)));
            }
        } else if (e instanceof Ast.UnaryExpr u) {
            lowerExpr(u.operand(), ctx);
            ctx.emit(new Ir.Unary(u.op(), typeOfExpr(u.operand(), ctx)));
        } else if (e instanceof Ast.CallExpr c) {
            lowerCall(c, ctx);
        } else if (e instanceof Ast.IndexExpr ix) {
            Ir.Type target = typeOfExpr(ix.target(), ctx);
            if (target.name().startsWith("Set<")) {
                throw new IllegalStateException("IR: Set não é indexável na v1 — use contains()");
            }
            Ir.Type result = target.name().startsWith("Map<") ? target.valueType() : target.elementType();
            lowerExpr(ix.target(), ctx);
            lowerExpr(ix.index(), ctx);
            ctx.emit(new Ir.IndexGet(target, result));
        } else if (e instanceof Ast.PairExpr) {
            throw new IllegalStateException("IR: `a to b` só é válido dentro de mapOf");
        } else if (e instanceof Ast.FieldExpr fe) {
            // Color.Red — enum constante sem receiver local
            if (fe.receiver() instanceof Ast.IdentExpr re && typeOf.containsKey(re.name())
                    && funParams.get(re.name()) == null && ctx.tryIndex(re.name()) == null) {
                ctx.emit(new Ir.LoadEnum(re.name(), fe.name()));
            } else if ("length".equals(fe.name())) {
                lowerExpr(fe.receiver(), ctx);
                ctx.emit(new Ir.Length(typeOfExpr(fe.receiver(), ctx)));
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
            Ir.Type t = matchType(me, ctx);
            int temp = ctx.declareTemp(t);
            lowerMatch(me.stmt(), ctx, temp);
            ctx.emit(new Ir.LoadLocal(temp, t));
        } else if (e instanceof Ast.LambdaExpr) {
            throw new IllegalStateException("IR: lambda na v2 (F3-02)");
        } else {
            throw new IllegalStateException("IR: expr não suportada: " + e.getClass().getSimpleName());
        }
    }

    /** Extrai um assert(cond, "msg") top-level de uma expressão de statement. */
    private AssertCall assertCall(Ast.Expr e) {
        if (e instanceof Ast.CallExpr c && c.callee() instanceof Ast.IdentExpr id && "assert".equals(id.name())) {
            if (c.args().size() == 1) {
                return new AssertCall(c.args().get(0), "assertion failed");
            }
            if (c.args().size() == 2 && c.args().get(1) instanceof Ast.StringLit m) {
                return new AssertCall(c.args().get(0), m.value());
            }
            throw new IllegalStateException(
                    "IR: assert espera (cond) ou (cond, \"mensagem\") — achou " + c.args().size() + " argumentos");
        }
        return null;
    }

    /** assert(cond, msg) já validado — built-in de teste (não vira Call). */
    private record AssertCall(Ast.Expr cond, String message) {}

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
            if (!funParams.containsKey(name) && "mapOf".equals(name)) {
                List<Ir.Type> keys = new ArrayList<>();
                List<Ir.Type> vals = new ArrayList<>();
                for (Ast.Expr a : c.args()) {
                    if (!(a instanceof Ast.PairExpr p)) {
                        throw new IllegalStateException("IR: mapOf espera pares `chave to valor`");
                    }
                    lowerExpr(p.left(), ctx);
                    lowerExpr(p.right(), ctx);
                    keys.add(typeOfExpr(p.left(), ctx));
                    vals.add(typeOfExpr(p.right(), ctx));
                }
                ctx.emit(new Ir.NewMap(keys, vals));
                return;
            }
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
            } else if ("listOf".equals(name)) {
                ctx.emit(new Ir.NewList(argTs));
            } else if ("setOf".equals(name)) {
                ctx.emit(new Ir.NewSet(argTs));
            } else if ("readLine".equals(name)) {
                if (!c.args().isEmpty()) {
                    throw new IllegalStateException("IR: readLine não recebe argumentos");
                }
                ctx.emit(new Ir.ReadLine());
            } else {
                ctx.emit(new Ir.Call(name, argTs, Ir.Type.ANY, false));
            }
        } else if (c.callee() instanceof Ast.FieldExpr fe) {
            if ("contains".equals(fe.name()) && c.args().size() == 1) {
                Ir.Type recv = typeOfExpr(fe.receiver(), ctx);
                if (recv.isCollection() || Ir.Type.STRING.equals(recv)) {
                    lowerExpr(fe.receiver(), ctx);
                    Ir.Type at = typeOfExpr(c.args().get(0), ctx);
                    lowerExpr(c.args().get(0), ctx);
                    ctx.emit(new Ir.Contains(recv, at));
                    return;
                }
            }
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
            if (c.callee() instanceof Ast.FieldExpr fe && "contains".equals(fe.name())) {
                return Ir.Type.BOOL;
            }
            if (c.callee() instanceof Ast.IdentExpr id) {
                if (funRet.containsKey(id.name())) return funRet.get(id.name());
                switch (id.name()) {
                    case "listOf" -> {
                        return new Ir.Type("List<" + homogeneousType(argTypes(c.args(), ctx)).name() + ">");
                    }
                    case "setOf" -> {
                        return new Ir.Type("Set<" + homogeneousType(argTypes(c.args(), ctx)).name() + ">");
                    }
                    case "mapOf" -> {
                        List<Ir.Type> ks = new ArrayList<>();
                        List<Ir.Type> vs = new ArrayList<>();
                        for (Ast.Expr a : c.args()) {
                            if (a instanceof Ast.PairExpr p) {
                                ks.add(typeOfExpr(p.left(), ctx));
                                vs.add(typeOfExpr(p.right(), ctx));
                            }
                        }
                        return new Ir.Type("Map<" + homogeneousType(ks).name()
                                + "," + homogeneousType(vs).name() + ">");
                    }
                    case "readLine" -> {
                        return Ir.Type.STRING;
                    }
                    default -> {
                        return Ir.Type.VOID;
                    }
                }
            }
            return Ir.Type.ANY;
        }
        if (e instanceof Ast.IndexExpr ix) {
            Ir.Type target = typeOfExpr(ix.target(), ctx);
            return target.name().startsWith("Map<") ? target.valueType() : target.elementType();
        }
        if (e instanceof Ast.PairExpr p) {
            return Ir.Type.ANY;
        }
        if (e instanceof Ast.FieldExpr fe2 && "length".equals(fe2.name())) {
            return Ir.Type.INT;
        }
        if (e instanceof Ast.FieldExpr fe) {
            if (fe.receiver() instanceof Ast.IdentExpr re && typeOf.containsKey(re.name())
                    && ctx.tryIndex(re.name()) == null) {
                return typeOf.get(re.name());
            }
            Ir.Type recv = typeOfExpr(fe.receiver(), ctx);
            return typeOf.getOrDefault(recv.name() + "." + fe.name(), Ir.Type.ANY);
        }
        if (e instanceof Ast.AssignExpr a) return Ir.Type.VOID; // store não deixa valor
        if (e instanceof Ast.IfExpr ie) return typeOfExpr(ie.thenExpr(), ctx);
        if (e instanceof Ast.MatchExpr me) return matchType(me, ctx);
        if (e instanceof Ast.LambdaExpr) return new Ir.Type("Function");
        return Ir.Type.ANY;
    }

    /** Tipo do match = tipo do primeiro braço; chamado antes do lowering. */
    private Ir.Type matchType(Ast.MatchExpr me, MethodCtx ctx) {
        for (Ast.CaseArm arm : me.stmt().cases()) {
            if (arm.result() != null) return typeOfExpr(arm.result(), ctx);
        }
        return Ir.Type.ANY;
    }

    /** Tipo comum dos argumentos (List<Int>, List<String>…); conflito → Any. */
    private Ir.Type homogeneousType(List<Ir.Type> ts) {
        if (ts.isEmpty()) return Ir.Type.ANY;
        Ir.Type first = ts.get(0);
        for (Ir.Type t : ts) {
            if (!t.equals(first)) return Ir.Type.ANY;
        }
        return first;
    }

    private List<Ir.Type> argTypes(List<Ast.Expr> args, MethodCtx ctx) {
        List<Ir.Type> out = new ArrayList<>();
        for (Ast.Expr a : args) out.add(typeOfExpr(a, ctx));
        return out;
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
        // == / != com null (ANY) compara referência, não conteúdo
        if (("==".equals(op) || "!=".equals(op))
                && (Ir.Type.ANY.equals(l) || Ir.Type.ANY.equals(r))) return Ir.Type.ANY;
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

    /** Tipo de parâmetro/retorno com enums já rebaixados para String. */
    private Ir.Type irTypeDecl(Ast.TypeRef t) {
        if (t == null) return Ir.Type.ANY;
        if (enumNames.contains(t.name())) return Ir.Type.STRING;
        return irType(t);
    }

    private static Ir.Type retType(Ast.TypeRef t) {
        return t == null ? Ir.Type.VOID : irType(t);
    }

    private Ir.Type retTypeDecl(Ast.TypeRef t) {
        return t == null ? Ir.Type.VOID : irTypeDecl(t);
    }

    private List<Ir.Type> paramTypesDecl(List<Ast.Param> params) {
        List<Ir.Type> out = new ArrayList<>();
        for (Ast.Param p : params) out.add(irTypeDecl(p.type()));
        return out;
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

        /** Tipo declarado de um local por índice (para stores tipados). */
        Ir.Type localType(int index) {
            for (Ir.Local l : locals) {
                if (l.index() == index) return l.type();
            }
            return Ir.Type.ANY;
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
