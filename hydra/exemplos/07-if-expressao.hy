// 07 — if é sempre expressão; else obrigatório no lugar de valor
main() {
    val a = 3
    val b = 4

    val maior = if (a > b) a else b
    println(maior)

    // em efeito, o valor é descartado
    if (a < b) {
        println("a menor")
    } else {
        println("a maior ou igual")
    }
}
