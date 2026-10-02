// 24 — concorrência: spawn/await; timeout é função de stdlib
trabalho(n: Int): Int {
    return n * 2
}

main() {
    val fut = spawn trabalho(21)
    val r = await fut
    println(r)
}
