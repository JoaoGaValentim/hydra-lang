# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 14 — F6-01b fmt canônico)
**Fase atual:** 6 (CLI + fmt); new/test = F6-01b restante; F5 stdlib em aberto
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fases 2–4 fechadas no subset; F6-01 + F6-01b (fmt) fechados.
- `compiler/`: **176/176 testes verdes**.
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer/parser/diagnósticos HYP001..013; paridade AST com Kof.
- IR + backends JVM e JS; paridade de alvos no subset.
- **Migrator** (F4): `.kf` → `.hy` + corpus Kof.
- **CLI** (F6): `hydra check|run|migrate|fmt|version` (`bin/hydra`).
- **Formatter** (F6-01b): reprint canônico + `//` por linha (D-HYD-026).

## Pipeline atual

```
.kf → Kof Parser → Migrator → .hy
.hy → Lexer → Parser → Ast.Unit → Ir.Module
  ├─ JvmBackend → Main.class  (hydra run)
  └─ JsBackend  → main.js     (hydra run --js)
.hy → Formatter → canônico    (hydra fmt)
CLI: hydra check | run | migrate | fmt
```

## Limites v1 (honestos)

- Sem objetos/`type` em runtime; enum vira string; lambda: IR rejeita (v2).
- try/catch JS na v2; Native: **BLQ-02** (macOS).
- Migrator: finally/do-while/break/implements/generics/arrays → MIG0xx.
- CLI: `new`/`test` ainda deferred; migração não preserva comentários Kof.

## Próxima ação

1. **F6-01b restante**: `hydra new` + `hydra test`.
2. **Fase 5**: stdlib com orçamento.
3. Backend JVM v2 quando a stdlib pedir `type`/enum em runtime.

## Comandos

```bash
cd compiler && mvn -B test
(cd compiler && mvn -B -DskipTests package) && bin/hydra check hydra/exemplos/01-hello.hy
bin/hydra fmt hydra/exemplos/01-hello.hy
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec.
- Ciclo 4: Fase 1 congelada + lexer.
- Ciclo 5–8: parser, enum, paridade, diagnósticos (Fase 2 fechada).
- Ciclo 9–10: IR + JVM E2E.
- Ciclo 11: JS backend + paridade de alvos (Fase 3 fechada).
- Ciclo 12: **F4-01/F4-02 migrate + corpus** (Fase 4 fechada no subset).
- Ciclo 13: **F6-01 CLI check/run/migrate**.
- Ciclo 14: **F6-01b fmt canônico + preservação de //.**
