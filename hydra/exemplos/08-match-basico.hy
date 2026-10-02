// 08 — match: uma construção; sempre expressão; default obrigatório (não-enum)
main() {
    val n = 2
    val texto = match (n) {
        case 1 -> "um"
        case 2 -> "dois"
        case 3 -> "três"
        default -> "outro"
    }
    println(texto)
}
