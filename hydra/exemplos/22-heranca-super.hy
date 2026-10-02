// 22 — herança: extends + super
type Animal(String name) {
    som(): String {
        return "..."
    }
}

type Dog(String name) extends Animal {
    som(): String {
        return "au"
    }

    latir(): String {
        return super.som() + " au au"
    }
}

main() {
    val d = Dog("Rex")
    println(d.som())
    println(d.latir())
}
