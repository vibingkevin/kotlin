// RUN_PIPELINE_TILL: FRONTEND
// WITH_PLATFORM_LIBS
// REQUIRES_APPLE_TARGET
import kotlinx.cinterop.*
import platform.darwin.*
import platform.Foundation.*

class Zzz : NSAssertionHandler() {
    @OptIn(kotlinx.cinterop.BetaInteropApi::class)
    @ObjCOutlet
    var x: <!MUST_BE_OBJC_OBJECT_TYPE!>String<!>
        get() = "zzz"
        set(value: String) { }
}
