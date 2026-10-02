# ESPECIFICACAO — Hydra

**Versão:** rascunho 0.1 (Fase 1 — em construção)
**Status:** NÃO congelado. Base: `AUDITORIA-SINTAXE.md` + `DECISOES.md`.
**Orçamento (meta):** EBNF ≤120 linhas · palavras reservadas ≤20 · uma forma por conceito.

Extensão de fonte: **`.hy`** (D-HYD-004).

---

## 1. Princípios (constituição)

1. Uma forma por conceito; se houver duas, uma morre.
2. A gramática normativa é a seção 3; o que não está lá não existe.
3. Lookahead ≤3 tokens, documentado (decisões de `for`, lambda, chamada).
4. Newline termina instrução; não há `;` na gramática (D-HYD-005).
5. Leitura de cima para baixo, sem exceções escondidas.
6. Cada construção cabe em duas frases.
7. Açúcar sintático só entra se substituir outra forma.
8. Compatibilidade com Kof é papel de `hydra migrate`, nunca da gramática.

---

## 2. Palavras reservadas (meta ≤20)

**Conjunto alvo (20):**

```
type enum import
val var
if else for match
return throw try catch
spawn await
true false null
this super
```

| Fora do conjunto | Papel |
|---|---|
| `fun`/`fn`/`func` | proibidos (como no Kof) |
| `class`/`record`/`interface` | não existem — um só `type` |
| `while`/`do` | não existem — cobertos por `for` |
| `switch`/`case` | não existem — um só `match` |
| `new` | não existe |
| `package` | não é keyword — caminho do diretório |
| `assert` | built-in de stdlib (função), não palavra |
| modificadores Java | **não existem** |
| tipos primitivos | não são keywords — nomes de tipo (`Int`, `String`) no contexto de tipo |
| `test`/`application` | declarações de topo — candidatas a keyword; se entrarem, estouram 20 e reavaliamos o conjunto |

**Comentários:** apenas `//` (D-HYD-006).

---

## 3. Gramática EBNF (normativa)

Convenção: `=` definição · `{ x }` zero ou mais · `[ x ]` opcional · `( x | y )` alternativa.

