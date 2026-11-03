/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlin.test.Test
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.ir.BackendSymbols
import org.jetbrains.kotlin.backend.common.lower.coroutines.getOrCreateFunctionWithContinuationStub
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

class ContinuationStubParallelTest {
    @Test
    fun parallelVtablePublishesOneContinuationStub() {
        val owner = IrFactoryImpl.buildClass { name = Name.identifier("Owner") }.withThisReceiver()
        val continuation = IrFactoryImpl.buildClass { name = Name.identifier("Continuation") }.withThisReceiver()
        val symbols = uninitialized(FakeContinuationSymbols::class.java) as FakeContinuationSymbols
        FakeContinuationSymbols::class.java.getDeclaredField("continuationClass").apply { isAccessible = true }.set(symbols, continuation.symbol)
        val context = proxy(CommonBackendContext::class.java, mapOf("getSymbols" to symbols)) as CommonBackendContext
        raceOnSharedIr {
            val function = IrFactoryImpl.buildFun {
                name = Name.identifier("load")
                isSuspend = true
                returnType = owner.defaultType
            }
            function.parent = owner
            return@raceOnSharedIr { function.getOrCreateFunctionWithContinuationStub(context) { it } }
        }
    }
}

private class FakeContinuationSymbols(
    builtIns: IrBuiltIns,
    override val continuationClass: IrClassSymbol,
) : BackendSymbols(builtIns) {
    override val getProgressionLastElementByReturnType get() = unused<Map<org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol, org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>>()
    override val throwNullPointerException get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val throwTypeCastException get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val throwKotlinNothingValueException get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val stringBuilder get() = unused<IrClassSymbol>()
    override val coroutineSuspendedGetter get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val getContinuation get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val returnIfSuspended get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val functionAdapter get() = unused<IrClassSymbol>()
    override val defaultConstructorMarker get() = unused<IrClassSymbol>()
    override val throwUninitializedPropertyAccessException get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val syntheticConstructorMarker get() = unused<IrClassSymbol>()
    override val coroutineContextGetter get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val suspendCoroutineUninterceptedOrReturn get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()
    override val coroutineGetContext get() = unused<org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol>()

    private fun <T> unused(): T = error("not used by continuation stub creation")
}

fun main() {
    ContinuationStubParallelTest().parallelVtablePublishesOneContinuationStub()
    println("PASS")
}
