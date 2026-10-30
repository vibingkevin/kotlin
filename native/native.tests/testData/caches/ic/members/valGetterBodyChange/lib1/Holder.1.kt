package test

class Holder {
    var calls: Int = 0
    val value: Any = "OK"
        get() = if (++calls == 1) field else 42
}
