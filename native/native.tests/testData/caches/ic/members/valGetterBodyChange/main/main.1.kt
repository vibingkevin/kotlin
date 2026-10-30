import kotlin.test.*
import test.*

@Test
fun test() {
    assertFailsWith<ClassCastException> { checkedLength(Holder()) }
}
