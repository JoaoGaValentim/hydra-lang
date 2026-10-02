# BACKLOG — Hydra

Formato: `ID | status | tamanho | dependências | descrição`
Status: `todo` | `fazendo` | `bloqueado` | `feito`

---

## Fase 0 — Reconhecimento

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F0-01 | feito | M | — | Linha de baseline `mvn test` do Kof — **medida no host macOS** (808/155F/8E/26S no subconjunto focado; native inexecutável; suíte cheia não fecha aqui). Repetir em Linux/CI depois. |
| F0-02 | fazendo | M | — | Fechar auditoria: ler lexer/parser/AST/IR/backend + exemplos restantes. |
| F0-03 | todo | S | F0-02 | Contar keywords reais em `TokenType.java`. |
| F0-04 | todo | S | F0-02 | Confirmar ausência/presença de interpolação no código. |
| F0-05 | todo | S | — | `STATE.md` final da Fase 0 + commit. |
| F0-06 | todo | M | — | Esqueleto do repo: LICENSE, NOTICE, README, .gitignore, ci.yml, primeiro push. |

## Fase 1 — Especificação

| ID | Status | Tamanho | Deps | Descrição |
|---|---|---|---|---|
| F1-01 | todo | L | F0 | `ESPECIFICACAO.md`: EBNF ≤120 linhas, keywords ≤20, regra de newline, tipos, semântica essencial. |
| F1-02 | todo | M | F1-01 | `DECISOES.md`: registrar cada hipótese (repetição, ramificação, funções, type/record, val/var, lambdas, comentários, strings, imports, etc.). |
| F1-03 | todo | L | F1-01 | 30+ exemplos `.hy` escritos à mão cobrindo todos os conceitos; relitura crítica. |
| F1-04 | todo | S | F1-03 | Checklist orçamento: keywords, linhas de gramática, formas por conceito. |

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
| F4-01 | todo | L | F3 | `hydra migrate` Kof→Hydra (`.kf` → extensão definida em F1). |
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
| INF-01 | todo | S | F0-06 | CI verde no primeiro skeleton (GitHub Actions JDK 25). |
| INF-02 | todo | S | INF-01 | Badge de CI no README do Hydra. |
| INF-03 | todo | S | F0-01 | Workaround shade kof-cli documentado; se necessário, fix local sem commit no upstream. |

## Bloqueios registrados

| ID | O quê | O que foi tentado | Próximo quando destravar |
|---|---|---|---|
| BLQ-01 | Shade `kof-cli` + `${revision}` | install com flatten, sed no m2, shade.skip | Editar pom local ou testar sem package; não bloqueia Fase 0–1. |
| BLQ-02 | Suíte nativa no macOS (toolchain) | — | Rodar suite no Linux/CI; tratar falhas native como ambientais. |
