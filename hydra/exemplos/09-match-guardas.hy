// 09 — match com guardas: `case padrão if guarda -> expr`
classificar(n: Int): String {
    match (n) {
        case Int i if i < 0 -> "negativo"
        case Int i if i == 0 -> "zero"
        case Int i if i < 10 -> "pequeno"
        default -> "grande"
    }
}

main() {
    println(classificar(-1))
    println(classificar(0))
    println(classificar(5))
    println(classificar(50))
}
