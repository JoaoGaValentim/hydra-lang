// 23 — tipo abstrato: membros sem corpo (assinatura)
type Shape(sides: Int) {
    area(): Float
}

type Square(side: Float) extends Shape {
    area(): Float {
        return side * side
    }
}

main() {
    val s = Square(2.0)
    println(s.area())
}
