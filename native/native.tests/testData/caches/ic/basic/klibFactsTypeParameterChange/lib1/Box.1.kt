package facts

class Box<out T> {
    fun value(): T = error("Not called")
}
