// 05 — type com membros: campos var + método
type User(var name: String, var age: Int) {
    greet(): String {
        return "Hello, " + name
    }

    celebrate() {
        age = age + 1
    }
}

main() {
    val u = User("Mel", 26)
    println(u.greet())
    u.celebrate()
    println(u.age)
}
