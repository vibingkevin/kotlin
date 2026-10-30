/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.facts

import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KlibFileFactsTest {
    private val facts: Defs = FactKind.entries.associate { kind ->
        FactKey(kind, SymbolKey(ULong.MAX_VALUE - kind.id.toULong())) to kind.id.toULong()
    }

    @Test
    fun roundTrip() = withSnapshot { path ->
        KlibFileFacts.write(path, facts)
        assertEquals(facts, KlibFileFacts.read(path))
    }

    @Test
    fun emptyRoundTrip() = withSnapshot { path ->
        KlibFileFacts.write(path, emptyMap())
        assertEquals(emptyMap(), KlibFileFacts.read(path))
    }

    @Test
    fun insertionOrderDoesNotChangeBytes() = withSnapshot { path ->
        val sameKind = facts + mapOf(
                FactKey(FactKind.SIGNATURE, SymbolKey(0UL)) to ULong.MAX_VALUE,
                FactKey(FactKind.SIGNATURE, SymbolKey(Long.MAX_VALUE.toULong())) to 1UL,
        )
        KlibFileFacts.write(path, sameKind)
        val expected = Files.readAllBytes(path)
        KlibFileFacts.write(path, sameKind.entries.reversed().associate { it.toPair() })
        assertContentEquals(expected, Files.readAllBytes(path))
    }

    @Test
    fun missingSnapshotReturnsNull() = withSnapshot { path ->
        assertNull(KlibFileFacts.read(path))
    }

    @Test
    fun truncatedSnapshotReturnsNull() = withSnapshot { path ->
        KlibFileFacts.write(path, facts)
        val complete = Files.readAllBytes(path)
        for (length in complete.indices) {
            Files.write(path, complete.copyOf(length))
            assertNull(KlibFileFacts.read(path), "Accepted a $length-byte prefix of a ${complete.size}-byte snapshot")
        }
    }

    @Test
    fun unknownVersionReturnsNull() = withSnapshot { path ->
        DataOutputStream(Files.newOutputStream(path)).use { output ->
            output.writeInt(Int.MAX_VALUE)
            output.writeInt(0)
        }
        assertNull(KlibFileFacts.read(path))
    }

    @Test
    fun invalidKindReturnsNull() = withSnapshot { path ->
        for (kindId in listOf(-1, Int.MAX_VALUE)) {
            DataOutputStream(Files.newOutputStream(path)).use { output ->
                output.writeInt(1)
                output.writeInt(1)
                output.writeInt(kindId)
                output.writeLong(1)
                output.writeLong(2)
            }
            assertNull(KlibFileFacts.read(path))
        }
    }

    @Test
    fun duplicateKeyReturnsNull() = withSnapshot { path ->
        DataOutputStream(Files.newOutputStream(path)).use { output ->
            output.writeInt(1)
            output.writeInt(2)
            repeat(2) {
                output.writeInt(FactKind.SIGNATURE.id)
                output.writeLong(1)
                output.writeLong(2)
            }
        }
        assertNull(KlibFileFacts.read(path))
    }

    @Test
    fun negativeCountReturnsNull() = withSnapshot { path ->
        DataOutputStream(Files.newOutputStream(path)).use { output ->
            output.writeInt(1)
            output.writeInt(-1)
        }
        assertNull(KlibFileFacts.read(path))
    }

    @Test
    fun trailingBytesReturnNull() = withSnapshot { path ->
        KlibFileFacts.write(path, facts)
        Files.write(path, Files.readAllBytes(path) + byteArrayOf(0))
        assertNull(KlibFileFacts.read(path))
    }

    private fun withSnapshot(action: (Path) -> Unit) {
        val directory = Files.createTempDirectory("klib-facts-test")
        try {
            action(directory.resolve(KlibFileFacts.FILE_NAME))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
