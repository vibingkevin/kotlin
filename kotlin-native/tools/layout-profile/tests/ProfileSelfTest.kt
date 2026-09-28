package org.jetbrains.kotlin.backend.konan.llvm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val p = LayoutQueryProfile
    // 两层查询嵌套：根查询只计一次，异常必须恢复深度。
    p.measure(0) { p.measure(1) { check(2 + 2 == 4) } }
    try { p.measure(2) { error("expected") } } catch (_: IllegalStateException) {}
    p.measure(3) { }
    val map = mutableMapOf<String, Int>()
    check(p.getOrPut(48, map, "one") { 1 } == 1)
    check(p.getOrPut(48, map, "one") { error("cached") } == 1)
    try { p.getOrPut(48, map, "retry") { error("expected") } } catch (_: IllegalStateException) {}
    check(p.getOrPut(48, map, "retry") { 2 } == 2)
    val retries = AtomicInteger()
    val flaky = lazy { check(retries.incrementAndGet() > 1); 7 }
    try { p.lazyValue(50, flaky) } catch (_: IllegalStateException) {}
    check(p.lazyValue(50, flaky) == 7)
    check(p.lazyValue(50, flaky) == 7)
    val initialized = AtomicInteger()
    val shared = lazy { initialized.incrementAndGet() }
    val start = CountDownLatch(1)
    val workers = List(8) { Thread {
        start.await()
        repeat(1000) { check(p.lazyValue(51, shared) == 1); p.measure(4) {} }
    }.also { it.start() } }
    start.countDown(); workers.forEach { it.join() }
    check(initialized.get() == 1)
    if (p.enabled) {
        val s = p.local.get()
        check(s.queryRoots[0] == 1L && s.queryRoots[1] == 0L)
        check(s.queryRoots[2] == 1L && s.queryRoots[3] == 1L)
        check(s.hits[48] == 1L && s.misses[48] == 3L)
        check(s.hits[50] == 1L && s.misses[50] == 2L)
        check(s.queryDepth == 0)
        check(s.exclusive.all { it >= 0 })
    }
    println("PROFILE_SELF_TEST_OK")
}
