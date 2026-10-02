// 04 — type imutável: um conceito, sem new, sem class/record
type Point(Int x, Int y)

type Pair(String a, Int b)

main() {
    val p = Point(10, 20)
    println(p.x)
    println(p.y)

    val q = Pair("hello", 42)
    println(q.a)
}
