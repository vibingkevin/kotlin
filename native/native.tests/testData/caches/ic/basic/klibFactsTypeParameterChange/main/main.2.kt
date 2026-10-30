import kotlin.reflect.KVariance
import kotlin.test.*
import facts.*

@Test
fun test() {
    assertEquals(KVariance.OUT, declaredVariance())
    assertEquals("R", declaredTypeParameterName())
}
