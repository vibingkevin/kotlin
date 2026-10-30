package facts

class Holder {
    var calls: Int = 0
    val value: Any = "OK"
        get() { return field }
}
