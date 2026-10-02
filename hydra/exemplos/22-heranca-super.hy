// 22 — herança: extends + super
type Animal(name: String) {
    som(): String {
        return "..."
    }
}

type Dog(name: String) extends Animal {
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
