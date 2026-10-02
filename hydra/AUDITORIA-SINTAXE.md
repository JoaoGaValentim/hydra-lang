# Auditoria de Sintaxe — Kof 0.5.0-beta

**Fonte:** repositório `KofLang/Kof4j` (clone local `kof_upstream/`), branch do clone com `AGENTS.md` datado de 27/09/2026, versão `0.5.0-beta`.
**Método:** leitura de `docs/language-reference/` (grammar, syntax, statements, expressions, functions, classes, closures, modules, lexical-structure), `training/idioms/`, `training/examples/`, `learn/` e código do frontend citado nas evidências.
**Status:** parcial — seções marcadas `INCOMPLETO` precisam da suíte de testes e da leitura do parser.

Legenda de criticidade para o Hydra:
- **REDUNDANTE** — duas ou mais formas para a mesma ideia; uma deve morrer no Hydra.
- **MANTER** — forma única ou justificada; candidata a preservar.
- **AVALIAR** — pode simplificar, mas exige decisão registrada.
- **CORRIGIR** — armadilha ou inconsistência que o Hydra não deve herdar.

---

## 1. Repetição (loops)

| Forma Kof | Exemplo | Papel |
|---|---|---|
| `while` | `while (cond) { … }` | condição antes de cada iter. |
| `do…while` | `do { … } while (cond)` | corpo ao menos uma vez. |
| `for` clássico | `for (var i = 0; i < n; i++) { … }` | init;cond;update. |
| `for-in` | `for (var item in lista) { … }` | itera `List<T>` ou array. |

**Achados:**
- Quatro formas para repetição. O corpus e os idiomas ensinam as quatro como equivalentes em uso diário.
- `for-in` aceita **apenas** `List<T>` e array (strings/Map/Set/receivers → `SEM058`). Isso é uma limitação de semântica escondida atrás de uma sintaxe ampla.
- `in` é palavra **contextual** (só existe dentro de `for-in`) — não é keyword do lexer. Isso é bom para o orçamento, mas cria leitura de contexto.
- `break`/`continue` sem label (`PARSE041` em label). Não há escape de laço aninhado além de flags/lógica.
- `do…while` existe, mas não aparece nos exemplos canônicos de `training/idioms/control-flow.md` como forma recomendada — aparece como forma válida.

**Veredito Hydra (hipótese inicial, a validar):**
- **REDUNDANTE:** `while`, `do…while`, `for` clássico, `for-in` como formas separadas.
- **Proposta:** uma palavra `for` cobrindo:
  - intervalo/contagem (`for i in 0..n` ou sintaxe equivalente — **ver decisão de intervalo**),
  - coleção (`for x in coll`),
  - condição geral (`for cond { }` ou desugar `while` para o mesmo `for`).
- `while` e `do…while` **não** entram como formas separadas. Se `do…while` for necessário (corpo ao menos uma vez), o `for` precisa de uma forma declarada e única — nunca uma segunda palavra.
- `break`/`continue` permanecem como operadores de controle de laço únicos.

---

## 2. Ramificação (if / switch / pattern)

| Forma Kof | Exemplo | Papel |
|---|---|---|
| `if` instrução | `if (c) { … } else { … }` | efeito; `else` opcional. |
| `if` expressão | `var x = if (c) a else b` | valor; `else` **obrigatório** (`PARSE044`). |
| `switch` instrução | `switch (x) { case 1: …; default: … }` | casos com `:`; sem fallthrough. |
| `switch` expressão | `var y = switch (o) { case T t -> …; default -> … }` | valor com `->`; `default` obrigatório exceto enum exaustivo (`SEM032`). |

**Achados:**
- **Duas formas de `if`** (instrução vs expressão) com regra diferente de `else`. Instrução: `else` opcional. Expressão: `else` obrigatório.
- **Duas formas de `switch`** (instrução `:` vs expressão `->`). Duas pontuações para o mesmo conceito.
- Pattern matching **só existe no switch**: binding (`case String s`) e desestruturação (`case Point(var x, var y)`).
- **Não há** guardas (`case P(x) if x>0`), padrões aninhados, `when`, nem padrão em `if`/`while` — catalogado como gap SG-014.
- Enum sem `default` em switch exige cobertura total (`SEM031`) — bom para exaustividade; manter no Hydra.
- Não há ternário `?:`; `if` expressão é o substituto natural.
- `instanceof` e `as` existem como operadores binários fora do pattern matching — **segunda via** de checagem de tipo/cast.

