/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CacheFileIdTest {
    @Test
    fun stringHashCollisionsDoNotShareACacheDirectory() {
        // "Aa" and "BB" have the same String.hashCode, and so do these paths.
        val aa = "/tmp/src/Aa.kt"
        val bb = "/tmp/src/BB.kt"
        assertEquals(aa.hashCode(), bb.hashCode())
        val packageName = "p"
        assertNotEquals(CacheSupport.cacheFileId(packageName, aa), CacheSupport.cacheFileId(packageName, bb))
        assertEquals(CacheSupport.cacheFileId(packageName, aa), CacheSupport.cacheFileId(packageName, aa))
    }
}
