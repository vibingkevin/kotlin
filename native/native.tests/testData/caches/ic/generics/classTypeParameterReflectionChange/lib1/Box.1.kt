package test

class Box<out T> {
    fun value(): T = error("Not called")
}
