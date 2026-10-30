/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.facts

import org.jetbrains.kotlin.backend.common.linkage.partial.PartialLinkageSupportForLinker
import org.jetbrains.kotlin.backend.common.overrides.IrLinkerFakeOverrideProvider
import org.jetbrains.kotlin.backend.common.serialization.BasicIrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.DeserializationStrategy
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinker
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.ir.BridgesPolicy
import org.jetbrains.kotlin.backend.konan.serialization.KonanIdSignaturer
import org.jetbrains.kotlin.backend.konan.serialization.KonanManglerDesc
import org.jetbrains.kotlin.backend.konan.serialization.KonanManglerIr
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibModuleOrigin
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Computes the facts the files of a klib define without compiling it, so that they can be compared against
 * the ones stored by the previous compilation for cache invalidation.
 *
 * Only defined facts are needed for the file-level propagation gate.
 */
internal interface KlibFactsGenerator {

    companion object {
        fun from(config: NativeSecondStageCompilationConfig): KlibFactsGenerator =
                KlibFactsGeneratorImpl(config.threadsCount, config.bridgesPolicy)
    }

    fun generateFactsFrom(kotlinLibrary: KotlinLibrary): FactsTracker
}

/**
 * Deserializes the IR of the klib alone, like `klib dump-ir`, and runs [FactsCollector] on its files,
 * before any lowering.
 *
 * The dependencies of the klib are not loaded: the symbols they provide stay unbound and no fake override is built.
 * [bridgesPolicy] must be the one of the compilation the facts are compared against (see [Fact.VtableLayout]).
 */
private class KlibFactsGeneratorImpl(
        private val threadsCount: Int,
        private val bridgesPolicy: BridgesPolicy
) : KlibFactsGenerator {
    override fun generateFactsFrom(kotlinLibrary: KotlinLibrary): FactsTracker {
        val symbolTable = SymbolTable(KonanIdSignaturer(KonanManglerDesc), IrFactoryImpl)
        val moduleDescriptor = createFakeModuleDescriptor(kotlinLibrary)
        val linker = FactsIrLinker(moduleDescriptor, symbolTable)
        val irModule = linker.deserializeFullModule(moduleDescriptor, kotlinLibrary)
        linker.modulesWithReachableTopLevels.forEach(IrModuleDeserializer::deserializeReachableDeclarations)

        if (threadsCount == 1) {
            return FactsTracker().apply {
                irModule.files.forEach { irFile -> FactsCollector.collectDefsFrom(irFile, this, bridgesPolicy) }
            }
        }

        val executors = Executors.newFixedThreadPool(threadsCount)
        val futures: List<Future<FactsTracker>> = irModule.files.map { irFile ->
            executors.submit<FactsTracker> {
                FactsTracker().apply {
                    FactsCollector.collectDefsFrom(irFile, this, bridgesPolicy)
                }
            }
        }

        return try {
            val collectedDefs = futures.flatMap { it.get().definedFacts.entries }.associate { it.toPair() }
            FactsTracker(initialDefinedFacts = collectedDefs)
        } finally {
            executors.shutdownNow()
        }
    }

    private fun createFakeModuleDescriptor(library: KotlinLibrary): ModuleDescriptorImpl {
        val builtIns = object : KotlinBuiltIns(LockBasedStorageManager.NO_LOCKS) {}
        val moduleDescriptor = ModuleDescriptorImpl(
                moduleName = Name.special("<${library.uniqueName}>"),
                storageManager = LockBasedStorageManager.NO_LOCKS,
                builtIns = builtIns,
                capabilities = mapOf(KlibModuleOrigin.CAPABILITY to DeserializedKlibModuleOrigin(library)),
        )
        moduleDescriptor.setDependencies(moduleDescriptor)
        builtIns.builtInsModule = moduleDescriptor
        return moduleDescriptor
    }
}

/** A copy of `KlibToolIrLinker` from the klib tool. */
private class FactsIrLinker(module: ModuleDescriptor, symbolTable: SymbolTable) :
        KotlinIrLinker(module, symbolTable, exportedDependencies = emptyList(), errorCallback = { error(it) }) {
    override val irMangler: KotlinMangler.IrMangler = KonanManglerIr

    override val fakeOverrideBuilder = IrLinkerFakeOverrideProvider(
            linker = this,
            symbolTable = symbolTable,
            mangler = irMangler,
            friendModules = emptyMap(),
            partialLinkageSupport = PartialLinkageSupportForLinker.DISABLED,
    )

    override val returnUnboundSymbolsIfSignatureNotFound get() = true

    override fun createModuleDeserializer(
            moduleDescriptor: ModuleDescriptor,
            klib: KotlinLibrary?,
            strategyResolver: (String) -> DeserializationStrategy,
    ): IrModuleDeserializer = FactsModuleDeserializer(
            moduleDescriptor = moduleDescriptor,
            klib = klib ?: error("Expecting kotlin library for $moduleDescriptor"),
            strategyResolver = strategyResolver,
    )

    override fun isBuiltInModule(moduleDescriptor: ModuleDescriptor) = false

    private inner class FactsModuleDeserializer(
            moduleDescriptor: ModuleDescriptor,
            klib: KotlinLibrary,
            strategyResolver: (String) -> DeserializationStrategy,
    ) : BasicIrModuleDeserializer(
            linker = this,
            moduleDescriptor = moduleDescriptor,
            strategyResolver = strategyResolver,
            klib = klib,
            libraryAbiVersion = klib.versions.abiVersion ?: KotlinAbiVersion.CURRENT,
            allowErrorNodes = true,
            deserializeTypeAliases = true,
    )
}
