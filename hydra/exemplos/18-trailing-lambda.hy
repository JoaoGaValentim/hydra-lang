// 18 — trailing lambda: conveniência de chamada (mesma forma de lambda)
aplicar(x: Int, f: (Int) -> Int): Int {
    return f(x)
}

main() {
    println(aplicar(5, (x: Int) -> x + 1))
    println(aplicar(5, (x: Int) -> {
        val y = x * 2
        y + 1
    }))
}