**Veredito Hydra:**
- **REDUNDANTE:** `if` instrução vs expressão; `switch` instrução vs expressão.
- **Proposta:**
  1. `if` é **sempre expressão**. Em posição de efeito, o valor é descartado. `else` sempre presente (elimina a regra "exceto quando é instrução").
  2. **Uma** construção de escolha por padrão (fusão atual `switch`/`case`), com:
     - binding e desestruturação,
     - guardas se forem adicionadas (se sim, uma única forma `case P(x) if guard -> …`),
     - exaustividade para enums,
     - sem fallthrough (regra única).
  3. `instanceof`/`as` **fora** do pattern? AVALIAR: se o pattern cobre a ideia "cheque o tipo e vincule", `instanceof`/`as` podem morrer como superfície binária (fica `as` no contexto de cast explícito se ainda for necessário, ou o próprio pattern).

---

## 3. Declaração de função

| Forma Kof | Exemplo |
|---|---|
| tipo antes do nome | `String saudacao() { … }` |
| tipo depois dos parênteses | `despedida(): String { … }` |
| sem tipo (void) | `main() { … }` / `void faz() { … }` |
| corpo expressão | `Bool positivo(Int x) = x > 0` |
| corpo bloco | `String f() { return "x" }` |
| corpo abstrato | `… ;` (interface/abstract) |

**Achados:**
- **Três** formas de escrever o tipo de retorno (antes do nome, depois com `:`, implícito void).
- Dois ordens de parâmetro também válidos: `f(Int x)` e `f(x: Int)`.
- **Não há** keyword `fun`/`fn`/`func` (são reserved, `PARSE085`) — bom, já é orçamento.
- Sobrecarga top-level e de método existe (por aridade/tipos); duplicata exata → `SEM047`.
- `main` é reconhecido **por nome** (não keyword); aceita 0 args ou 1 arg `List<String>`/`String[]`.
- Parâmetros com default geram sobrecargas sintéticas por aridade no lowering.
- Não há varargs, nem parâmetro por referência.

**Veredito Hydra:**
- **REDUNDANTE:** tipo antes do nome vs depois com `:`. Duas formas para "declaração de função com retorno".
- **Proposta (candidata):** **uma** forma canônica. Opções:
  - **A (recomendada preliminar):** `nome(params): Tipo { … }` — tipo **depois** dos parênteses, sempre explícito quando não void; void omitido (`main() { }`).
  - **B:** `Tipo nome(params) { … }` — tipo antes do nome (estilo mais "declarativo").
  - Decisão registrada em `DECISOES.md` na Fase 1, com teste de legibilidade.
- Corpo `= expr` **pode** permanecer como **única** forma de expressão-body (substitui `return` em funções triviais). Se entrar, `return` continua para blocos. Açúcar que **substitui** verbosidade, não que soma.
- Ordem de parâmetros: **uma**. Candidata: `nome(x: Int, s: String)` (anotado) **ou** `nome(Int x, String s)` (tipo-first). Escolher uma e migrar.

---

## 4. Variáveis

| Forma Kof | Exemplo |
|---|---|
| `val` inferido | `val y = 20` |
| `var` inferido | `var x = 10` |
| tipo-first | `String nome = "Mel"` |
| anotado | `var idade: Int = 30` |

**Achados:**
- `val` bloqueia reatribuição (`SEM037`) — bom.
- Quatro combinações de escrita para "uma variável".
- `val` em campo de classe → `PARSE016`; usa-se `final` — **segundo mecanismo** de imutabilidade em membros.
- Sem `let`/`const` (já rejeitados) — bom.
- Top-level `val`/`var` proibido (`PARSE007`) — só funções/tipos no topo. Decisão estrutural clara.

**Veredito Hydra:**
- **AVALIAR:** `val`/`var` ficam se o custo for baixo (são 2 keywords, semântica clara).
- **REDUNDANTE:** tipo-first vs anotado como formas paralelas. Candidata única:
  - `val x: Int = 30` / `var x = 10` (anotação opcional sempre no mesmo lugar),
  - **ou** tipo-first única `Int x = 30` + keyword de mutabilidade ausente quando imutável.
- Campo imutável: **um** mecanismo (provavelmente `val` em membro, ou `final` como único token — escolher um, eliminar o par `val`+`final`).

---

## 5. Tipos de dados (record / class / interface / enum / entity)

