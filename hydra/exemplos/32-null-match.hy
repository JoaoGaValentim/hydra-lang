// 32 — match cobre null sem operador ?.
processar(x: String?): String {
    match (x) {
        case String s -> s
        default -> "vazio"
    }
}

main() {
    println(processar("oi"))
    println(processar(null))
}
