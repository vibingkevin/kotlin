/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlin.test.Test
import org.jetbrains.kotlin.backend.common.lower.enumEntriesMap
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.symbols.impl.IrEnumEntrySymbolImpl
import org.jetbrains.kotlin.name.Name

class EnumEntriesMapParallelTest {
    @Test
    fun parallelCodegenPublishesOneEnumEntriesMap() {
        raceOnSharedIr {
            val enumClass = IrFactoryImpl.buildClass {
                name = Name.identifier("Color")
                kind = ClassKind.ENUM_CLASS
            }
            repeat(4_000) { index ->
                val entry = IrFactoryImpl.createEnumEntry(
                    startOffset = 0,
                    endOffset = 0,
                    origin = IrDeclarationOrigin.DEFINED,
                    name = Name.identifier("E$index"),
                    symbol = IrEnumEntrySymbolImpl(),
                )
                entry.parent = enumClass
                enumClass.declarations += entry
            }
            return@raceOnSharedIr { enumEntriesMap(enumClass) }
        }
    }
}

fun main() {
    EnumEntriesMapParallelTest().parallelCodegenPublishesOneEnumEntriesMap()
    println("PASS")
}
