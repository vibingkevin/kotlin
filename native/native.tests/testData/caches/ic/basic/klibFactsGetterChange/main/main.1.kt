import kotlin.test.*
import facts.*

@Test
fun test() {
    assertFailsWith<ClassCastException> { checkedLength(Holder()) }
}
