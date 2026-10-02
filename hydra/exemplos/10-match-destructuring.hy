// 10 — match com binding e desestruturação
type Point(x: Int, y: Int)

descrever(p: Point): String {
    match (p) {
        case Point x y if x == y -> "diagonal"
        case Point x y if x > y -> "horizontal"
        default -> "vertical"
    }
}

main() {
    println(descrever(Point(3, 3)))
    println(descrever(Point(5, 2)))
    println(descrever(Point(1, 9)))
}
