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
| F1-01 | fazendo | L | F0 | `ESPECIFICACAO.md` rascunho 0.1 (EBNF ~71 linhas, keywords 20, tipos, semântica). **Falta:** relitura crítica + congelar orçamento. |
| F1-02 | fazendo | M | F1-01 | `DECISOES.md` D-HYD-001..013 registradas. Restam: wildcard import, `as`, `test`/`application` keywords, `finally`. |
| F1-03 | fazendo | L | F1-01 | **39** exemplos `.hy` em `hydra/exemplos/`. Falta relitura crítica contra a EBNF (ex.: alias de tipo `type Inteiro = Int` pode não estar na gramática). |
| F1-04 | todo | S | F1-03 | Checklist orçamento: keywords, linhas de gramática, formas por conceito — executar e registrar no STATE. |

## Fase 2 — Frontend

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F2-01 | todo | L | F1 | Lexer Hydra (novo, ao lado do Kof). |
| F2-02 | todo | L | F2-01 | Parser Hydra + AST. |
| F2-03 | todo | M | F2-02 | Testes de parse: válidos, inválidos, borda; mensagens com linha/coluna/sugestão. |
| F2-04 | todo | M | F2-02 | Prova: exemplos da Fase 1 → AST equivalente à do Kof correspondente. |

## Fase 3 — IR e backends

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F3-01 | todo | L | F2 | Semântica + geração de IR a partir da AST Hydra (contrato IR estável). |
| F3-02 | todo | L | F3-01 | Programa mínimo ponta a ponta em JVM. |
| F3-03 | todo | M | F3-02 | Native e JS para o mesmo programa. |
| F3-04 | todo | M | F3-02 | Suíte de paridade entre alvos. |

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
