/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox

import org.jetbrains.kotlin.backend.konan.AAPCS64TargetAbiInfo
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.IdSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Tag("caches")
class TargetAbiInfoTest {
    @Test
    fun collidingTypesCanBeQueriedByFourCodegenThreads() {
        val types = (0 until 512).map { index ->
            // These are distinct public signatures with equal hashes, as in the parallel IC test.
            val name = (0 until 9).joinToString("") { bit -> if (index and (1 shl bit) == 0) "Aa" else "BB" }
            val signature = IdSignature.CommonSignature("test", name, null, 0L, null)
            IrSimpleTypeImpl(IrClassSymbolImpl(signature = signature), false, emptyList(), emptyList())
        }
        assertEquals(512, types.toSet().size)
        assertEquals(1, types.map { it.hashCode() }.toSet().size)
        // Daemon threads keep a broken HashMap lookup from preventing the test JVM from exiting.
        val executor = Executors.newFixedThreadPool(4) { task -> Thread(task, "abi-cache-test").apply { isDaemon = true } }
        try {
            repeat(100) {
                val abi = AAPCS64TargetAbiInfo()
                val start = CountDownLatch(4)
                val futures = (0 until 4).map { worker ->
                    executor.submit {
                        start.countDown()
                        start.await()
                        for (index in worker until types.size step 4) {
                            assertTrue(abi.defaultParameterAttributesForIrType(types[index]).isEmpty())
                        }
                    }
                }
                try {
                    futures.forEach { it.get(10, TimeUnit.SECONDS) }
                } catch (failure: Exception) {
                    val workers = Thread.getAllStackTraces().filterKeys { it.name == "abi-cache-test" }
                    throw AssertionError(workers.entries.joinToString("\n") { entry ->
                        "${entry.key.name}: ${entry.value.joinToString("\n") }"
                    }, failure)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
