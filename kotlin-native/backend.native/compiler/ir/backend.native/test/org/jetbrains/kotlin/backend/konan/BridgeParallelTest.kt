/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlin.test.Test
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.lang.reflect.Modifier
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.backend.konan.ir.BridgesPolicy
import org.jetbrains.kotlin.backend.konan.ir.OverriddenFunctionInfo
import org.jetbrains.kotlin.backend.konan.lower.BridgesSupport
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class BridgeParallelTest {
    @Test
    fun parallelVtablePublishesOneBridge() {
        val file = uninitialized(IrFileImpl::class.java) as IrFileImpl
        file.packageFqName = FqName("kotlin")
        val unit = IrFactoryImpl.buildClass { name = Name.identifier("Unit") }.withThisReceiver()
        unit.parent = file
        val box = IrFactoryImpl.buildClass { name = Name.identifier("Box") }.withThisReceiver()
        box.parent = file
        val marker = IrFactoryImpl.buildClass { name = Name.identifier("TransparentForDebugger") }.withThisReceiver()
        marker.addConstructor { isPrimary = true }
        val symbols = uninitialized(BackendNativeSymbols::class.java) as BackendNativeSymbols
        BackendNativeSymbols::class.java.getDeclaredField("transparentForDebugger").apply { isAccessible = true }
            .set(symbols, marker.symbol)
        val bridges = uninitialized(BridgesSupport::class.java) as BridgesSupport
        BridgesSupport::class.java.getDeclaredField("irFactory").apply { isAccessible = true }.set(bridges, IrFactoryImpl)
        BridgesSupport::class.java.getDeclaredField("symbols").apply { isAccessible = true }.set(bridges, symbols)
        BridgesSupport::class.java.getDeclaredField("irBuiltIns").apply { isAccessible = true }.set(bridges, emptyBuiltIns())
        check(unit.defaultType.isUnit())
        raceOnSharedIr {
            val function = IrFactoryImpl.buildFun {
                name = Name.identifier("adapted")
                returnType = box.defaultType
            }.apply { parent = box }
            val overridden = IrFactoryImpl.buildFun {
                name = Name.identifier("original")
                returnType = unit.defaultType
            }.apply { parent = box }
            val info = OverriddenFunctionInfo(function, overridden, BridgesPolicy.BOX_UNBOX_ONLY)
            check(info.needBridge) { "expected a box/unbox bridge for Unit vs reference" }
            return@raceOnSharedIr { bridges.getBridge(info) }
        }
    }
}

private fun emptyBuiltIns(): IrBuiltIns {
    val internalName = "org/jetbrains/kotlin/backend/konan/EmptyBuiltIns"
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, Type.getInternalName(IrBuiltIns::class.java), null)
    val constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    constructor.visitCode()
    constructor.visitVarInsn(Opcodes.ALOAD, 0)
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(IrBuiltIns::class.java), "<init>", "()V", false)
    constructor.visitInsn(Opcodes.RETURN)
    constructor.visitMaxs(0, 0)
    constructor.visitEnd()
    val seen = mutableSetOf<String>()
    for (method in IrBuiltIns::class.java.methods) {
        if (!Modifier.isAbstract(method.modifiers)) continue
        val descriptor = Type.getMethodDescriptor(method)
        if (!seen.add(method.name + descriptor)) continue
        val stub = writer.visitMethod(Opcodes.ACC_PUBLIC, method.name, descriptor, null, null)
        stub.visitCode()
        when (method.returnType) {
            Void.TYPE -> stub.visitInsn(Opcodes.RETURN)
            java.lang.Boolean.TYPE, java.lang.Integer.TYPE, java.lang.Byte.TYPE, java.lang.Short.TYPE, java.lang.Character.TYPE -> {
                stub.visitInsn(Opcodes.ICONST_0)
                stub.visitInsn(Opcodes.IRETURN)
            }
            java.lang.Long.TYPE -> {
                stub.visitInsn(Opcodes.LCONST_0)
                stub.visitInsn(Opcodes.LRETURN)
            }
            java.lang.Float.TYPE -> {
                stub.visitInsn(Opcodes.FCONST_0)
                stub.visitInsn(Opcodes.FRETURN)
            }
            java.lang.Double.TYPE -> {
                stub.visitInsn(Opcodes.DCONST_0)
                stub.visitInsn(Opcodes.DRETURN)
            }
            else -> {
                stub.visitInsn(Opcodes.ACONST_NULL)
                stub.visitInsn(Opcodes.ARETURN)
            }
        }
        stub.visitMaxs(0, 0)
        stub.visitEnd()
    }
    writer.visitEnd()
    val bytes = writer.toByteArray()
    val loader = object : ClassLoader(IrBuiltIns::class.java.classLoader) {
        fun define(): Class<*> = defineClass(internalName.replace('/', '.'), bytes, 0, bytes.size)
    }
    return loader.define().getDeclaredConstructor().newInstance() as IrBuiltIns
}

fun main() {
    BridgeParallelTest().parallelVtablePublishesOneBridge()
    println("PASS")
}
