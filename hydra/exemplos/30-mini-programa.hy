// 30 — mini programa: combina type, match, for, fun, nullable
type Conta(titular: String, var saldo: Float)

main() {
    val c = Conta("Mel", 100.0)
    println(c.titular)

    val ops = listOf(10.0, -30.0, 50.0)
    for op in ops {
        c.saldo = c.saldo + op
    }
    println(c.saldo)

    val status = match (c.saldo > 0.0) {
        case true -> "positivo"
        default -> "não positivo"
    }
    println(status)

    val nome: String? = null
    if (nome != null) {
        println(nome)
    } else {
        println("sem nome")
    }
}