| Conceito | Forma Kof | Observação |
|---|---|---|
| dado imutável | `record Point(Int x, Int y)` | accessors `p.x()`; equals/hashCode/toString gerados. |
| estado mutável | `class User { String name … }` | campos públicos por default; sem getters. |
| atalho record | `class User(String name, Int age) { }` | **é record** (parser rota para record body). |
| contrato | `interface Shape { Double area() }` | sem type-params (`PARSE007`). |
| constantes | `enum Color { Red, Blue }` | sem corpo/métodos; valor = instância singleton; `SEM062` em `== "Red"`. |
| entidade DB | `entity User { id: Int generated }` | campos + flags `generated`/`unique`. |

**Achados críticos:**
- **`class X(...)` com parênteses é record, não class.** Duas grafias para o mesmo conceito (record). Documentado como comportamento estável — mas é exatamente o tipo de ambiguidade que o Hydra rejeita.
- Record vs class: resolvem **imutabilidade vs mutabilidade**, mas com **sintaxes paralelas** (ambas podem ter corpo de métodos; record também tem `extends`/`implements`).
- `class` sem parênteses tem constructor manual (`constructor(...)` ou bloco); `User(...)` sem `new` funciona por construção implícita; `new User(...)` também — **duas formas de instanciar**.
- Acesso a record: `p.x` **e** `p.x()` ambos funcionam (probe) — **duas formas de leitura**.
- `enum` é "só constantes" mas gera métodos sintéticos (`values`, `valueOf`, `name`, …) — semântica rica com sintaxe mínima (bom padrão para o Hydra).
- `entity` é vocabulário de domínio embutido na linguagem — candidato a library/stdin no Hydra, não a keyword.

**Veredito Hydra:**
- **REDUNDANTE:** `record` vs `class X(...)`; `new X(...)` vs `X(...)`; `p.x` vs `p.x()`.
- **Proposta:**
  1. **Um** construtor de tipo de dados com campos. Imutabilidade por default. Comportamento (métodos) opcional no mesmo conceito.
     - Exemplo de alvo: `type Point(Int x, Int y)` + `type User(String name, Int age) { greet(): String { … } }` com mutabilidade explícita quando necessária (`type mutable User { var name: String }` ou similar — **decidir na Fase 1**).
  2. **Uma** forma de instanciação: `Point(10, 20)` — **sem `new`**. `new` sai da gramática.
  3. **Uma** forma de acesso: escolher `p.x` (leitura de campo) **ou** `p.x()` (método gerado). Recomendação preliminar: `p.x` se `type` gera campo real; `p.x()` se o contrato for "método". **Registrar na decisão.**
  4. `enum` permanece como forma de constantes nomeadas (já é enxuta).
  5. `interface`/`entity`: avaliar se `type` com corpo abstrato cobre interface; `entity` vira biblioteca de ORM, não keyword.

---

## 6. Lambdas e passagem de função

| Forma Kof | Exemplo |
|---|---|
| lambda com params | `(x: Int) -> x * 2` |
| lambda sem params | `() -> println("oi")` |
| lambda com bloco | `(a: Int, b: Int) -> { return a + b }` |
| bloco-lambda (0 params) | `{ println("bloco") }` |
| trailing lambda | `list.forEach { x: Int -> println(x) }` / `app.get("/hello") { … }` |

**Achados:**
- Duas notações de lambda (`(params) -> body` e `{ body }`).
- Trailing lambda é açúcar sobre "último argumento é função" — padrão de DSL (web, UI) muito usado no Kof (`app.get`, `Button`, etc.).
- Inferência de tipo de parâmetro funciona no contexto de `List.map/filter/reduce` (SG-012); sem contexto, `Object` + erro claro — bom diagnóstico.
- Tipos de função: `(Int, String) -> Bool` — já é a forma `->` unificada com lambda.
- Captura mutável usa Box (runtime) — semântica boa; não é sintaxe.

**Veredito Hydra:**
- **AVALIAR (hipótese do prompt):** se trailing lambda for mantido, ele é o **único** jeito de passar a última função; se não, é removido.
- **Análise de valor:** trailing lambda é o que permite `app.get("/x") { }` sem cerimônia — está alinhado com "intenção". **Recomendação preliminar: manter trailing lambda como forma canônica de "última função", e unificar a notação de lambda.**
- Unificação candidata:
  - **Uma** notação: `(x) -> expr` ou `(x) -> { … }` (parênteses sempre quando há params; bloco `{ }` sem params só no trailing?).
  - **Decisão:** se trailing entra como forma canônica, o corpo `{ }` sem params **substitui** `() -> { }` no contexto de trailing. Fora de trailing, `() -> …` continua? Isso cria duas formas de novo.
  - **Alternativa estrita:** trailing lambda **é** a única forma de bloco final; lambda nomeada usa sempre `(params) -> body`. `f { }` ≡ `f(() -> { })`. **Uma forma de escrever a função anônima: `(params) -> body`.** Trailing é conveniência de chamada, não segunda forma de lambda.
