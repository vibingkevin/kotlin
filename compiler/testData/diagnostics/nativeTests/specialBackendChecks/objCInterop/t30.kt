// RUN_PIPELINE_TILL: FRONTEND
// WITH_PLATFORM_LIBS
// REQUIRES_APPLE_TARGET
import kotlinx.cinterop.*
import platform.darwin.*
import platform.Foundation.*

class Zzz : NSAssertionHandler() {
    @OptIn(kotlinx.cinterop.BetaInteropApi::class)
    @ObjCOutlet
    var <!MUST_NOT_HAVE_EXTENSION_RECEIVER!>NSObject<!>.x: NSObject
        get() = this
        set(value: NSObject) { }
}
