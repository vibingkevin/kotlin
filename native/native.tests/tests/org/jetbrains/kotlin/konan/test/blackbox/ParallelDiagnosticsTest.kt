/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox

import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.diagnostics.FallbackDiagnostics
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithoutSource
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.ir.KtDiagnosticReporterWithImplicitIrBasedContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Tag("caches")
class ParallelDiagnosticsTest {
    @Test
    fun parallelBackendReportersPreserveEveryDiagnostic() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            repeat(5) {
                val collector = DiagnosticsCollectorImpl()
                val ready = CountDownLatch(4)
                val start = CountDownLatch(1)
                val futures = (0 until 4).map { worker ->
                    executor.submit {
                        // Native generation states have separate reporters over the same CLI collector.
                        val reporter = KtDiagnosticReporterWithImplicitIrBasedContext(collector, LanguageVersionSettingsImpl.DEFAULT)
                        ready.countDown()
                        start.await()
                        repeat(10_000) { index ->
                            val factory = if (index == 0) FallbackDiagnostics.FALLBACK_ERROR else FallbackDiagnostics.FALLBACK_WARNING
                            reporter.report(factory, "$worker:$index", null)
                        }
                    }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                futures.forEach { it.get(30, TimeUnit.SECONDS) }
                val diagnostics = collector.diagnostics
                assertEquals(40_000, diagnostics.size)
                assertEquals(40_000, diagnostics.map { (it as KtDiagnosticWithoutSource).message }.toSet().size)
                assertTrue(collector.hasErrors)
                assertTrue(collector.hasWarningsForWError)
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
