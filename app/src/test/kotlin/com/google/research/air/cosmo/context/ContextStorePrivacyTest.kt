package com.google.research.air.cosmo.context

import com.google.research.air.cosmo.setting.CosmoSettingStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextStorePrivacyTest {
    @Test
    fun disabledSourceIsRejectedBeforeDaoInsert() = runTest {
        val dao = mockk<ContextEventDao>(relaxed = true)
        val settings = mockk<CosmoSettingStore>(relaxed = true)
        every { settings.isScreenContextEnabled() } returns false
        val store = RoomContextStore(dao, settings)

        val result =
            store.insert(
                ContextEvent(
                    id = "screen-1",
                    source = Source.SCREEN,
                    timestampMs = 1_746_000_000_000L,
                    text = "disabled source text",
                    blobRef = null,
                    embedding = null,
                    retentionClass = RetentionClass.EPHEMERAL,
                    sourceMetadata = emptyMap()
                )
            )

        assertEquals(InsertResult.Rejected(RejectReason.SOURCE_DISABLED), result)
        coVerify(exactly = 0) { dao.insertWithFts(any()) }
        coVerify(exactly = 0) { dao.insertEvent(any()) }
        coVerify(exactly = 0) { dao.insertFts(any()) }
    }
}
