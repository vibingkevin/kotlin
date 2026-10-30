import kotlin.test.*
import test.*

@Test
fun runTest() {
    assertEquals(119, computedDefaultValue())
    assertEquals(119, computedDefaultValueAcrossLibraries())
}
