# BACKLOG — Hydra

Formato: `ID | status | tamanho | dependências | descrição`
Status: `todo` | `fazendo` | `bloqueado` | `feito`

---

## Fase 0 — Reconhecimento

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F0-01 | feito | M | — | Linha de baseline `mvn test` do Kof — medida no host (808/155F/8E/26S focado; native inexecutável no macOS). |
| F0-02 | feito | M | — | Auditoria: lexer/parser/AST/IR/backend + idiomas de treino. §17 quase fechado. |
| F0-03 | feito | S | F0-02 | Contagem keywords: **62** word-form em `TokenType.java` (+5 contextuais). |
| F0-04 | feito | S | F0-02 | Interpolação **ausente** (`Lexer.java:213`); `${}` é config runtime. |
| F0-05 | feito | S | — | `STATE.md` da Fase 0 atualizado. |
| F0-06 | feito | M | — | Esqueleto: LICENSE, NOTICE, README, .gitignore, ci.yml, push inicial. |

## Fase 1 — Especificação

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F1-01 | feito | L | F0 | `ESPECIFICACAO.md` **congelada** (EBNF ~71 linhas, keywords 20, tipos, semântica). |
| F1-02 | feito | M | F1-01 | `DECISOES.md` D-HYD-001..018 (contextuais, wildcard, `as`, `finally` fechados). |
| F1-03 | feito | L | F1-01 | **40** exemplos `.hy`; relitura fez correções (22/23/36). |
| F1-04 | feito | S | F1-03 | Checklist orçamento **aprovado** (STATE). Fase 1 congelada. |

## Fase 2 — Frontend

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F2-01 | feito | L | F1 | Lexer Hydra (`compiler/`) — 12 testes. |
| F2-02 | feito | L | F2-01 | Parser + AST — unit, fun, type, enum, stmts, exprs, lambda, match. 21 testes. |
| F2-03 | feito | M | F2-02 | Diagnósticos: `SyntaxError` com code (HYP00x) + sugestão + contexto de linha; `DiagnosticsTest` 13/13. |
| F2-04 | feito | L | F2-02 | Paridade AST Hydra ↔ Kof: `ParityTest` 9/9 + `hydra/PARITY.md`; CI instala Kof. |

## Fase 3 — IR e backends

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F3-01 | feito | L | F2 | IR + lowering: `hydra.compiler.ir.{Ir,IrBuilder}`; contrato em `hydra/IR.md`; `IrTest` 10/10. |
| F3-02 | feito | L | F3-01 | Backend JVM: `JvmBackend` + `Compiler`; E2E 7/7 (executa `.class` de verdade). |
| F3-03 | feito | M | F3-02 | Backend JS: `JsBackend` (switch(pc)); E2E Node 5/5 + emissão. Native: **BLQ-02** (macOS) — sem alvo nativo nesta estação. |
| F3-04 | feito | M | F3-02 | Paridade de alvos: `TargetParityTest` — mesma fonte → mesma saída JVM e JS. |

## Fase 4 — Migração

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F4-01 | todo | L | F3 | `hydra migrate` Kof→Hydra (`.kf` → `.hy`). |
| F4-02 | todo | M | F4-01 | Rodar sobre `examples/` e `training/examples/`; compilar; comparar saída. |

## Fase 5 — Stdlib

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F5-01 | todo | L | F3 | Revisar módulos com orçamento: uma API por conceito; gap codes por alvo. |

## Fase 6 — Ferramentas

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F6-01 | todo | L | F3 | `hydra fmt` idempotente, `check`, `test`, `run`, `new`. |
| F6-02 | todo | M | F6-01 | LSP + editor + REPL/script mode. |

## Fase 7 — Docs e corpus

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F7-01 | todo | L | F6 | `learn/` Hydra mais curto que o Kof; `training/` para LLMs. |

## Fase 8 — Desempenho e robustez

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F8-01 | todo | M | F6 | Benchmarks Kof vs Hydra; fuzz parser; `fmt(fmt(x))==fmt(x)`. |

## Infra / publicação

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| INF-01 | feito | S | F0-06 | CI verde no skeleton (run 36952608349: structure + upstream-compile). |
| INF-02 | todo | S | INF-01 | Badge de CI no README do Hydra. |
| INF-03 | feito | S | F0-01 | Workaround shade documentado (BLQ-01); não bloqueia Hydra. |

## Bloqueios registrados

| ID | O quê | O que foi tentado | Próximo quando destravar |
|---|---|---|---|
| BLQ-01 | Shade `kof-cli` + `${revision}` | install, flatten, sed m2, shade.skip | Editar pom local ou testar sem package; não bloqueia Fase 0–1. |
| BLQ-02 | Suíte nativa no macOS (toolchain) | — | Rodar suite no Linux/CI; tratar falhas native como ambientais. |
