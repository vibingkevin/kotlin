import kotlin.reflect.KVariance
import kotlin.test.*
import test.*

@Test
fun test() {
    assertEquals(KVariance.OUT, declaredVariance())
    assertEquals("T", declaredTypeParameterName())
}