- `->` é o operador único de função (lambda e tipo de função) — **manter**.

---

## 7. Strings e literais

| Item | Forma Kof |
|---|---|
| string | `"texto"` |
| interpolação | **não existe** no parser de 0.5.0-beta (conforme docs lidas; concatenção via `+`) |
| char | `'c'` |
| aspas alternativas | não documentadas como válidas |

**Achados:**
- Concatenação com `+`; conteúdo em `==` (nunca `.equals`) — boa regra, já congelada.
- Sem interpolação declarada na linguagem — os exemplos usam `"a" + x + "b"`. Se o Hydra quiser interpolação, entra como **substituição** de concatenação em literais (uma forma), não soma de sintaxe.
- Comentários: `//` e `/* */` — **duas formas**.

**Veredito Hydra:**
- **Strings:** um literal `"…"`; **uma** forma de interpolação se existir (candidata: `"Olá, {nome}"` ou `s"…"` — escolher e eliminar concatenação como forma canônica de literais compostos? Concatenação `+` pode permanecer para runtime; literais interpolados cobrem 90% dos casos).
- **Comentários:** escolher **um** estilo. Recomendação: `//` linha apenas (remove bloco `/* */` que não é nestable e não documenta). Se doc-comentários forem necessários, definir **um** token de doc depois (não agora).

---

## 8. Erros

| Forma Kof | Exemplo |
|---|---|
| throw string | `throw "msg"` |
| try/catch/finally | `try { } catch (String e) { } finally { }` |
| Result/Option | **não existem** como tipos |

**Achados:**
- **Um** mecanismo: exceção String. `throw` exige String (`SEM026`). Bom — já é orçamento.
- `catch (Int e)` compila mas comportamento unspecified — **armadilha**: o catch tipo não filtra de verdade.
- `finally` funciona em return/throw — ok.
- Não há `Result<T,E>` nem `Option` — erros são exceção. **Isso alinha com "um mecanismo".**

**Veredito Hydra:**
- **MANTER:** throw String + try/catch/finally como único mecanismo.
- **CORRIGIR:** `catch` deve aceitar **apenas** o tipo de erro da linguagem (String). Rejeitar `catch (Int)` em compilação — elimina comportamento unspecified.
- Não introduzir Result/Option como segundo estilo de erro no core.

---

## 9. Concorrência

| Forma Kof | Exemplo |
|---|---|
| spawn fire-and-forget | `spawn trabalho()` |
| spawn com handle | `val r = spawn compute()` |
| await | `var v = await r` |
| timeout | `awaitTimeout(h, ms)` (**função**, não sintaxe) |

**Veredito Hydra:**
- **MANTER:** `spawn`/`await` como única forma de concorrência.
- Timeout como **função de stdlib**, não keyword — já é o padrão do Kof; manter.

---

## 10. Nulabilidade

| Forma Kof | Exemplo |
|---|---|
| tipo nullable | `String?` |
| narrowing | `if (x != null) { … }` |

**Achados:**
- `?` é sufixo de tipo, não operador de expressão. Não existe `?:`, `??`.
- Narrowing só no then-branch do `if (x != null)`.
- Null literal na atribuição é rejeitado (`SEM048`); null chega via API.

**Veredito Hydra:**
- **MANTER:** `T?` + estreitamento por `if (x != null)`.
- Não adicionar `?.`/`?:`/`??` (são sintaxe extra; o narrowing cobre o caso comum).

---

## 11. Módulos / imports

| Forma Kof | Exemplo |
|---|---|
| package | `package com.dev.app` |
| import classe | `import com.dev.NodeUI` |
| import wildcard | `import com.dev.*` |
| import stdlib | `import kof.json` |

**Achados:**
- Sem `module` keyword; "módulo" = root directory passado ao compilador.
- Wildcard **não** qualifica nomes simples (anti-guess) — bom.
- Sem rename (`as`), sem `export`, sem import static.
- Colisão de wildcard não adivinha — mantém qualificação honesta.
- `in` é contextual; `test`/`application` são identifiers contextuais — **keywords disfarçadas**. AVALIAR: no Hydra, `test` deve ser keyword ou contextual? Contextual economiza orçamento; keyword melhora diagnóstico.

