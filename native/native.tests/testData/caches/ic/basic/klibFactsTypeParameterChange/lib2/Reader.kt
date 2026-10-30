package facts

import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVariance

fun declaredVariance(): KVariance =
    ((Box<String>::value).returnType.classifier as KTypeParameter).variance

fun declaredTypeParameterName(): String =
    ((Box<String>::value).returnType.classifier as KTypeParameter).name
