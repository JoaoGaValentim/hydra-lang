# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 17 — Fase 5 stdlib de coleções + IO)
**Fase atual:** 5 (stdlib) em andamento; 6 fechada
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fases 2–4 fechadas no subset; F6 completa; F5 (coleções/IO) entregue.
- `compiler/`: **213/213 testes verdes**.
- Paridade de corpus: **40/40** exemplos com saída idêntica JVM≡JS.
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer/parser/diagnósticos HYP001..013; paridade AST com Kof.
- IR + backends JVM e JS; paridade total do corpus (try/catch, assert,
  throw String, concat, match completo, enum=String, retorno de cauda,
  coleções tipadas, indexação, for-in, readLine).
- **Migrator** (F4): `.kf` → `.hy` + corpus Kof.
- **CLI** (F6): `hydra check|run|test|new|migrate|fmt|version` (`bin/hydra`).
- **Formatter** (F6-01b): reprint canônico + `//` por linha (D-HYD-026).
- **Test runner** (F6-01b): `test "nome" { assert(…) }` → PASS/FAIL, exit != 0,
  JVM e JS (D-HYD-027).

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

- Campos/objetos (`LoadField`/`NewObject`), lambda e `application` main: erro
  honesto (v2) — exemplos 04/05/10/17/18/22/23/29/30/33/37/40.
- Concorrência `spawn`/`await`: IR rejeita (v2).
- Native: **BLQ-02** (macOS).
- Migrator: finally/do-while/break/implements/generics/arrays → MIG0xx.
- CLI: migração não preserva comentários Kof.

## Próxima ação

1. **Backend JVM/JS v2**: `type`/campos (04/05/10/30), lambda (17/18/29/33/40).
2. `hydra build` (hydra.toml multi-arquivo) + imports com resolução real.
3. F5 restante: `kof.io`/`kof.log` como módulos .hy (padrão da stdlib).

## Comandos

```bash
cd compiler && mvn -B test
(cd compiler && mvn -B -DskipTests package) && bin/hydra check hydra/exemplos/01-hello.hy
bin/hydra new /tmp/app && bin/hydra test /tmp/app/tests
echo Mel | bin/hydra run hydra/exemplos/25-imports.hy
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
- Ciclo 17: **Fase 5 stdlib de coleções + IO; corpus 40/40 JVM≡JS; 213/213 (D-HYD-029).**
