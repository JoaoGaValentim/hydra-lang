# DECISOES — Hydra

Formato curto:
```
### D-HYD-XXX — título
- Contexto:
- Decisão:
- Alternativas descartadas:
- Consequências:
- Data:
```

---

### D-HYD-001 — Compatibilidade não é meta da gramática
- Contexto: Hydra deriva do Kof; o prompt mestre define orçamento de sintaxe rígido.
- Decisão: o Hydra **não** aceita formas sintáticas antigas do Kof. Compatibilidade é papel da ferramenta de migração (Fase 4), nunca da gramática.
- Alternativas descartadas: modo legacy no parser; aceitar ambos os estilos de `;`; aceitar `while` e `for`.
- Consequências: gramática limpa; usuários Kof migram com `hydra migrate`; testes do Hydra não herdam a suíte do Kof literalmente.
- Data: 2026-10-01

### D-HYD-002 — IR e backends são contrato estável
- Contexto: Kof tem IR de ~30 ops e backends JVM/Native/JS maduros.
- Decisão: o trabalho central do Hydra está no **frontend**. O IR permanece o contrato entre frontend e backends. Mudanças de IR exigem registro aqui e mudança mínima.
- Alternativas descartadas: reescrever IR; gerar Java como intermediário (proibido pelo mestre e pela filosofia Kof).
- Consequências: reutilização máxima de backend; paridade de alvo preservada onde o IR cobre.
- Data: 2026-10-01

