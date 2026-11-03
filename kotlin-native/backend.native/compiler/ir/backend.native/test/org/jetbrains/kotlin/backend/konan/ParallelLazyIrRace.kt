/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.name.Name
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Each round builds fresh IR, then several threads materialize the same lazy declaration.
 * The published objects must be one instance. Value equality is not enough: two maps of the
 * same enum entries compare equal and still mean two threads published two declarations.
 */
internal fun proxy(type: Class<*>, answers: Map<String, Any?>): Any =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type), InvocationHandler { proxyObject, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxyObject)
            "equals" -> proxyObject === args?.getOrNull(0)
            "toString" -> type.simpleName
            else -> answers[method.name] ?: when {
                method.isDefault -> InvocationHandler.invokeDefault(proxyObject, method, *(args ?: emptyArray()))
                else -> error("unexpected ${type.simpleName}.${method.name}")
            }
        }
    })

internal fun uninitialized(type: Class<*>): Any {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type)
}

internal fun IrClass.withThisReceiver(): IrClass {
    val receiver = IrFactoryImpl.createValueParameter(
        startOffset = 0,
        endOffset = 0,
        origin = IrDeclarationOrigin.INSTANCE_RECEIVER,
        kind = IrParameterKind.DispatchReceiver,
        name = Name.special("<this>"),
        type = symbol.typeWith(),
        isAssignable = false,
        symbol = IrValueParameterSymbolImpl(),
        varargElementType = null,
        isCrossinline = false,
        isNoinline = false,
        isHidden = false,
    )
    receiver.parent = this
    thisReceiver = receiver
    return this
}

internal fun raceOnSharedIr(threads: Int = 8, rounds: Int = 60, newAction: () -> () -> Any?) {
    val failures = mutableListOf<String>()
    repeat(rounds) { round ->
        val action = newAction()
        val start = CountDownLatch(1)
        val ready = CountDownLatch(threads)
        val done = CountDownLatch(threads)
        val results = arrayOfNulls<Any>(threads)
        val error = AtomicReference<Throwable>()
        repeat(threads) { index ->
            Thread {
                try {
                    ready.countDown()
                    start.await()
                    results[index] = action()
                } catch (throwable: Throwable) {
                    error.compareAndSet(null, throwable)
                } finally {
                    done.countDown()
                }
            }.also { it.isDaemon = true }.start()
        }
        check(ready.await(20, TimeUnit.SECONDS)) { "workers did not start" }
        start.countDown()
        check(done.await(20, TimeUnit.SECONDS)) { "backend threads did not finish round $round" }
        error.get()?.let { throw it }
        val first = results[0]
        if (results.any { it !== first }) {
            failures += "round $round"
        }
    }
    check(failures.isEmpty()) {
        "parallel lazy IR published distinct instances in ${failures.size}/$rounds rounds: ${failures.take(8)}"
    }
}