```ebnf
unit        = { import } , { decl } ;
import      = "import" , qualname ;

decl        = type-decl | fun-decl | test-decl | app-decl ;

(* tipos: um conceito, imutável por default *)
type-decl   = "type" , ident , [ type-params ] , header , body ;
header      = "(" , [ field , { "," , field } ] , ")" ;
body        = [ "{" , { member } , "}" ] ;
field       = [ "var" ] , typ , ident , [ "=" , expr ] ;
member      = field | method | ctor ;
method      = ident , "(" , params , ")" , [ ":" , typ ] , ( block | "=" , expr ) ;
ctor        = ident , "(" , params , ")" , block ;

(* funções: uma forma de retorno *)
fun-decl    = ident , "(" , params , ")" , [ ":" , typ ] , ( block | "=" , expr ) ;
params      = [ param , { "," , param } ] ;
param       = ident , ":" , typ , [ "=" , expr ] ;
typ         = qualname , [ type-params ] , { "?" } ;

type-params = "<" , ident , { "," , ident } , ">" ;
qualname    = ident , { "." , ident } ;

(* variáveis: val imutável; var mutável; anotação no mesmo lugar *)
var-decl    = ( "val" | "var" ) , ident , [ ":" , typ ] , [ "=" , expr ] ;

(* instruções *)
block       = "{" , { stmt } , "}" ;
stmt        = var-decl | return-stmt | if-stmt | for-stmt | match-stmt
            | throw-stmt | try-stmt | spawn-stmt | expr-stmt | block ;

return-stmt = "return" , [ expr ] ;
if-stmt     = "if" , "(" , expr , ")" , block , [ "else" , ( if-stmt | block ) ] ;
throw-stmt  = "throw" , expr ;
try-stmt    = "try" , block , { "catch" , "(" , ident , ")" , block } ;
spawn-stmt  = "spawn" , expr ;
expr-stmt   = expr ;

(* repetição: UMA palavra for — três cabeçalhos, um conceito *)
for-stmt    = "for" , for-head , block ;
for-head    = in-head | classic-head | cond-head ;
in-head     = ( "val" | "var" ) , ident , "in" , expr ;
classic-head= "(" , [ var-decl | expr ] , ";" , [ expr ] , ";" , [ expr ] , ")" ;
cond-head   = "(" , expr , ")" ;

(* escolha por padrão: UMA construção match — sempre expressão *)
match-stmt  = "match" , "(" , expr , ")" , "{" , { case-arm } , [ default-arm ] , "}" ;
case-arm    = "case" , pattern , [ "if" , expr ] , "->" , expr ;
default-arm = "default" , "->" , expr ;
pattern     = ident , ident                       (* binding: case String s *)
            | ident , "(" , [ bind , { "," , bind } ] , ")" ;  (* destructuring *)
bind        = [ "val" | "var" ] , ident ;

(* expressões *)
expr        = assign ;
assign      = or , [ assign-op , assign ] ;
assign-op   = "=" | "+=" | "-=" | "*=" | "/=" | "%=" ;
or          = and , { "||" , and } ;
and         = bitor , { "&&" , bitor } ;
bitor       = bitxor , { "|" , bitxor } ;
bitxor      = bitand , { "^" , bitand } ;
bitand      = equality , { "&" , equality } ;
equality    = relational , { ( "==" | "!=" ) , relational } ;
relational  = shift , { ( "<" | "<=" | ">" | ">=" ) , shift } ;
shift       = additive , { ( "<<" | ">>" ) , additive } ;
additive    = multiplicative , { ( "+" | "-" ) , multiplicative } ;
multiplicative = unary , { ( "*" | "/" | "%" ) , unary } ;
unary       = ( "!" | "-" | "await" | "spawn" ) , unary | postfix ;
postfix     = primary , { "." , ident , [ args ] | "[" , expr , "]" | args } ;
args        = "(" , [ expr , { "," , expr } ] , ")" ;

primary     = literal | ident | "this" | "super" | "(" , expr , ")"
            | lambda | if-expr | match-expr | new-like ;
if-expr     = "if" , "(" , expr , ")" , expr , "else" , expr ;
match-expr  = match-stmt ;
lambda      = "(" , [ param-list ] , ")" , "->" , ( expr | block ) ;
            | block ;                         (* trailing / bloco final *)
new-like    = ident , args ;                  (* construção: sem new *)

literal     = int-lit | float-lit | string-lit | "true" | "false" | "null" ;

(* especiais de topo *)
test-decl   = "test" , string-lit , block ;
app-decl    = "application" , "{" , { ident , block } , "}" ;
```

**EBNF contagem (alvo):** a gramática acima cabe em ~95 linhas de produção — dentro do orçamento de 120.

**Lookahead documentado (≤3):**
- `for`: após `for`, 1º token `(` → classic/cond; `val`/`var`/`ident`+`in` → in; senão expr → cond.
- lambda vs chamada: `(` params `)` `->` vs `(` expr `)`.
- `type` header vs body: `(` → header de campos; `{` → corpo de membros.

---

## 4. Tipos

| Tipo | Escrita | Notas |
|---|---|---|
| Inteiro | `Int` | inferido com `val x = 1` |
| Texto | `String` | `==` é conteúdo |
| Booleano | `Bool` | |
| Flutuante | `Float`, `Double` | |
| Nulo | `T?` | estreitamento em `if (x != null)` |
| Lista | `List<T>` | stdlib |
| Mapa | `Map<K,V>` | stdlib |
| Função | `(Int) -> Int` | lambda e tipo com o mesmo `->` |
| Tipo | `type` declaration | imutável por default |

**Sem:** `int`/`string` minúsculos como keyword; arrays `T[]` na v1 (usar `List<T>`); `Result`/`Option` no core.

---

## 5. Semântica essencial

