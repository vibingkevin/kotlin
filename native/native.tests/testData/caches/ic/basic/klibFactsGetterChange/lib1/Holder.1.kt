package facts

class Holder {
    var calls: Int = 0
    val value: Any = "OK"
        get() { return if (++calls == 1) field else 42 }
}
