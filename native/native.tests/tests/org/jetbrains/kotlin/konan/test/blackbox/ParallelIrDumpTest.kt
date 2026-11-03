/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.test.blackbox.support.EnforcedHostTarget
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.callCompiler
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.KotlinNativeClassLoader
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.KotlinNativeHome
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.KotlinNativeTargets
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.provisionedXcodeCompilerArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("caches")
@EnforcedHostTarget
class ParallelIrDumpTest : AbstractNativeSimpleTest() {
    @Test
    fun parallelCodegenCreatesTheDumpDirectory() {
        assumeTrue(HostManager.host.family == Family.OSX || HostManager.host.family == Family.LINUX)
        val sources = buildDir.resolve("sources").apply { mkdirs() }
        repeat(16) { index ->
            sources.resolve("part$index.kt").writeText("fun value$index() = $index")
        }
        sources.resolve("main.kt").writeText("fun main() = println(value0() + value15())")
        val library = compileToLibrary(sources, buildDir.resolve("library").apply { mkdirs() })

        repeat(3) { round ->
            val output = buildDir.resolve("round$round").apply { mkdirs() }
            val cache = output.resolve("cache").apply { mkdirs() }
            // Let backend jobs create their shared dump directory during codegen.
            val dumps = output.resolve("dumps")
            val args = listOf(
                "-kotlin-home", testRunSettings.get<KotlinNativeHome>().dir.path,
                "-target", testRunSettings.get<KotlinNativeTargets>().testTarget.name,
                "-g", "-Xenable-incremental-compilation", "-Xbackend-threads=4",
                "-Xic-cache-dir=${cache.path}", "-Xinclude=${library.klibFile.path}",
                "-Xphases-to-dump-before=Codegen", "-Xdump-directory=${dumps.path}",
                "-o", output.resolve("main").path,
            ) + provisionedXcodeCompilerArgs
            val result = callCompiler(args.toTypedArray(), testRunSettings.get<KotlinNativeClassLoader>().classLoader)
            assertEquals(ExitCode.OK, result.exitCode) { result.toolOutput }
            assertTrue(dumps.walkTopDown().any { it.isFile && it.extension == "ir" })
        }
    }
}
