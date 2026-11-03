/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlin.test.Test
import org.jetbrains.kotlin.backend.konan.lower.NativeInnerClassesSupport
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.name.Name

class OuterThisFieldParallelTest {
    @Test
    fun parallelLayoutPublishesOneOuterThisField() {
        val support = NativeInnerClassesSupport(IrFactoryImpl)
        raceOnSharedIr(rounds = 200) {
            val outer = IrFactoryImpl.buildClass { name = Name.identifier("Outer") }.withThisReceiver()
            val inner = IrFactoryImpl.buildClass {
                name = Name.identifier("Inner")
                isInner = true
            }.withThisReceiver()
            inner.parent = outer
            return@raceOnSharedIr { support.getOuterThisField(inner) }
        }
    }
}

fun main() {
    OuterThisFieldParallelTest().parallelLayoutPublishesOneOuterThisField()
    println("PASS")
}
