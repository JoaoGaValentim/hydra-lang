// 29 — closures: lambda captura locais
main() {
    var n = 0
    val incrementar = () -> {
        n = n + 1
    }

    incrementar()
    incrementar()
    println(n)
}
