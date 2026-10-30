import kotlin.test.*
import test.*

@Test
fun runTest() {
    assertEquals(130, computedDefaultValue())
    assertEquals(130, computedDefaultValueAcrossLibraries())
}