- **Imutabilidade por default:** campos sem `var` são finais. `val` local não reatribui.
- **Construção:** `Point(10, 20)` — sem `new`. Acesso: `p.x` (leitura). Escrita só em campo `var`.
- **`if`:** sempre expressão; `else` obrigatório na posição de valor; em efeito o valor é descartado.
- **`match`:** sempre expressão; `default` obrigatório exceto enum exaustivo; sem fallthrough; guardas com `if` após o padrão.
- **`for`:** três cabeçalhos, um keyword. `cond-head` equivale a `while`. `in-head` itera coleção. `classic-head` cobre contagem.
- **Erros:** `throw` + `try/catch` (único mecanismo). Exceção = `String`. `catch` só aceita o identificador do erro.
- **Nulabilidade:** `T?`; narrowing só no ramo de `if (x != null)`.
- **Concorrência:** `spawn`/`await`. Timeout é função de stdlib.
- **Funções top-level:** sobrecarga por aridade/tipos; `main()` é o entry point reconhecido pelo nome.
- **Módulos:** sem `package` keyword; arquivos em diretórios; `import a.b.C` qualifica; sem wildcard na v1 (ou wildcard único — decidir nos exemplos).
- **Testes:** `test "nome" { … }` com `assert(cond, "msg")` (built-in).
- **FFI:** declaração `extern` com diagnóstico de lacuna por alvo (`FFI001`…), nunca silêncio.

---

## 6. O que NÃO existe (lista de morte)

`while` · `do-while` · `switch` · `case` (keyword de switch) · `new` · `class` · `record` · `interface` · `entity` · `fun`/`fn`/`func` · `let`/`const` · `;` · `/* */` · `++`/`--` · `?:`/`??`/`?.` · `instanceof` · `as` (subsumido por `match` na v1) · modificadores Java · `package` keyword · wildcard import (v1) · Result/Option no core.

---

## 7. Exemplos canônicos

Corpo completo em [`hydra/exemplos/`](exemplos/) — **39 arquivos `.hy`** numerados 01–38 (+ arquivos de apoio). Cobrem: hello, val/var, funções, tipos immutable/mutable, enum, if, match (básico/guardas/destructuring/null), for (3 cabeçalhos), strings, nullable, erros, lambdas, trailing, listas, mapas, conjuntos, herança, tipo abstrato, concorrência, imports, testes, matemática, assign composto, closures, mini programa, migração, enum exaustivo, tipos anotados, application, comentários, bitwise, ordem superior.

Amostras (as restantes estão nos arquivos):

```hy
// hello
main() {
    println("Hello, Hydra")
}
```

```hy
type Point(Int x, Int y)

type User(var name: String, var age: Int) {
    greet(): String {
        return "Hello, " + name
    }
}

main() {
    val p = Point(10, 20)
    println(p.x)

    val u = User("Mel", 26)
    println(u.greet())
}
```

```hy
main() {
    val xs = listOf(1, 2, 3)
    for x in xs {
        println(x)
    }

    for var i = 0; i < 3; i += 1 {
        println(i)
    }

    for (i < 3) {
        i += 1
    }
}
```

```hy
main() {
    val n = match 2 {
        case 1 -> "um"
        case 2 -> "dois"
        default -> "outro"
    }
    println(n)
}
```

```hy
main() {
    val s: String? = find()
    if (s != null) {
        println(s.length)
    }
}
```

---

## 8. Pendências desta especificação

- [ ] Fechar se `test`/`application` entram como keywords (estoura o orçamento). Exemplo `37-application.hy` existe como forma candidata.
- [ ] Decidir wildcard de import sim/não (exemplo `25-imports.hy` usa qualname puro).
- [ ] Decidir `as` definitivamente morto ou mantido (fora do conjunto de 20; `match` cobre downcast na v1).
- [ ] Relitura crítica dos 39 exemplos + EBNF (orçamento de linhas, keywords, formas por conceito).
- [ ] Congelar após revisão de orçamento (keywords, linhas EBNF, formas por conceito).
- [ ] Mapear cada forma para o AST/IR do Kof (contrato Fase 2–3).
- [ ] `finally`: fora do conjunto de 20; se precisar voltar, entra e sai outro.

---

*Documento vivo da Fase 1. Congelar só quando os portões de orçamento passarem.*
