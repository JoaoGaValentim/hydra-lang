// 03 — funções: UMA forma (sem palavra-chave); params `nome: Tipo`
dobro(x: Int): Int {
    return x * 2
}

saudar(nome: String): String = "Hello, " + nome

// tipo no retorno é opcional quando inferido pela expressão
tripla(x: Int) = x * 3

main() {
    println(dobro(21))
    println(saudar("Hydra"))
    println(tripla(3))
}
