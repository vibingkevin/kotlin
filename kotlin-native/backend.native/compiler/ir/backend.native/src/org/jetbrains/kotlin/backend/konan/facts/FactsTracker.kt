/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.facts

import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.path

internal typealias FactHash = ULong
internal typealias FileName = String
internal typealias Defs = Map<FactKey, FactHash>
internal typealias DefsAssociation = Map<FileName, Defs>

internal class FactsTracker(initialDefinedFacts: DefsAssociation = emptyMap()) {
    private val facts = initialDefinedFacts.entries.associateTo(mutableMapOf()) { [file, definitions] ->
        file to definitions.toMutableMap()
    }

    val definedFacts: DefsAssociation get() = facts

    fun defines(definee: IrFile, newFact: Fact) {
        facts.getOrPut(definee.path) { mutableMapOf() }[newFact.factKey] = newFact.contentHash()
    }
}
