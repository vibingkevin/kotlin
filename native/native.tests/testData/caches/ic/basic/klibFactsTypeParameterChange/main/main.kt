import kotlin.reflect.KVariance
import kotlin.test.*
import facts.*

@Test
fun test() {
    assertEquals(KVariance.INVARIANT, declaredVariance())
    assertEquals("T", declaredTypeParameterName())
}
