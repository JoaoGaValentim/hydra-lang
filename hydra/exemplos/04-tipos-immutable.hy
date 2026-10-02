// 04 — type imutável: um conceito, sem new, sem class/record
// Campos: nome: Tipo (uma forma só)
type Point(x: Int, y: Int)

type Pair(a: String, b: Int)

main() {
    val p = Point(10, 20)
    println(p.x)
    println(p.y)

    val q = Pair("hello", 42)
    println(q.a)
}
