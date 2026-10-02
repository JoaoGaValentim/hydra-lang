// 23 — tipo abstrato: membros sem corpo (assinatura)
type Shape(Int sides) {
    area(): Float
}

type Square(Float side) extends Shape(4) {
    area(): Float {
        return side * side
    }
}

main() {
    val s = Square(2.0)
    println(s.area())
}
