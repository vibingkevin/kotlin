/*
 * Copyright 2010-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
package org.jetbrains.kotlin.native.interop.indexer

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** 进程级实验统计；单轮使用独立 JVM。Kotlin 与 native 耗时不可相加。 */
internal object LayoutQueryProfile {
    @PublishedApi internal val directory = System.getenv("KN_LAYOUT_PROFILE_DIR")
    @PublishedApi internal val enabled = directory != null && System.getenv("KN_LAYOUT_PROFILE") in setOf("counts", "time")
    @PublishedApi internal val timed = enabled && System.getenv("KN_LAYOUT_PROFILE") == "time"
    private val labels = arrayOf("kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:331:clang_Type_getSizeOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:332:clang_Type_getAlignOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:382:clang_Type_getOffsetOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:391:clang_Type_getSizeOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:392:clang_Type_getAlignOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:395:clang_getFieldDeclBitWidth",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:650:clang_Type_getSizeOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:723:clang_Type_getSizeOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:724:clang_Type_getSizeOf",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:805:clang_getArraySize",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Utils.kt:82:clang_Type_getSizeOf",
        "Indexer.structRegistry",
        "Indexer.enumRegistry",
        "Indexer.objCClassRegistry",
        "Indexer.objCProtocolRegistry",
        "Indexer.objCCategoryById",
        "Indexer.typedefRegistry",
        "kotlin-native/Interop/Indexer/src/main/kotlin/org/jetbrains/kotlin/native/interop/indexer/Indexer.kt:725:clang_getNumElements")
    @PublishedApi internal val querySites = booleanArrayOf(true, true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false, true)
    @PublishedApi internal class State {
        val calls = LongArray(18)
        val inclusive = LongArray(18)
        val exclusive = LongArray(18)
        val hits = LongArray(18)
        val misses = LongArray(18)
        val queryRoots = LongArray(18)
        val queryNanos = LongArray(18)
        var queryDepth = 0
        val errors = LongArray(18)
        // 所有已计时子调用的累计时间，用差值计算当前窗口的独占时间。
        var children = 0L
    }
    private val states = ConcurrentLinkedQueue<State>()
    @PublishedApi internal val local = ThreadLocal.withInitial { State().also { states.add(it) } }
    init {
        if (enabled) java.lang.Runtime.getRuntime().addShutdownHook(Thread({ dump() }, "layout-profile-cinterop"))
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
        File(folder, "cinterop-$pid-${System.identityHashCode(this)}.tsv").writeText(out.toString())
    }
}
