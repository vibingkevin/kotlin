package test

import kotlin.reflect.KFunction0
import kotlin.reflect.KTypeParameter

fun declaredTypeParameterName(): String {
    val reference: KFunction0<String> = ::value
    return (reference.returnType.classifier as KTypeParameter).name
}