**Veredito Hydra:**
- **Uma** forma de import: `import a.b.C` e/ou `import a.b` (pacote). Wildcard? Se entrar, uma forma só. Recomendação preliminar: **sem wildcard** (remove uma forma; diretórios do pacote já trazem os arquivos). Se wildcard for mantido, é a única forma de importar "tudo de um pacote".
- `package` com ponto — manter.
- Sem rename — manter (remove sintaxe).

---

## 12. Declarações especiais

| Forma Kof | Exemplo | Natureza |
|---|---|---|
| test embutido | `test "nome" { assert(c, "msg") }` | contextual |
| application | `application { onStart { } onShutdown { } }` | contextual |
| extern FFI | `extern "lib" f(Int x): Int` | keyword |

**Veredito Hydra:**
- **MANTER:** testes embutidos e application como declarações de topo — são o coração do "menos código".
- `extern` permanece como primitiva de FFI com diagnóstico de lacuna por alvo (`FFI001`/`FFI002`).
- Contextual vs keyword: **escolher um**. Se `test` for keyword, sai da lista de identifiers comuns e melhora erro. Custo: 1 palavra reservada. Recomendação preliminar: keyword (orçamento ≥20 comporta).

---

## 13. Operadores e pontuação

| Item | Kof | Observação |
|---|---|---|
| atribuição composta | `= += -= *= /= %= &= \|= ^= <<= >>= >>>=` | 11 formas. |
| incremento | `++ --` prefixo e pósfixo | 4 combinações. |
| lógicos | `&& \|\| !` | ok. |
| bit a bit | `& \| ^ << >> >>>` | sem `~` (usa `x ^ -1`). |
| comparação string | `<` rejeitado (`SEM053`) | usar `compareTo`. |
| ponto-e-vírgula | **opcional** em toda posição de fim de instrução | dois estilos de pontuação. |
| `instanceof` / `as` | operadores binários | fora do pattern. |
| membros | `[]` arrays; `.get` em array → `SEM028` | coerência ok. |

**Achados críticos:**
- **Ponto-e-vírgula opcional** em todo statement-end → a linguagem aceita dois estilos de código. Isso viola "uma forma por conceito" na **estética** do programa (fmt pode canonicizar, mas o orçamento fala em gramática).
- Compound assignment de 11 operadores é muito; mas são o mesmo conceito (`op=`). Pode reduzir para `= += -= *= /=` se bitwise composto for raro — **avaliar com dados de uso**.
- `++`/`--` podem morrer em favor de `i += 1` (remove prefixo/pósfixo e o "valor velho" não-testado).

**Veredito Hydra:**
- **Fim de instrução:** **um** estilo. Candidatas:
  - **A:** newline sempre termina instrução (estilo Kotlin/Go sem `;`); `;` não existe ou é erro.
  - **B:** `;` sempre obrigatório (estilo C/Java).
  - Recomendação preliminar: **A** (newline termina; remove o segundo estilo). Parser: lookahead pequeno documentado.
- **Compound assignment:** candidata a redução para as 6 aritméticas + lógicas se o orçamento apertar; manter as de bitwise se o target native precisar.
- `++`/`--`: **avaliar remoção** (ganha 1 conceito, perde pouco).

---

## 14. Imports de stdlib e nomes de domínio (superfície de API, não gramática)

Módulos citados: `kof.io`, `kof.time`, `kof.cache`, `kof.web`, `kof.http`, `kof.security`, `kof.db`/`kof.orm`, `kof.config`, `kof.log`, `kof.ui`, `json`.
Idiomas de coleção: `listOf`, `mapOf`, `setOf`.
JSON: `json.decode<User>(body())`.

**Veredito Hydra (Fase 5):** uma API por conceito; nomes consistentes; nada duplicado. Não é gramática — entra no backlog da stdlib simplificada.

---

## 15. Tokens e palavras — orçamento atual (Kof)

Evidência de código: `TokenType.java` (130 linhas) + `Lexer.java`.

