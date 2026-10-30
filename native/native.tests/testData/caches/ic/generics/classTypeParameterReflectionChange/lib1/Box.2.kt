package test

class Box<out R> {
    fun value(): R = error("Not called")
}
