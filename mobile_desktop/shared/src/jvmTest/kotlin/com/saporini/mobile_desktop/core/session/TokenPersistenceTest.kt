package com.saporini.mobile_desktop.core.session

import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenPersistenceTest {
    @Test
    fun clearingSessionTokensKeepsPendingInventoryRetry() = runTest {
        val previousHome = System.getProperty("user.home")
        val tempHome = createTempDirectory("saporini-token-test-").toFile()
        try {
            System.setProperty("user.home", tempHome.absolutePath)
            val persistence = TokenPersistence()
            persistence.saveAccessToken("access")
            persistence.saveRefreshToken("refresh")
            persistence.savePendingInventoryMoves("[{\"requestKey\":\"retry-key\"}]")

            persistence.clear()

            assertNull(persistence.loadAccessToken())
            assertNull(persistence.loadRefreshToken())
            assertEquals("[{\"requestKey\":\"retry-key\"}]", persistence.loadPendingInventoryMoves())
        } finally {
            if (previousHome == null) System.clearProperty("user.home") else System.setProperty("user.home", previousHome)
            tempHome.deleteRecursively()
        }
    }
}
