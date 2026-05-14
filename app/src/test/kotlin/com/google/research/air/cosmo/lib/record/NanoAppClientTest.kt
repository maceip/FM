package com.google.research.air.cosmo.lib.record

import android.app.Application
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the production-side seams on [NanoAppClient].
 *
 * The Context Hub binding itself can't be exercised in JVM unit tests — `ContextHubManager`
 * is `@SystemApi` and absent from the public SDK + missing on host. These tests verify the
 * surfaces that do not depend on the platform: listener forwarding, message dispatch, and the
 * "AOC unavailable" fallback path that `RecordServiceManager` relies on.
 */
class NanoAppClientTest {

    private lateinit var scheduler: ScheduledExecutorService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        scheduler = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun tearDown() {
        scheduler.shutdownNow()
        unmockkStatic(Log::class)
    }

    @Test
    fun startRecordingReturnsFalseWhenContextHubUnavailable() {
        // Application returns null for the contexthub system service in unit tests — the JVM
        // doesn't have a real platform. NanoAppClient must report unavailable and stay quiet
        // so RecordServiceManager falls back to mic.
        val app = mockk<Application>(relaxed = true)
        every { app.getSystemService("contexthub") } returns null
        val client = NanoAppClient(app, scheduler)

        assertFalse(client.startRecording())
    }

    @Test
    fun deliverMessageForwardsBytesToListener() {
        val app = mockk<Application>(relaxed = true)
        every { app.getSystemService("contexthub") } returns null
        val client = NanoAppClient(app, scheduler)
        val captured = mutableListOf<ByteArray>()
        client.recordListener =
            object : RecordServiceListener {
                override fun onBytesReceived(bytes: ByteArray) {
                    captured += bytes
                }
                override fun onStopRecording(error: Boolean) = Unit
            }

        val payload = byteArrayOf(1, 2, 3, 4)
        client.deliverMessageForTest(payload, messageType = 0)

        assertEquals(1, captured.size)
        assertEquals(payload.toList(), captured[0].toList())
    }

    @Test
    fun stopRecordingSignalsListenerEvenWhenContextHubUnavailable() {
        val app = mockk<Application>(relaxed = true)
        every { app.getSystemService("contexthub") } returns null
        val client = NanoAppClient(app, scheduler)
        var stoppedWith: Boolean? = null
        client.recordListener =
            object : RecordServiceListener {
                override fun onBytesReceived(bytes: ByteArray) = Unit
                override fun onStopRecording(error: Boolean) {
                    stoppedWith = error
                }
            }

        client.stopRecording()

        // When the Context Hub call cannot succeed, the listener is told the stop did not
        // actually transmit (error=false from the false return). The contract is that the
        // listener is notified either way so it can release its capture state.
        assertEquals(false, stoppedWith)
    }
}