**Contagem medida em `TokenType.java` (enum):**
- Tokens de palavra (keywords + literais boolean/null + tipos primitivos + `fun`/`fn`/`func` reserved): **~64** valores de enum com forma de palavra.
- Lista de keywords no lexer (docs `lexical-structure.md` §1.1): ~60 entradas + `extern` + 3 reserved mortos (`FUN`/`FN`/`FUNC`) ≈ **64 tokens de palavra**.
- **Meta Hydra:** ≤20 palavras reservadas. Isso exige corte grande de modificadores Java-like (`transient`, `volatile`, `synchronized`, `native`, `default`, `override`, `abstract`, `public`, `private`, `protected`, `static`, `final`…) e de keywords de tipo primitivas se unificadas (`int`/`long`/`float`/`double`/`byte`/`short`/`char` podem virar um punhado de nomes de tipo no parser, não keywords separadas — avaliar).

**Contextuais confirmados no parser (`Parser.java:60-77`):** `test`, `application`, `infra`, `sealed` (modificador). `in` é contextual no `for-in`.

**Achado extra:** `infra "nome" { … }` é declaração de topo contextual (Makealive) — mais uma forma de declaração especial. No Hydra: fora do core inicial, ou library.

---

## 16. Tabela-resumo: conceito → formas no Kof → Hydra

| Conceito | Formas no Kof | Veredito | Hipótese Hydra |
|---|---|---|---|
| Repetição | while, do-while, for, for-in | REDUNDANTE | um `for` |
| Ramificação | if-stmt, if-expr, switch-stmt, switch-expr | REDUNDANTE | `if` sempre expr + um `match` |
| Função: retorno | tipo-first, tipo-suffix, void implícito | REDUNDANTE | uma forma |
| Função: params | `f(Int x)`, `f(x: Int)` | REDUNDANTE | uma forma |
| Função: corpo | bloco, `= expr`, abstrato `;` | AVALIAR | `= expr` se substituir; senão um corpo |
| Lambda | `(x) -> e`, `{ e }`, trailing | AVALIAR | `(x) -> e` única; trailing = conveniência de chamada |
| Variáveis | val, var, tipo-first, anotado | AVALIAR | val/var + uma forma de anotação |
| Dados | record, class, class(...)=record | REDUNDANTE | um `type` (imutável por default) |
| Instanciação | `X()`, `new X()` | REDUNDANTE | `X()` |
| Acesso record | `p.x`, `p.x()` | REDUNDANTE | uma forma |
| Enum | enum { A, B } | MANTER | enum |
| Interface | interface | AVALIAR | talvez type com corpo abstrato |
| Entity | entity | AVALIAR | stdlib ORM, não keyword |
| Erros | throw String, try/catch/finally | MANTER | único mecanismo; catch só String |
| Nulabilidade | `T?` + narrowing | MANTER | `T?` + narrowing |
| Concorrência | spawn/await | MANTER | spawn/await |
| Comentários | `//`, `/* */` | REDUNDANTE | um estilo |
| Strings | `"…"` + `+` | AVALIAR | literal + uma interpolação |
| Ponto-e-vírgula | opcional | REDUNDANTE | um estilo de fim |
| `new` | sim | REDUNDANTE | não |
| `++`/`--` | sim | AVALIAR | remover a favor de `+=` |
| Compound assign | 11 ops | AVALIAR | reduzir se possível |
| Import | classe, wildcard, pacote | AVALIAR | uma forma |
| `instanceof`/`as` | binários | AVALIAR | subsumidos pelo pattern? |
| test/application | contextuais | MANTER | manter (keyword?) |
| `sealed` | contextual | AVALIAR | fora do core inicial? |
| Modificadores Java | public/private/protected/static/final/… | REDUNDANTE | mínimo (default público; um de visibilidade se necessário) |
| `fun`/`fn`/`func` | reserved mortos | MANTER como mortos | fora do Hydra |

---

## 17. Pendências desta auditoria (Fase 0 incompleta)

- [ ] Linha de base real de testes (`mvn test`) — em execução; resultados em `STATE.md`.
- [ ] Leitura do código: `Lexer.java`, `Parser.java`, `ExpressionParser`, `TypeDeclarations`, `StatementParser`, IR e um backend completo.
- [ ] Varredura de `learn/` e `training/` para formas sintáticas ainda não listadas (UI, web, db, concurrency examples).
- [ ] Contagem precisa de keywords do `TokenType.java`.
- [ ] Confirmar se interpolação de string existe em algum lugar do compiler (docs dizem não; código manda).
- [ ] `examples/ci`, `examples/fullstack`, `examples/orm` para formas reais de programa.
- [ ] Decisões pontuais registradas em `DECISOES.md` com alternativas descartadas.

---

*Documento vivo. Atualizado a cada ciclo da Fase 0.*
