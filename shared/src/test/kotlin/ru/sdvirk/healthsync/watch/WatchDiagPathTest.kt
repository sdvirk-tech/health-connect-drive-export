package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDiagPathTest {
    @Test
    fun diagPathSharesHealthSyncPrefix() {
        assertTrue(WatchSync.PATH_DIAG.startsWith("/healthsync/"))
        assertTrue(WatchSync.PATH_DIAG != WatchSync.PATH_SAMPLES)
    }
}
