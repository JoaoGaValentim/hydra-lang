# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 12 — F4-01/F4-02 migração)
**Fase atual:** 4 fechada no subset (migrate + corpus); próximo = F5 stdlib ou F6 ferramentas
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fases 2–4 fechadas no subset honesto (F3: IR+JVM+JS+paridade; F4: migrate).
- `compiler/`: **104/104 testes verdes** (inclui MigrateTest 15 + MigrateCorpusTest 5).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer/parser/diagnósticos HYP001..013; paridade AST com Kof.
- IR + backends JVM e JS; paridade de alvos no subset.
- **`Migrator` (F4-01)**: `.kf` → `.hy` com gate de parse e diagnósticos MIG0xx.
- **Corpus (F4-02)**: `training/examples` + `tests/golden/*` migram com saída parseável ou diagnóstico honesto.

## Pipeline atual

```
.kf → Kof Parser → Migrator → .hy
.hy → Lexer → Parser → Ast.Unit → Ir.Module
  ├─ JvmBackend → Main.class
  └─ JsBackend  → main.js
```

## Limites v1 (honestos)

- Sem objetos/`type` em runtime (LoadField/NewObject → erro).
- Enum vira string; lambda: IR rejeita (v2).
- try/catch JS na v2; Native: **BLQ-02** (macOS).
- Migrator: finally/do-while/break/implements/generics/arrays → MIG0xx (parcial com diagnóstico).
- Comentários não são preservados na migração.

## Próxima ação

1. **Fase 5**: stdlib com orçamento (uma API por conceito; gap codes por alvo).
2. **Fase 6**: `hydra fmt`/`run`/`migrate` CLI (`F6-01`).
3. Backend JVM v2 (`type`/enum runtime, lambda) quando F5/F6 pedirem.

## Comandos

```bash
cd compiler && mvn -B test
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec.
- Ciclo 4: Fase 1 congelada + lexer.
- Ciclo 5–8: parser, enum, paridade, diagnósticos (Fase 2 fechada).
- Ciclo 9–10: IR + JVM E2E.
- Ciclo 11: JS backend + paridade de alvos (Fase 3 fechada).
- Ciclo 12: **F4-01/F4-02 migrate + corpus** (Fase 4 fechada no subset).
