import kotlin.reflect.KVariance
import kotlin.test.*
import test.*

@Test
fun test() {
    assertEquals(KVariance.INVARIANT, declaredVariance())
    assertEquals("T", declaredTypeParameterName())
}
