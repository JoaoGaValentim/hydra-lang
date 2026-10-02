// 15 — nullabilidade: T? + narrowing no if; sem ?./?:
acharTexto(flag: Bool): String? {
    if (flag) {
        return "encontrado"
    }
    return null
}

main() {
    val s = acharTexto(true)
    if (s != null) {
        println(s.length)
    }

    val t = acharTexto(false)
    if (t != null) {
        println(t.length)
    } else {
        println("vazio")
    }
}
