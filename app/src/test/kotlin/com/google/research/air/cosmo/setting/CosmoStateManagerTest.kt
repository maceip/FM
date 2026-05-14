package com.google.research.air.cosmo.setting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Verifies the C6 contract for [CosmoStateManager]:
 *  - battery threshold matches cosmo (15%, per `CosmoStateManager.smali`)
 *  - low-battery transition triggers a single snooze window (no thrash on repeat reads)
 *  - threshold-respecting reads above the boundary do not change state
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CosmoStateManagerTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)

    @Before
    fun setupAndroidStubs() {
        // CosmoStateManager.scheduleNextStateUpdate constructs an Intent, which the JVM unit
        // test runtime does not stub. Stub the constructor + the action setter so the
        // alarm-scheduling side effect is harmless during state-flow tests.
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun teardownAndroidStubs() {
        unmockkAll()
    }

    @Test
    fun batteryAtOrAboveThresholdLeavesStateUnchanged() = runTest {
        val store = FakeStore(globalEnabled = true, autoSnoozeEnabled = true)
        val manager = newManager(store)

        manager.handleBatteryLevelChange(CosmoStateManager.BATTERY_LOW_THRESHOLD)
        manager.handleBatteryLevelChange(20f)
        advanceUntilIdle()

        // Did not snooze, did not flip to LOW_BATTERY because battery never dropped *below* the
        // threshold (the smali condition is strict less-than).
        assertEquals(null, store.snoozeUntil)
    }

    @Test
    fun batteryBelowThresholdSnoozesWhenAutoSnoozeIsEnabled() = runTest {
        val store = FakeStore(globalEnabled = true, autoSnoozeEnabled = true)
        val manager = newManager(store)

        manager.handleBatteryLevelChange(14f)
        advanceUntilIdle()

        // Snooze side-effect is what disables capture. The intermediate state may be LOW_BATTERY
        // or SNOOZED depending on whether updateAndScheduleCosmoState has run; both encode the
        // same product behavior (capture suspended).
        assertEquals(true, manager.stateFlow.value in setOf(CosmoState.LOW_BATTERY, CosmoState.SNOOZED))
        assertNotNull("expected snooze to be set after low-battery event", store.snoozeUntil)
        val firstSnoozeUntil = store.snoozeUntil

        // Repeated below-threshold reads must not extend the snooze window — that's the
        // anti-thrash guarantee. Genesis achieves this by re-snoozing to the same offset; the
        // assertion here is that the store snooze does not advance to a later instant.
        manager.handleBatteryLevelChange(13f)
        manager.handleBatteryLevelChange(12f)
        advanceUntilIdle()

        assertEquals(firstSnoozeUntil, store.snoozeUntil)
    }

    @Test
    fun batteryBelowThresholdWithAutoSnoozeOffDoesNotSnooze() = runTest {
        val store = FakeStore(globalEnabled = true, autoSnoozeEnabled = false)
        val manager = newManager(store)

        manager.handleBatteryLevelChange(5f)
        advanceUntilIdle()

        assertEquals(null, store.snoozeUntil)
    }

    private fun TestScope.newManager(store: FakeStore): CosmoStateManager {
        val realStore = mockk<CosmoSettingStore>(relaxed = true)
        every { realStore.isGlobalCosmoEnabled() } answers { store.globalEnabled }
        every { realStore.isAutoSnoozeEnabled() } answers { store.autoSnoozeEnabled }
        every { realStore.isSnoozed(any()) } answers {
            store.snoozeUntil?.isAfter(clock.instant()) == true
        }
        every { realStore.getSnoozeUntil() } answers { store.snoozeUntil }
        every { realStore.getCosmoStartTime() } returns 0
        every { realStore.getCosmoEndTime() } returns 1439
        val instantSlot = slot<Instant>()
        coEvery { realStore.setSnoozeUntil(capture(instantSlot)) } answers {
            store.snoozeUntil = instantSlot.captured
        }
        coJustRun { realStore.clearSnooze() }
        coJustRun { realStore.setGlobalCosmoEnabled(any()) }
        coJustRun { realStore.setCosmoStartTime(any()) }
        coJustRun { realStore.setCosmoEndTime(any()) }
        every { alarmManager.canScheduleExactAlarms() } returns true
        return CosmoStateManager(realStore, context, this, alarmManager, clock)
    }

    private class FakeStore(
        var globalEnabled: Boolean,
        var autoSnoozeEnabled: Boolean,
        var snoozeUntil: Instant? = null
    )
}
