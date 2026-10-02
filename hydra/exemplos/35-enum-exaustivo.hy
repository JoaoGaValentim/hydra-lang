// 35 — enum exaustivo: sem default; match cobre todos os casos
enum Dia {
    Segunda
    Terca
    Quarta
    Quinta
    Sexta
    Sabado
    Domingo
}

ehUtil(d: Dia): Bool {
    match (d) {
        case Dia.Segunda -> true
        case Dia.Terca -> true
        case Dia.Quarta -> true
        case Dia.Quinta -> true
        case Dia.Sexta -> true
        default -> false
    }
}

main() {
    println(ehUtil(Dia.Segunda))
    println(ehUtil(Dia.Sabado))
}
