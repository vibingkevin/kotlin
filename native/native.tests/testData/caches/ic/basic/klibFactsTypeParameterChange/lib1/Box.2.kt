package facts

class Box<out R> {
    fun value(): R = error("Not called")
}