### D-HYD-003 — Herança de qualidade do AGENTS.md do Kof
- Contexto: `AGENTS.md` do Kof define Q0–Q7, no-stubs, compile-before-delivery, honest diagnostics, no-silent-fallback.
- Decisão: o Hydra adota a **disciplina de qualidade** (testes, prova, sem stub, sem silêncio de alvo) mas **não** o workflow multi-agent do Kof (claims em DOING.md, sync-push do upstream, PR #619).
- Alternativas descartadas: copiar DOING.md e o ritual de claims; ignorar quality gates.
- Consequências: portões de qualidade da seção 10 do mestre valem; repositório e memória (`hydra/STATE.md`) são do Hydra.
- Data: 2026-10-01

### D-HYD-004 — Extensão de arquivo do Hydra (prévia)
- Contexto: precisa-se de extensão para `.kf` do Kof.
- Decisão (prévia, revisar na Fase 1): extensão **`.hy`** para fontes Hydra.
- Alternativas descartadas: `.kof`, `.hd`, manter `.kf`.
- Consequências: migração renomeia; tooling distingue por extensão.
- Data: 2026-10-01 (prévia — confirmar com exemplos e CLI)

### D-HYD-005 — Fim de instrução: estilo único por newline (prévia)
- Contexto: Kof aceita `;` opcional em todo statement-end.
- Decisão (prévia): no Hydra, **newline termina instrução**; `;` não é parte da gramática (ou é rejeitado). Uma regra em todo lugar.
- Alternativas descartadas: `;` obrigatório (cerimônia); opcional (dois estilos).
- Consequências: parser com regra de quebra de linha documentada e lookahead pequeno; `fmt` pode não precisar inserir `;`.
- Data: 2026-10-01 (prévia — validar com exemplos de blocos, lambdas, chamadas multi-linha)

### D-HYD-006 — Comentários: um estilo
- Contexto: Kof tem `//` e `/* */`.
- Decisão (prévia): **apenas `//`**. Sem bloco.
- Alternativas descartadas: manter os dois; usar só `/* */`.
- Consequências: menos um token de comentário; docs longas em `//` consecutivos.
- Data: 2026-10-01 (prévia)

### D-HYD-007 — Repetição: uma forma `for` (hipótese a validar na Fase 1)
- Contexto: Kof tem while/do-while/for/for-in.
- Decisão (hipótese): Hydra tem **uma** construção de repetição `for` cobrindo contagem, coleção e condição; `while`/`do-while` não existem como palavras separadas.
- Alternativas descartadas: manter `while` por ser familiar; duas formas (for + while).
- Consequências: gramática menor; necessidade de decidir sintaxe de intervalo e de "uma vez" (`do`) dentro do mesmo `for`.
- Data: 2026-10-01 (hipótese — refinar com exemplos)

### D-HYD-008 — Ramificação: `if` sempre expressão + um `match` (hipótese)
- Contexto: Kof tem if-stmt, if-expr, switch-stmt, switch-expr.
- Decisão (hipótese): `if` é sempre expressão (`else` sempre presente). Escolha por padrão é **uma** construção `match` (fusão switch/case) com binding, desestruturação, guardas opcionais e exaustividade de enum.
- Alternativas descartadas: manter switch instrução e expressão; adicionar `when` Kotlin-like ao lado.
- Consequências: duas regras de ramificação no total (if, match); pattern matching concentrado.
- Data: 2026-10-01 (hipótese)

### D-HYD-009 — Erros: único mecanismo (throw String + try/catch)
- Contexto: Kof já não tem Result/Option no core.
- Decisão: manter **um** mecanismo. `catch` aceita **apenas** o tipo de erro da linguagem (String). Rejeitar outros tipos em compilação.
- Alternativas descartadas: introduzir Result; aceitar catch genérico unspecified.
- Consequências: elimina armadilha do `catch (Int)` do Kof; diagnóstico claro.
- Data: 2026-10-01

### D-HYD-010 — Nulabilidade e concorrência preservadas
- Contexto: `T?` + narrowing e `spawn`/`await` são congelados no Kof e úteis.
- Decisão: manter como únicas formas. Sem `?.`/`?:`/`??`. Timeout por função de stdlib.
- Alternativas descartadas: monads de erro/nulidade na gramática.
- Consequências: orçamento preservado; familiaridade Kof mantida onde é bom.
- Data: 2026-10-01

### D-HYD-011 — Conjunto alvo de palavras reservadas (20)
- Contexto: `TokenType.java` medido tem **62** tokens de forma-palavra (+5 contextuais). Meta ≤20.
- Decisão: conjunto alvo de 20 — `type enum import val var if else for match return throw try catch spawn await true false null extends super`. Sem `fun`/`fn`/`func`, sem `class`/`record`/`while`/`switch`/`new`/`package`/modificadores Java/`this`. `extends` entra no lugar de `this` (membro sem `this.`; `super.m()` mantém `super`). `assert` é built-in. Tipos primitivos não são keywords.
- Alternativas descartadas: ≤30 para caber `fun`+`while`+`package`; manter `as` (subsumido por `match` na v1); manter `finally` (fora do conjunto alvo inicial); manter `this` e estourar o orçamento.
- Consequências: `test`/`application` ainda não entram como keywords (contextuais, como no Kof); se entrarem, estouram 20 e reavaliamos.
- Data: 2026-10-01 (rev. 2026-10-01 — `this` → `extends`)

### D-HYD-014 — Acesso a membro sem `this`
- Contexto: orçamento de 20 keywords; `this` consome 1 palavra e exemplos Kof/Hydra não o usam.
- Decisão: sem `this`. Campos e métodos referenciam membros pelo nome; `super` permanece para `super.m()`.
- Alternativas descartadas: manter `this` e remover `extends` (herança é conceito real do Kof); usar `@` ou prefixo.
- Consequências: ambiguidade se um parâmetro colidir com campo — resolver no semântico (escopo mais próximo), com diagnóstico claro, não com palavra nova.
- Data: 2026-10-01

### D-HYD-015 — `test`/`application` permanecem contextuais (não keywords)
- Contexto: orçamento fechado em 20 com `extends`; promover `test`/`application` estoura o orçamento.
- Decisão: **contextuais** (como no Kof) na v1. O parser reconhece `test "…"` e `application {` no início de declaração de topo; fora disso são identificadores comuns.
- Alternativas descartadas: promover a keywords e remover `extends`/`super`; deixar de fora do core e usar só `main`.
- Consequências: lookahead de topo; se o orçamento abrir, reavaliar.
- Data: 2026-10-01

### D-HYD-016 — Sem wildcard de import na v1
- Contexto: Kof aceita `import a.b.*`; wildcard soma forma e esconde nomes.
- Decisão: v1 só `import a.b.C` (qualname completo). Sem `*`.
- Alternativas descartadas: manter wildcard; import de pacote com renome.
- Consequências: migração expande imports; `fmt` pode inserir imports explícitos.
- Data: 2026-10-01

### D-HYD-017 — `as` morto na v1
- Contexto: `match` com binding cobre downcast (`case String s -> …`).
- Decisão: sem operador `as`. Se precisar, é problema de design do padrão, não de palavra nova.
- Alternativas descartadas: manter `as` no orçamento; criar `cast`.
- Consequências: `-1` menos keyword; exemplos de cast usam match.
- Data: 2026-10-01

### D-HYD-018 — `finally` fora do core na v1
- Contexto: `try/catch` cobre o caso comum; `finally` é raro e estoura o orçamento de 20.
- Decisão: v1 sem `finally`. Recursos precisam de padrão explícito (ex.: `try` + `catch` + código no mesmo bloco). Se `finally` voltar, entra e sai outra palavra.
- Alternativas descartadas: manter `finally` e remover `extends` (herança é conceito; finally é sintaxe de recurso).
- Consequências: stdlib de recursos (arquivos, conexões) usa padrão documentado sem `finally`.
- Data: 2026-10-01

### D-HYD-019 — `for` clássico com parênteses e vírgulas; `case`/`default`/`in` contextuais
- Contexto: D-HYD-005 remove `;`; a EBNF inicial do `for` clássico ainda usava `;`. `case`/`default`/`in` não podem entrar nas 20 keywords.
- Decisão: classic-head = `( var i = 0, cond, update )` com vírgulas. `in` é contextual (IDENT `in` após nome de variável no for-in). `case`/`default` são contextuais dentro de `match`. `in-head` aceita `val`/`var` opcionais (`for x in xs` e `for var x in xs`).
- Alternativas descartadas: reintroduzir `;` só para o for; promover `case`/`in` a keywords (estoura 20).
- Consequências: parser trata palavras pelo texto em contexto; exemplos 11–13 alinhados.
- Data: 2026-10-01

### D-HYD-020 — `extends` após o header de campos
- Contexto: `type Dog(String name) extends Animal` lê melhor que `extends` antes do header; exemplos 22–23 já usam essa ordem.
- Decisão: `type-decl = type ident header [extends typ] body`.
- Alternativas descartadas: ordem inversa (extends antes do header).
- Consequências: EBNF e parser alinhados aos exemplos.
- Data: 2026-10-01

### D-HYD-021 — IR Hydra espelha shape do Kof
- Contexto: D-HYD-002 congela IR/backends como contrato; Kof tem `IRModule/IRClass/IRMethod/IRBasicBlock/KofOperation`.
- Decisão: `hydra.compiler.ir.Ir` replica o shape (módulo → classes → métodos → blocks → ops de pilha) com tipos canônicos string (`Int/Float/String/Bool/Void/Any`). `IrBuilder` faz lowering AST→IR. Documentado em `hydra/IR.md`.
- Alternativas descartadas: IR de árvore tipada (mudaria o contrato de backend); reutilizar classes Java do Kof no runtime do compilador Hydra (acoplamento indevido no frontend).
- Consequências: backends futuros (JVM/JS/Native) consomem `Ir.Module`; paridade com Kof fica no shape, não na implementação Java.
- Data: 2026-10-02

### D-HYD-012 — Gramática EBNF rascunho (Fase 1)
- Contexto: precisa-se de gramática normativa ≤120 linhas antes do lexer.
- Decisão: `hydra/ESPECIFICACAO.md` §3 é a gramática alvo (~95 linhas). `for` com 3 cabeçalhos (in/classic/cond); `match` sempre expressão; `if` sempre expressão; `type` único; lambdas `(p) -> e`; construção `X()` sem `new`.
- Alternativas descartadas: EBNF descritiva longa do Kof (~406 linhas); gramática com `;`; gramática com dois estilos de comentário.
- Consequências: parser da Fase 2 nasce daqui; exemplos em `hydra/exemplos/` (**40** `.hy`) cobrem as formas.
- Data: 2026-10-01

### D-HYD-013 — Sem interpolação de string (confirmado no código)
- Contexto: auditoria §17 exigia confirmar no lexer, não só nos docs.
- Decisão: **não** herdar interpolação. `Lexer.java:213` rejeita com diagnóstico explícito; `${}` no código do Kof é interpolação de config em runtime, não sintaxe.
- Alternativas descartadas: `"$x"` estilo Kotlin; backticks; template literals.
- Consequências: strings = literal + `+`/`+=`; stdlib futura pode oferecer `format` sem mudar a gramática.
- Data: 2026-10-01

### D-HYD-022 — Backend JS com máquina de estados (switch pc)
- Contexto: F3-03 precisa emitir JS a partir de `Ir.Module`; JS não tem `goto` e o IR é CFG arbitrário (blocks + Jump/JumpIfFalse).
- Decisão: cada método JS vira `let pc = 0; while (true) { switch (pc) { … } }` — um `case` por basic block; `Jump` seta `pc` e `break`; `JumpIfFalse` condicional. Bool = `1`/`0` (mesmo domínio do JVM `int`).
- Alternativas descartadas: labels `L1:;` + `break L1` (quebra com fall-through/cascata); reescrever CFG em `if` aninhado (perde o shape do IR); interpretar ops em JS (não é compile).
- Consequências: um backend JS genérico sem refatorar o IR; paridade de alvo provável no subset coberto (`TargetParityTest`); try/catch JS e objetos ficam para a v2 do backend.
- Data: 2026-10-02

### D-HYD-023 — Native fora do subset desta estação (BLQ-02)
- Contexto: host macOS aarch64; baseline do Kof já marca Native como inexecutável (Apple ≠ GNU ELF).
- Decisão: **não** implementar alvo nativo no Hydra até haver Linux dedicado. Documentar como gap honesto em `IR.md`/`STATE.md`. JS é o alvo interpretado viável no host atual.
- Alternativas descartadas: cross-compile forçado; prometer alvo sem testar; baixar toolchain não verificada.
- Consequências: F3-03 fecha com JS + paridade; Fase de Native reabre quando o ambiente existir (não é bloqueio de roadmap, é bloqueio de host).
- Data: 2026-10-02

### D-HYD-024 — Migrator AST-walk com gate de parse e diagnósticos MIG
- Contexto: D-HYD-001 coloca compat Kof em tooling (`hydra migrate`), nunca na gramática. Kof tem AST pública (`dev.kof.compiler.*`).
- Decisão: F4-01 = `hydra.compiler.migrate.Migrator`: parse Kof → walk AST → imprime Hydra canônico → **gate** com o parser Hydra. Construtos sem forma honesta viram `MIG0xx` (do-while, finally, break/continue, implements, generics, arrays). `ok=false` quando há parcialidade; saída parcial ainda é retornada. Kof-compiler vira dependência de **compile** no módulo do compilador (só o Migrator consome).
- Alternativas descartadas: rewriter só de tokens (perde struct de class/record); migração silenciosa quebra o gate de "sem stub"; depender do backend para validar migração (fase errada).
- Consequências: corpus Kof real coberto por `MigrateCorpusTest`; usuários veem diagnóstico do que NÃO migrou; gramática Hydra permanece intacta.
- Data: 2026-10-02

### D-HYD-025 — CLI cobre só o que o pipeline prova (F6-01)
- Contexto: BACKLOG pedia fmt/check/test/run/new; o pipeline atual prova check, run (JVM/JS) e migrate.
- Decisão: `hydra.compiler.cli.Cli` implementa **check · run · migrate · version · help**. `fmt`/`new`/`test` ficam para F6-01b (não saem como stub). Wrapper `bin/hydra` usa o jar + deps do local m2.
- Alternativas descartadas: CLI com `fmt` que só trim (finge formatar); `test` sem runner; jar fat sem documentar classpath do Kof.
- Consequências: usuário migra e roda de verdade; BACKLOG marca fmt/new/test como deferred honesto.
- Data: 2026-10-02

### D-HYD-026 — fmt é reprint canônico da AST (F6-01b)
- Contexto: fmt precisa provar idempotência (`fmt(fmt(x))==fmt(x)`) sem inventar sintaxe nova; parser já produz `Ast.Unit`; comentários `//` não entram na AST.
- Decisão: `hydra.compiler.fmt.Formatter` reimprime a AST com indent 4, preservando `//` por linha original (scan fora de strings + reanexação antes/trailing). Forma canônica: blank line entre decls de topo; `for x in xs` sem `val` (val implícito); `else if` canônico; `= expr` quando o parser sugar `ReturnStmt` na mesma linha da decl; `test "…"`/`application` reconstruídos a partir de `FunDecl`. `hydra fmt <arquivo> [saida]` grava e reporta `reformat`/`unchanged`. Exemplos aspiracionais fora do subset (ex.: `20-mapas.hy` com `to`) são pulados com Assumptions — fmt não os conserta.
- Alternativas descartadas: fmt só de whitespace (perde forma canônica); fmt que descarta comentários; parser de comments no lexer mudando o contrato de tokens.
- Consequências: `FormatterTest` 57 testes (unit + corpus 39 exemplos parseáveis); CLI fmt em `CliTest`; `new`/`test` continuam deferred.
- Data: 2026-10-02

### D-HYD-027 — `assert` vira op de IR; throw/catch são String em todos os alvos (F6-01b)
- Contexto: `hydra test` precisa rodar `test "nome" { assert(cond, "msg") }` de verdade no JVM e no JS. `assert` era built-in documentado mas caía como chamada desconhecida; `throw "msg"` perdia a mensagem no JVM e `catch (e)` bindava Throwable; o flaky frame do ASM aparecia com dois try/catch seguidos (pareamento de handler por bloco adjacente).
- Decisão: `assert(cond)`/`assert(cond, "msg")` é lowering direto no topo de um statement — `Ir.Assert(message)` (não vira `Call`); arity errada é erro honesto. `Throw` carrega String (qualquer valor não-String passa por `Ir.ToString`); `CatchStart` produz String no `e` (JVM extrai `getMessage()`); o pareamento TryStart/TryEnd↔CatchStart usa **fila de regiões fechadas**, não o "try ativo". `Ir.ToString` dá concat universal (`"n=" + 42`, float/bool com forma JVM) e o JS imprime Bool como `true/false` e Float com `.0` para paridade. `Ir.safeName` sanitiza nomes de método (`test "nome com espaço"` → `test_nome_com_espaço`). O TestRunner sintetiza o harness na AST (substitui `main`, PASS/FAIL por teste, exit != 0), sem sintaxe nova.
- Alternativas descartadas: `assert` como função de stdlib (não existe runtime stdlib ainda; perderia o throw tipado); try/catch JS continuar ignorado (quebraria paridade do runner); catch tipado na linguagem (o erro é String por contrato).
- Consequências: `hydra test` roda em JVM e JS com o mesmo resultado; `hydra new` nasce com smoke test; `IrTest`/`JvmBackendE2ETest`/`JsBackendE2ETest`/`CliTest` cobrem os caminhos.
- Data: 2026-10-02

### D-HYD-028 — `match` correto no IR; enum = String no runtime; POP tipado (F6-01b+)
- Contexto: a varredura de paridade do corpus (39 exemplos) expôs código **silenciosamente errado** antes escondido: `case 1`/`case true`/`case String s` nunca testavam nada (só o padrão de enum testava); o resultado de `match`-expressão saía de um temp `Any`; `Pop` de Int/Float emitia `POP` (1 slot) em cima de `long`/`double` (2 slots) → `VerifyError`; o sujeito do match era reavaliado por braço; `case Point x y` não ligava `x`/`y`; retorno de cauda sem anotação (`tripla(x: Int) = x * 3`) virava `void`.
- Decisão: no IR, o sujeito do `match` é materializado uma vez num temp tipado e cada teste é um bloco dedicado que empilha exatamente 1 Bool (literal, enum-string, tipo→`!= null`, ou sempre-verdadeiro quando o tipo estático já casa); guards e bindings rodam no bloco do braço; destructuring lê campos do sujeito. `Pop` carrega `Type` (`POP` vs `POP2`). Enums são **String** no runtime (constante `"Tipo.Caso"`) inclusive em assinaturas — a classe-enum do IR permanece só como metadado dos casos. Retorno de cauda é inferido (join Int/Float→Float; conflito→Any; sem return→Void). Refinamento de tipo do binding não existe na v1 (o nome do padrão é cópia do sujeito).
- Alternativas descartadas: manter match "quase certo" (viola Q2/no-silent-fallback — o corpus já provava a divergência); objetos-enum de verdade no runtime (backend v2); `checker` separado agora (o IR já tem tipos suficientes para o subset).
- Consequências: 36/39 exemplos com saída idêntica JVM≡JS (faltam `listOf`/`setOf`/`readLine`/campos = Fase 5/v2, com erro honesto). Regressões cobertas em `JvmBackendE2ETest`/`JsBackendE2ETest`; `IrTest.enumAndMatch` alinhado a "enum = String".
- Data: 2026-10-02

### D-HYD-029 — stdlib mínima vira op de IR com tipo de elemento (Fase 5)
- Contexto: os built-ins documentados (`listOf`, `mapOf`, `setOf`, `readLine`) e a superfície de coleção (`xs[i]`, `.length`, `.contains`, for-in) falhavam ou caíam em runtime nos backends; o parser já tinha um bug silencioso (`xs[0]` virava `get(0)` sem alvo). Sem stdlib, metade do corpus não roda.
- Decisão: built-ins de coleção/IO são **ops do IR tipadas** (`NewList`/`NewSet`/`NewMap` com tipos de elemento, `IndexGet(collectionType, resultType)`, `Length(subjectType)`, `Contains(collectionType, valueType)`, `IterInit/IterNext`, `ReadLine`), não chamadas para um runtime escondido — cada backend mapeia para o tipo nativo (JVM: ArrayList/LinkedHashSet/LinkedHashMap + box/unbox de Long/Double/Boolean; JS: Array/Set/Map com `has`, e `keys()` para for-in de Map). `a to b` só existe dentro de `mapOf` (fora disso é erro honesto); Set não é indexável; `for k in map` itera chaves nos dois alvos (paridade). Tipos de coleção são `List<T>`/`Set<T>`/`Map<K,V>` no IR (nome canônico, parsing dos argumentos no `Ir.Type`).
- Alternativas descartadas: runtime `kof.*` em arquivo separado (segundo lugar para a mesma verdade); coleções como `Any` (perde paridade de impressão e unbox); operador `in` de membro (idioma Kof usa `contains` — uma forma por conceito).
- Consequências: **corpus 40/40 JVM≡JS**; `hydra run 25-imports.hy` lê stdin nos dois alvos (node com `redirectInput(INHERIT)`); testes de IR/JVM/JS cobrem coleções, indexação, `to`, readLine. Campos (`type`) e lambda continuam v2.
- Data: 2026-10-02

---

*Novas decisões entram aqui no ciclo em que forem tomadas (seção 8, passo 8).*
