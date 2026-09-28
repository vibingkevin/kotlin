/*
 * Copyright 2010-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
package org.jetbrains.kotlin.backend.konan.llvm

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** 进程级实验统计；单轮使用独立 JVM。Kotlin 与 native 耗时不可相加。 */
internal object LayoutQueryProfile {
    @PublishedApi internal val directory = System.getenv("KN_LAYOUT_PROFILE_DIR")
    @PublishedApi internal val enabled = directory != null && System.getenv("KN_LAYOUT_PROFILE") in setOf("counts", "time")
    @PublishedApi internal val timed = enabled && System.getenv("KN_LAYOUT_PROFILE") == "time"
    private val labels = arrayOf("kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/ir/ClassLayoutBuilder.kt:275:LLVMABIAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/ir/ClassLayoutBuilder.kt:278:LLVMABISizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/ir/ClassLayoutBuilder.kt:470:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/CodeGenerator.kt:50:LLVMIntPtrTypeInContext",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/CodeGenerator.kt:425:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/CodeGenerator.kt:489:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/CodeGenerator.kt:516:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/CodeGenerator.kt:535:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/CodeGenerator.kt:613:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/ContextUtils.kt:518:LLVMIntPtrTypeInContext",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/ContextUtils.kt:537:LLVMGetArrayLength",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/ContextUtils.kt:629:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/DebugUtils.kt:155:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/DebugUtils.kt:156:LLVMPreferredAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/DebugUtils.kt:158:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/DebugUtils.kt:159:LLVMPreferredAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/DebugUtils.kt:241:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/DebugUtils.kt:242:LLVMPreferredAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IntrinsicGenerator.kt:70:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IntrinsicGenerator.kt:377:LLVMPointerSize",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IntrinsicGenerator.kt:541:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IntrinsicGenerator.kt:542:LLVMSizeOfTypeInBits",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IrToBitcode.kt:689:LLVMPreferredAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IrToBitcode.kt:1361:LLVMGetIntTypeWidth",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/IrToBitcode.kt:1362:LLVMGetIntTypeWidth",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:94:LLVMABIAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:102:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:123:LLVMOffsetOfElement",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:125:LLVMABIAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:127:LLVMABISizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:240:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:310:LLVMOffsetOfElement",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmDeclarations.kt:405:LLVMOffsetOfElement",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/RTTIGenerator.kt:194:LLVMABISizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/RTTIGenerator.kt:195:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/RTTIGenerator.kt:280:LLVMOffsetOfElement",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/RTTIGenerator.kt:412:LLVMGetArrayLength",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/RTTIGenerator.kt:444:LLVMOffsetOfElement",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/RTTIGenerator.kt:506:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/Runtime.kt:107:LLVMABISizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/Runtime.kt:108:LLVMABIAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/Runtime.kt:109:LLVMOffsetOfElement",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/Runtime.kt:121:LLVMByteOrder",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/objc/ObjCDataGenerator.kt:109:LLVMABISizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/objc/ObjCDataGenerator.kt:192:LLVMABIAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/objc/ObjCDataGenerator.kt:228:LLVMABIAlignmentOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/objcexport/BlockPointerSupport.kt:250:LLVMStoreSizeOfType",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/objcexport/ObjCExportCodeGenerator.kt:1871:LLVMStoreSizeOfType",
        "IrType.toLLVMType",
        "ClassLayoutBuilder.fields",
        "Runtime.pointerSize",
        "Runtime.pointerAlignment",
        "Runtime.stringHeaderExtraSize",
        "Runtime.isBigEndian",
        "DebugInfo.llvmTypeSize",
        "DebugInfo.llvmTypeAlignment",
        "DebugInfo.types",
        "NativeBackendContext.layoutBuilder.metadata",
        "NativeBackendContext.layoutBuilder.attribute",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/ContextUtils.kt:535:LLVMCountStructElementTypes",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/ContextUtils.kt:539:LLVMIsPackedStruct",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmUtils.kt:75:LLVMCountStructElementTypes",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmUtils.kt:76:LLVMCountStructElementTypes",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/LlvmUtils.kt:274:LLVMCountStructElementTypes",
        "kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/llvm/Runtime.kt:115:LLVMCountStructElementTypes")
    @PublishedApi internal val querySites = booleanArrayOf(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true)
    @PublishedApi internal class State {
        val calls = LongArray(65)
        val inclusive = LongArray(65)
        val exclusive = LongArray(65)
        val hits = LongArray(65)
        val misses = LongArray(65)
        val queryRoots = LongArray(65)
        val queryNanos = LongArray(65)
        var queryDepth = 0
        val errors = LongArray(65)
        // 所有已计时子调用的累计时间，用差值计算当前窗口的独占时间。
        var children = 0L
    }
    private val states = ConcurrentLinkedQueue<State>()
    @PublishedApi internal val local = ThreadLocal.withInitial { State().also { states.add(it) } }
    init {
        if (enabled) java.lang.Runtime.getRuntime().addShutdownHook(Thread({ dump() }, "layout-profile-backend"))
    }
    inline fun <T> measure(id: Int, body: () -> T): T {
        if (!enabled) return body()
        val s = local.get()
        s.calls[id]++
        val query = querySites[id]
        val rootQuery = query && s.queryDepth++ == 0
        if (rootQuery) s.queryRoots[id]++
        if (!timed) {
            try { return body() } catch (t: Throwable) { s.errors[id]++; throw t } finally { if (query) s.queryDepth-- }
        }
        val beforeChildren = s.children
        val start = System.nanoTime()
        try { return body() } catch (t: Throwable) { s.errors[id]++; throw t } finally {
            val elapsed = System.nanoTime() - start
            if (query) s.queryDepth--
            if (rootQuery) s.queryNanos[id] += elapsed
            val child = s.children - beforeChildren
            s.inclusive[id] += elapsed
            s.exclusive[id] += elapsed - child
            // 向父窗口只传播本次完整时间，不能重复传播孙调用。
            s.children = beforeChildren + elapsed
        }
    }
    fun cache(id: Int, hit: Boolean) {
        if (!enabled) return
        val s = local.get()
        if (hit) s.hits[id]++ else s.misses[id]++
    }
    inline fun <K, V : Any> getOrPut(id: Int, map: MutableMap<K, V>, key: K, body: () -> V): V = measure(id) {
        val value = map[key]
        cache(id, value != null)
        if (value != null) value else body().also { map[key] = it }
    }
    fun <K, V : Any> getOrDefault(id: Int, map: Map<K, V>, key: K, fallback: V): V = measure(id) {
        val value = map[key]
        cache(id, value != null)
        value ?: fallback
    }
    fun <T> lazyValue(id: Int, value: Lazy<T>): T {
        if (!enabled) return value.value
        // 默认 lazy 本身以此对象为锁；在同一临界区观察真实初始化状态。
        return synchronized(value) {
            measure(id) { cache(id, value.isInitialized()); value.value }
        }
    }
    inline fun <T> cached(id: Int, body: (miss: () -> Unit) -> T): T = measure(id) {
        var missed = false
        val value = body { check(!missed); missed = true; cache(id, false) }
        if (!missed) cache(id, true)
        value
    }
    private fun dump() {
        val folder = File(directory!!)
        folder.mkdirs()
        val out = StringBuilder("id\tlabel\tcalls\tinclusive_ns\texclusive_ns\thits\tmisses\terrors\tquery_root_calls\tquery_root_ns\n")
        for (i in labels.indices) {
            out.append("$i\t${labels[i]}\t${states.sumOf { it.calls[i] }}\t${states.sumOf { it.inclusive[i] }}\t${states.sumOf { it.exclusive[i] }}\t${states.sumOf { it.hits[i] }}\t${states.sumOf { it.misses[i] }}\t${states.sumOf { it.errors[i] }}\t${states.sumOf { it.queryRoots[i] }}\t${states.sumOf { it.queryNanos[i] }}\n")
        }
        out.append("#mode\t${if (timed) "time" else "counts"}\n#threads\t${states.size}\n")
        val pid = java.lang.management.ManagementFactory.getRuntimeMXBean().name.substringBefore('@')
        File(folder, "backend-$pid-${System.identityHashCode(this)}.tsv").writeText(out.toString())
    }
}
