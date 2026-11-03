/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlin.test.Test
import org.jetbrains.kotlin.backend.konan.lower.getObjectClassInstanceFunction
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.name.Name

class ObjectInstanceFunctionParallelTest {
    @Test
    fun parallelRttiPublishesOneObjectInstanceFunction() {
        val context = proxy(NativeLoweringContext::class.java, mapOf("getIrFactory" to IrFactoryImpl)) as NativeLoweringContext
        raceOnSharedIr {
            val objectClass = IrFactoryImpl.buildClass {
                name = Name.identifier("Singleton")
                kind = ClassKind.OBJECT
            }.withThisReceiver()
            return@raceOnSharedIr { context.getObjectClassInstanceFunction(objectClass) }
        }
    }
}

fun main() {
    ObjectInstanceFunctionParallelTest().parallelRttiPublishesOneObjectInstanceFunction()
    println("PASS")
}
