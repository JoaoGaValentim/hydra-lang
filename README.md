# Hydra

**Uma linguagem, uma sintaxe, muitos mundos.**

Hydra é uma linguagem de programação derivada e melhorada a partir do
[Kof](https://github.com/KofLang/Kof4j). A regra acima de todas as outras:

> **Hydra tem uma única sintaxe, simples. Para cada ideia, existe exatamente uma forma de escrevê-la.**

Hydra não é "Kof com mais recursos". Hydra é Kof **depois de uma dieta rigorosa**:
menos sintaxe, mais intenção, mesma capacidade. Se uma melhoria exige uma segunda
forma de escrever algo que já tem forma, a melhoria está errada.

---

## Estado

| | |
|---|---|
| Fase | 6 — frontend + IR/backends + migrate + CLI + fmt |
| Frontend | lexer/parser/diagnósticos HYP00x (`compiler/`) |
| IR + alvos | JVM (ASM) e JS (`switch pc`); paridade de alvos no subset |
| Migração | `hydra migrate` Kof→Hydra (AST-walk + gate + MIG0xx) |
| CLI | `hydra check\|run\|migrate\|fmt` (`bin/hydra`; new/test = F6-01b rest.) |
| Especificação | `hydra/ESPECIFICACAO.md` (congelada; EBNF ~71 linhas; keywords 20) |
| Exemplos | `hydra/exemplos/` — 40 arquivos `.hy` |
| Testes | `cd compiler && mvn -B test` (176) |
| CI | verde (structure + upstream-compile + hydra-compiler) |
| Memória | `hydra/STATE.md`, `BACKLOG.md`, `DECISOES.md`, `LOG.md` |

---

## Origem e licença

Hydra é **obra derivada** do projeto [Kof](https://github.com/KofLang/Kof4j)
(licença GPL-3.0). Veja `NOTICE` para o crédito de origem e `LICENSE` para a
licença completa do compilador e ferramentas.

Programas escritos em Hydra **não herdam** a GPL automaticamente — o autor do
programa escolhe a licença daquilo que escrever. A GPL cobre o compilador,
a toolchain e o código deste repositório.

---

## Visão (curta)

- Tipagem estática forte, com inferência onde ela reduz código sem criar ambiguidade.
- Pipeline próprio: lexer → parser → AST → tipos → semântica → IR → backends.
- **Nunca** gerar Java como linguagem intermediária.
- Um frontend, vários backends (JVM, Native, JS), com paridade de comportamento.
- Onde um alvo não cumprir uma intenção: **erro de compilação com código de lacuna**, nunca silêncio.
- Orçamento de sintaxe: gramática EBNF curta, ≤20 palavras reservadas (meta), uma forma por conceito.
- Ferramentas: `hydra build | run | check | test | fmt | migrate | …` (a implementar).

---

## Desenvolvimento

### Pré-requisitos

- JDK 25 (Temurin ou equivalente)
- Maven 3.9+
- Git
- Para alvo Native (em Linux): `binutils` (`as`, `ld`)

### Repositório

| Remoto | URL | Papel |
|---|---|---|
| `origin` | `https://github.com/JoaoGaValentim/hydra-lang` | projeto Hydra (publicação) |
| `upstream` | `https://github.com/KofLang/Kof4j` | origem Kof (somente leitura, créditos) |

O código-fonte do Kof **não** é versionado aqui como cópia de trabalho; consulte o
`upstream` ou um clone local separado.

### Estrutura

```
hydra/
  STATE.md            # memória de curto prazo (fase, o que funciona, próximo passo)
  BACKLOG.md          # tarefas priorizadas
  DECISOES.md         # registro de decisões
  LOG.md              # diário de ciclos e métricas
  AUDITORIA-SINTAXE.md  # formas sintáticas do Kof e vereditos
  ESPECIFICACAO.md    # gramática e semântica do Hydra (Fase 1)
  exemplos/           # programas pequenos na sintaxe Hydra
```

### Ciclo de trabalho

O projeto opera em ciclos (ver prompt mestre / `hydra/LOG.md`):
reorientar → escolher → entender → testar → implementar → verificar →
revisar orçamento → registrar → commit → continuar.

Regras de qualidade (herdadas da disciplina do Kof, adaptadas):

- Compila antes de entregar; testa junto com a mudança.
- Nunca apagar ou enfraquecer teste para fazer passar.
- Sem stub que finge funcionar; lacuna de alvo é diagnóstico, não silêncio.
- Orçamento de sintaxe é constituição: se estourar, simplifique antes de continuar.

---

## CI

[![CI](https://github.com/JoaoGaValentim/hydra-lang/actions/workflows/ci.yml/badge.svg)](https://github.com/JoaoGaValentim/hydra-lang/actions/workflows/ci.yml)

O workflow `.github/workflows/ci.yml` roda em `push`/`pull_request` para `main`.
A medida que o compilador Hydra existir, a CI passa a rodar `mvn test`, `hydra check`
e a suíte de paridade entre alvos.

---

## Fases (resumo)

| Fase | Saída |
|---|---|
| 0 Reconhecimento | auditoria, baseline de testes Kof, estado inicial |
| 1 Especificação | gramática única, decisões, 30+ exemplos |
| 2 Frontend | lexer/parser/AST do Hydra |
| 3 IR e backends | programa mínimo em JVM/Native/JS + paridade |
| 4 Migração | `hydra migrate` Kof→Hydra |
| 5 Stdlib | uma API por conceito |
| 6 Ferramentas | fmt, check, test, run, LSP, REPL |
| 7 Documentação | learn/ corpus mais curto que o do Kof |
| 8 Desempenho | benchmarks, fuzz, propriedades |
| 9 Melhoria contínua | laço infinito de simplificação |

---

## Contato e governança

Decisões reversíveis são tomadas e registradas em `hydra/DECISOES.md`.
O que for irreversível (publicação de release, mudança de licença, credenciais)
é decisão humana.

> **Hydra: se cortar uma cabeça e nascerem duas, você cortou do jeito errado.**
> Aqui, a cabeça que sobra é a única.
