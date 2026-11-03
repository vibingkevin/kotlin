/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox

import org.jetbrains.kotlin.konan.test.blackbox.support.EnforcedHostTarget
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.KotlinNativeClassLoader
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.KotlinNativeHome
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.KotlinNativeTargets
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.provisionedXcodeCompilerArgs
import org.jetbrains.kotlin.konan.test.blackbox.support.util.getAbsoluteFile
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider

@Tag("caches")
@EnforcedHostTarget
class ParallelCompilationCancellationTest : AbstractNativeSimpleTest() {
    @Test
    fun cancellationWaitsForWorkersAndClosesThePool() {
        assumeTrue(HostManager.host.family == Family.OSX || HostManager.host.family == Family.LINUX)
        val sources = buildDir.resolve("sources").apply { mkdirs() }
        repeat(4) { file ->
            sources.resolve("file$file.kt").writeText("fun value$file() = $file")
        }
        sources.resolve("main.kt").writeText("fun main() = println(value0() + value1() + value2() + value3())")
        val library = compileToLibrary(sources, buildDir.resolve("library").apply { mkdirs() })
        val classes = buildDir.resolve("probe-classes").apply { mkdirs() }
        val helper = getAbsoluteFile("native/native.tests/testData/caches/parallelCancellation/InterruptCompiler.java")
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.path, helper.path))
        val compilerClasspath = (testRunSettings.get<KotlinNativeClassLoader>().classLoader as URLClassLoader)
            .urLs.map { File(it.toURI()).path }
        val java = File(System.getProperty("java.home"), "bin/java").path

        for (interrupt in listOf(false, true)) {
            val output = buildDir.resolve("probe-$interrupt").apply { mkdirs() }
            val cache = output.resolve("cache").apply { mkdirs() }
            val log = output.resolve("output.txt")
            val command = listOf(
                java, "-Xmx2g", "-cp", (listOf(classes.path) + compilerClasspath).joinToString(File.pathSeparator),
                "InterruptCompiler", interrupt.toString(),
                "-kotlin-home", testRunSettings.get<KotlinNativeHome>().dir.path,
                "-target", testRunSettings.get<KotlinNativeTargets>().testTarget.name,
                "-g", "-Xprofile-phases", "-Xbackend-threads=4", "-Xenable-incremental-compilation",
                "-Xic-cache-dir=${cache.path}", "-Xinclude=${library.klibFile.path}", "-o", output.resolve("main").path,
            )
            val process = ProcessBuilder(command + provisionedXcodeCompilerArgs).redirectErrorStream(true).redirectOutput(log).start()
            try {
                assertTrue(process.waitFor(90, TimeUnit.SECONDS)) { "Compilation probe timed out: ${log.readText()}" }
                assertEquals(0, process.exitValue()) { log.readText() }
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor()
            }
        }
    }
}
