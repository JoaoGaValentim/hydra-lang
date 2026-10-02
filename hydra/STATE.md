# STATE — Hydra

**Última atualização:** 2026-10-01 (início da Fase 0)
**Fase atual:** 0 — Reconhecimento
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Clone de `KofLang/Kof4j` em `kof_upstream/` (read-only, remoto `upstream`).
- Remotos do repo Hydra:
  - `origin` → `https://github.com/JoaoGaValentim/hydra-lang.git` (destino oficial)
  - `upstream` → `https://github.com/KofLang/Kof4j.git` (créditos e consulta)
- `gh auth status`: autenticado como `JoaoGaValentim` com escopos `repo`, `workflow`.
- JDK 25 (Temurin) + Maven 3.10.0 disponíveis no host (darwin/aarch64).
- Documentos de linguagem do Kof lidos: `AGENTS.md`, `PHILOSOPHY.md`, `docs/philosophy.md`, `docs/language-reference/` (grammar, syntax, statements, expressions, functions, classes, closures, modules, lexical-structure, README).
- `hydra/AUDITORIA-SINTAXE.md` escrito com a primeira passada de redundâncias.

## O que funciona

- Build parcial do Kof: `kof-compiler`, `kof-runtime`, `kof-script`, `kof-c-compiler` compilam e instalam (`mvn install -DskipTests`).
- Linha de baseline **medida** (ver abaixo) — suíte cheia do Kof **não** fecha neste host.
- Esqueleto do repo Hydra: LICENSE (GPL-3.0), NOTICE (Hydra ← KofLang/Kof4j), README, .gitignore, ci.yml, `hydra/*` de memória.

## Linha de baseline do Kof (medida em 2026-10-01, host macOS aarch64)

| Item | Resultado real |
|---|---|
| Compilação módulos core | **OK** (`kof-compiler`, `kof-runtime`, `kof-script`, `kof-c-compiler`) |
| `kof-cli` package/shade | **FALHA** — `dev.kof:kof-parent:pom:${revision}` não resolvido (BLQ-01) |
| `mvn test` full reactor | **FALHA** no `kof-compiler`; módulos seguintes SKIPPED; tempo ~2–4 min parcial |
| Subconjunto focado (parser/E2E/seleção) | **808 testes / 155 falhas / 8 erros / 26 skip** em ~3:40 min |
| Causa dominante das falhas | **Backend Native** — o `as` do macOS rejeita a assembly GNU/ELF gerada (`.section`, `movq`, `syscall`); erro `COMP001` em todos os `*Native*` |
| Testes JVM/JS nomeados que falham | poucos: `JsonE2ETest.jvmDecodeBoolFalseAndWhitespace`, `KofJsFfiBridgeTest` (8 erros, provável GraalJS/node), `TypeVarianceE2ETest` cross |
| Suíte completa 3000+ do AGENTS.md | **não executada até o fim** neste host (native + shade). Número oficial do upstream **não** foi reproduzido aqui. |

**Honestidade:** os números acima são o que **este host** mediu. Não inventar "X de 3000 passam". Em Linux com binutils, a suíte native deve se comportar muito melhor (CI `upstream-compile` já clona e compila no ubuntu).

## O que está quebrado / pendente

- **Build `kof-cli` shade falha** com `${revision}` (BLQ-01). Workaround local: poms no `~/.m2` corrigidos com sed; shade ainda lê parent do reactor. Não commitar workaround no Kof.
- **Native no macOS:** ferramenta `as` da Apple ≠ GNU as. Falha **ambiental**, não regressão do Kof. Baseline real de native = Linux/CI.
- `KofJsFfiBridgeTest` com 8 erros — verificar se exige GraalJS embutido/node no host.
- Suíte cheia multi-módulo: repetir em Linux quando a CI de upstream compilar.

## Próxima ação concreta

1. Aguardar/concluir `mvn test` e registrar: total de testes, falhas, tempo, falhas por módulo.
2. Ler código do frontend (`Lexer.java`, `Parser.java`, `AstNodes.java`, `TypeDeclarations`) e um backend.
3. Fechar `AUDITORIA-SINTAXE.md` §17 (pendências).
4. Escrever `hydra/DECISOES.md` com as hipóteses validadas/rejeitadas.
5. Criar `LICENSE`, `NOTICE`, `README.md`, `.gitignore`, `.github/workflows/ci.yml` e primeiro commit + push (Fase 17.5).
6. Iniciar `hydra/ESPECIFICACAO.md` (Fase 1) com gramática EBNF ≤120 linhas e ≤20 keywords.

## Comandos úteis descobertos

```bash
# Compilar Kof sem testes (evita shade se parar antes do kof-cli)
cd kof_upstream && mvn -pl kof-compiler,kof-runtime,kof-script,kof-c-compiler install -DskipTests -B

# Suíte de teste (não dispara shade — fase test)
cd kof_upstream && mvn test -B

# Testes de um módulo
cd kof_upstream && mvn test -pl kof-compiler -B

# Flatten de poms (CI-friendly versions)
cd kof_upstream && mvn flatten:flatten -B
```

## Armadilhas conhecidas

1. **`${revision}` no Maven:** parent pom precisa de flatten; shade do `kof-cli` quebra se o parent literal vazar. Não usar `package` no Kof como gate sem tratar isso.
2. **Native no macOS:** testes native podem falhar por toolchain (`as`/`ld`/sintaxe Mach-O vs ELF). Falha de ambiente ≠ regressão do Kof. Anotar como ambiental.
3. **AGENTS.md do Kof:** regras de agentes naquele repo (claims em DOING.md, quality gates Q0–Q7, proibição de stub, etc.). O Hydra herda a **disciplina de qualidade**, não o workflow multi-agent do Kof. Não editar o `kof_upstream/` como se fosse o repo do Hydra.
4. **Não forçar push** no `upstream`. Push só em `origin` (hydra-lang).
5. **Licença GPL-3.0:** Hydra é derivado; manter LICENSE, NOTICE e créditos. Programas em Hydra não herdam GPL automaticamente (igual ao Kof).

## Números da linguagem

| Métrica | Kof (medido/parcial) | Meta Hydra |
|---|---|---|
| Palavras reservadas (TokenType + docs) | ~64 tokens de palavra (+ contextuais) | ≤20 |
| Linhas de gramática EBNF | ~406 no grammar.md (descritiva) | ≤120 |
| Formas de repetir | 4 (while, do-while, for, for-in) | 1 |
| Formas de ramificar | 4 (if-stmt, if-expr, switch-stmt, switch-expr) | 2 (if + match) |
| Formas de função (retorno) | 3 (tipo-first, tipo-suffix, void) | 1 |
| Comentários | 2 (`//`, `/* */`) | 1 |
| Estilos de `;` | 2 (opcional) | 1 |
| `class X(...)` vs `record` | 2 grafias → record | 1 conceito `type` |
| `new X()` vs `X()` | 2 | 1 (`X()`) |

## Histórico imediato

- Ciclo 1: clone, docs, remotos, auditoria inicial, suíte em background.
- Ciclo 2: baseline medido; esqueleto do repo (LICENSE/NOTICE/README/CI); auditoria com TokenType; decisões D-HYD-001..010; commit+push inicial.
