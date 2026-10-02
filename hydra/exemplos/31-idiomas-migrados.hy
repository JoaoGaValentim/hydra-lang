// 31 — após-migração: estilo Kof canônico traduzido para Hydra
// Kof: while + switch + class + new + fun — todos mortos aqui

type Point(x: Int, y: Int)

area(p: Point): Int {
    return p.x * p.y
}

classificar(n: Int): String {
    match (n) {
        case 0 -> "zero"
        default -> "outro"
    }
}

main() {
    val p = Point(3, 4)
    println(area(p))

    var i = 0
    for (i < 3) {
        println(classificar(i))
        i += 1
    }
}
