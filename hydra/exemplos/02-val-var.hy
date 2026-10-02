// 02 — val/var: imutável por default
main() {
    val x = 10
    var y = x + 5
    y = y * 2
    println(y)

    // val não reatribui — seria erro de compilação
    // val x = 11
}
