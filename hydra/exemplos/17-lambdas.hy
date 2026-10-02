// 17 — lambdas: uma forma (x) -> expr ou (x) -> bloco
main() {
    val dobro = (x: Int) -> x * 2
    println(dobro(4))

    val saudar = (nome: String) -> {
        println("Hello, " + nome)
    }
    saudar("Hydra")
}
