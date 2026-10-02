// 06 — enum: uma construção; match exaustivo sem default
enum Color {
    Red
    Green
    Blue
}

nome(c: Color): String {
    match (c) {
        case Color.Red -> "red"
        case Color.Green -> "green"
        case Color.Blue -> "blue"
    }
}

main() {
    println(nome(Color.Red))
    println(nome(Color.Blue))
}
