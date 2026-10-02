// 40 — lambdas em coleções: um idioma para funções de ordem superior
main() {
    val xs = listOf(1, 2, 3, 4)
    var soma = 0
    for x in xs {
        val dobro = (n: Int) -> n * 2
        soma = soma + dobro(x)
    }
    println(soma)
}
