// RUN_PIPELINE_TILL: CODEGEN
// WITH_PLATFORM_LIBS
// REQUIRES_APPLE_TARGET
// KT-50109
class Foo {
    companion object : platform.darwin.NSObjectMeta()
}
