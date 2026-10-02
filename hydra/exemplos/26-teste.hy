// 26 — testes: test "nome" { } + assert built-in
soma(a: Int, b: Int): Int {
    return a + b
}

test "soma basica" {
    assert(soma(2, 3) == 5, "2+3 deve ser 5")
    assert(soma(0, 0) == 0, "0+0 deve ser 0")
}

test "soma com negativo" {
    assert(soma(-1, 1) == 0, "-1+1 deve ser 0")
}

main() {
    println("rode com hydra test")
}
