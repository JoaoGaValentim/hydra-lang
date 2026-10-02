// 16 — erros: único mecanismo throw String + try/catch (catch só String)
dividir(a: Int, b: Int): Int {
    if (b == 0) {
        throw "divisao por zero"
    }
    return a / b
}

main() {
    println(dividir(10, 2))

    try {
        println(dividir(1, 0))
    } catch (e) {
        println("erro: " + e)
    }
}
