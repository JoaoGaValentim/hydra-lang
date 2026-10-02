// 34 — type com campos + método mutável + construção sem new
type Contador(var valor: Int) {
    incrementar() {
        valor = valor + 1
    }

    atual(): Int {
        return valor
    }
}

main() {
    val c = Contador(0)
    c.incrementar()
    c.incrementar()
    println(c.atual())
}
