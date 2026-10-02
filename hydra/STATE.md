# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 16 — match/enum/paridade corrigidos)
**Fase atual:** 6 fechada; F5 stdlib é a próxima
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoValentim/hydra-lang` (ver remoto: `JoaoGaValentim/hydra-lang`).
- Fases 2–4 fechadas no subset; F6 completa (check/run/migrate/fmt/new/test).
- `compiler/`: **205/205 testes verdes**.
- Paridade de corpus: **36/39** exemplos com saída idêntica JVM≡JS; os 3 restantes
  são stdlib v1 (`listOf`/`setOf`/`readLine`) com erro honesto, não silêncio.
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer/parser/diagnósticos HYP001..013; paridade AST com Kof.
- IR + backends JVM e JS; paridade de alvos no subset (try/catch, assert,
  throw String, concat universal, match com literais/guardas/bindings,
  enum=String, retorno de cauda inferido).
- **Migrator** (F4): `.kf` → `.hy` + corpus Kof.
- **CLI** (F6): `hydra check|run|test|new|migrate|fmt|version` (`bin/hydra`).
- **Formatter** (F6-01b): reprint canônico + `//` por linha (D-HYD-026).
- **Test runner** (F6-01b): `test "nome" { assert(…) }` → PASS/FAIL, exit != 0,
  JVM e JS; `assert` é op de IR (D-HYD-027).

## Pipeline atual

```
.kf → Kof Parser → Migrator → .hy
.hy → Lexer → Parser → Ast.Unit → Ir.Module
  ├─ JvmBackend → Main.class  (hydra run)
  └─ JsBackend  → main.js     (hydra run --js)
.hy → Formatter → canônico    (hydra fmt)
test "…" → TestRunner (harness em AST) → JvmBackend/JsBackend → PASS/FAIL
CLI: hydra check | run | test | new | migrate | fmt
```

## Limites v1 (honestos)

- Campos/objetos (`LoadField`), lambda e `application` main: erro honesto (v2).
- Stdlib de coleções (`listOf`/`setOf`/`readLine`): Fase 5 — hoje o backend
  emite a chamada e falha em runtime; o IR ainda não bloqueia.
- Native: **BLQ-02** (macOS).
- Migrator: finally/do-while/break/implements/generics/arrays → MIG0xx.
- CLI: migração não preserva comentários Kof.

## Próxima ação

1. **Fase 5**: stdlib com orçamento — `listOf`/`setOf`/`mapOf`/`readLine` no IR
   (ou rejeição honesta no IR antes do backend; decidir na fase).
2. Backend JVM v2: `type`/campos (exemplos 04/05/10/30) e lambda.
3. `hydra build` (hydra.toml multi-arquivo) quando a stdlib pedir.

## Comandos

```bash
cd compiler && mvn -B test
(cd compiler && mvn -B -DskipTests package) && bin/hydra check hydra/exemplos/01-hello.hy
bin/hydra new /tmp/app && bin/hydra test /tmp/app/tests
bin/hydra test hydra/exemplos/26-teste.hy
for f in hydra/exemplos/*.hy; do diff <(bin/hydra run $f) <(bin/hydra run $f --js); done
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
- Ciclo 15: **F6-01b new/test + assert no IR + paridade try/catch/bool (Fase 6 fechada).**
- Ciclo 16: **match/enum/POP tipados + retorno de cauda (D-HYD-028); corpus JVM≡JS 36/39.**
