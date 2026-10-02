// 36 — anotação de tipo em val/var + tipo de função
type Inteiro = Int

aplicar(x: Int, f: (Int) -> Int): Int {
    return f(x)
}

main() {
    val a: Int = 1
    val b: Inteiro = a + 1
    println(b)

    val f: (Int) -> Int = (x: Int) -> x * x
    println(aplicar(3, f))
}
