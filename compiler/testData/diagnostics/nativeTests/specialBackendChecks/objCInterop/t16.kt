// RUN_PIPELINE_TILL: CODEGEN
// WITH_PLATFORM_LIBS
// REQUIRES_APPLE_TARGET
import platform.darwin.*
import platform.Foundation.*

class Z

fun foo() = NSLog("zzz", Z())
