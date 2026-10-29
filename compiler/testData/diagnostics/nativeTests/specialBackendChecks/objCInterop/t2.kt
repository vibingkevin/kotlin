// RUN_PIPELINE_TILL: FRONTEND
// WITH_PLATFORM_LIBS
// REQUIRES_APPLE_TARGET
import platform.darwin.*
import platform.Foundation.*

// KT-42161, KT-63699: Must be not `NOTHING_TO_OVERRIDE` diagnostic, but `OVERRIDING_VARIADIC_OBJECTIVE_C_METHODS_IS_NOT_SUPPORTED`
class Zzz : NSAssertionHandler() {
    <!NOTHING_TO_OVERRIDE!>override<!> fun handleFailureInFunction(functionName: String, file: String, lineNumber: NSInteger /* = Long */, description: String?, vararg args: Any?) { }
}
