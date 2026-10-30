/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.facts

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal object KlibFileFacts {
    const val FILE_NAME = "klib_facts"
    private const val VERSION = 1

    fun write(path: Path, facts: Defs) {
        DataOutputStream(Files.newOutputStream(path)).use { output ->
            output.writeInt(VERSION)
            output.writeInt(facts.size)
            facts.entries.sortedWith(compareBy({ it.key.kind.id }, { it.key.key.signatureHash })).forEach { [key, hash] ->
                output.writeInt(key.kind.id)
                output.writeLong(key.key.signatureHash.toLong())
                output.writeLong(hash.toLong())
            }
        }
    }

    // Missing, old or incomplete snapshots retain the original conservative propagation.
    fun read(path: Path): Defs? = try {
        DataInputStream(Files.newInputStream(path)).use { input ->
            if (input.readInt() != VERSION) return null
            val count = input.readInt()
            require(count >= 0 && Files.size(path) == 8L + count * 20L)
            buildMap {
                repeat(count) {
                    val kindId = input.readInt()
                    val kind = FactKind.entries.singleOrNull { it.id == kindId } ?: return null
                    val key = FactKey(kind, SymbolKey(input.readLong().toULong()))
                    require(put(key, input.readLong().toULong()) == null)
                }
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
