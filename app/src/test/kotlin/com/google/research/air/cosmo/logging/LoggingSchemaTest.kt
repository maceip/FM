package com.google.research.air.cosmo.logging

import java.io.File
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingSchemaTest {
    @Test
    fun genesisObservabilitySchemaExposesRecoveredSurfaceCounts() {
        assertEquals(78, GenesisObservabilitySchema.logTags.size)
        assertEquals(309, GenesisObservabilitySchema.floggerEvents.size)
        assertEquals(309, GenesisObservabilitySchema.firebaseEvents.size)
    }

    @Test
    fun floggerDerivedFirebaseSchemaUsesLifecycleShape() {
        GenesisObservabilitySchema.firebaseEvents.forEach { event ->
            assertTrue(event.name.length <= 40)
            assertTrue(event.requiredParams.contains("surface"))
            assertTrue(event.requiredParams.contains("signal"))
            assertTrue(event.requiredParams.contains("result"))
        }
    }

    @Test
    fun telemetryFirebaseCatalogMatchesTelemetryEventSurface() {
        val defs = GenesisObservabilitySchema.telemetryFirebaseEvents
        assertEquals(defs.distinctBy { it.name }.size, defs.size)
        defs.forEach { event ->
            assertTrue(event.name.length <= 40)
            assertTrue(TELEMETRY_EVENT_FIREBASE_NAMES.contains(event.name))
        }
        assertEquals(TELEMETRY_EVENT_FIREBASE_NAMES.size, defs.size)
    }

    /**
     * Compile-enforced pairing: every [TelemetryEvent] sealed subclass binds a
     * [FirebaseEventDefinition] in its constructor (the constructor signature requires it,
     * so missing the registration fails to compile). This test verifies that the
     * Firebase registry count matches the sealed surface, that every subclass exposes a
     * `DEFINITION` companion-object field, and that the `eventName` an instance reports
     * matches the registered definition (catches accidental name drift).
     */
    @Test
    fun everyTelemetryEventSubclassHasMatchingRegisteredDefinition() {
        val subclasses = TelemetryEvent::class.sealedSubclasses
        val defs = GenesisObservabilitySchema.telemetryFirebaseEvents
        assertEquals(subclasses.size, defs.size)
        val registeredNames = defs.map { it.name }.toSet()
        subclasses.forEach { subclass ->
            val companion = subclass.companionObjectInstance
                ?: error("TelemetryEvent subclass ${subclass.simpleName} is missing a companion object exposing DEFINITION")
            val property = subclass.companionObject?.declaredMemberProperties?.firstOrNull { it.name == "DEFINITION" }
                ?: error("${subclass.simpleName} companion object does not declare a DEFINITION property")
            val def = property.getter.call(companion) as FirebaseEventDefinition
            assertTrue(
                "${subclass.simpleName}.DEFINITION (${def.name}) is not registered in TELEMETRY_FIREBASE_EVENT_DEFINITIONS",
                registeredNames.contains(def.name)
            )
        }
    }

    @Test
    fun telemetryProvenanceCoversEveryAllowedFirebaseEvent() {
        val provenance = GenesisObservabilitySchema.telemetryProvenance
        assertEquals(TELEMETRY_EVENT_FIREBASE_NAMES, provenance.map { it.eventName }.toSet())
        assertEquals(provenance.distinctBy { it.eventName }.size, provenance.size)
        assertTrue(provenance.none { it.containsUserContent })
        provenance.forEach { event ->
            assertTrue(event.owner in GenesisObservabilitySchema.logTags)
            assertTrue(event.sourcePath.startsWith("app/src/main/kotlin/"))
            assertTrue(event.emittedFrom.isNotBlank())
            val sourceFile = repoRoot().resolve(event.sourcePath)
            assertTrue("Missing telemetry source file: ${event.sourcePath}", sourceFile.isFile)
            val source = sourceFile.readText()
            val sourceOwner = event.emittedFrom.substringBefore(".")
            val sourceMethod = event.emittedFrom.substringAfter(".")
            assertTrue("${event.emittedFrom} owner missing from ${event.sourcePath}", source.contains(sourceOwner))
            assertTrue("${event.emittedFrom} method missing from ${event.sourcePath}", source.contains(sourceMethod))
        }
    }

    @Test
    fun firebaseAnalyticsIsOnlyUsedBehindLoggingBoundary() {
        val root = repoRoot().resolve("app/src/main/kotlin")
        val offenders =
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "Logging.kt" }
                .filter { file ->
                    val text = file.readText()
                    text.contains("FirebaseAnalytics") || text.contains(".logEvent(")
                }
                .map { it.relativeTo(repoRoot()).path }
                .toList()

        assertTrue("Firebase analytics bypasses logging boundary: $offenders", offenders.isEmpty())
    }
}

/** Must stay in sync with every [TelemetryEvent] subclass Firebase name (allowlist). */
private val TELEMETRY_EVENT_FIREBASE_NAMES =
    setOf(
        "fulfillment_run",
        "fulfillment_action_execution",
        "task_monitor_message",
        "task_monitor_surface",
        "fulfillment_action_parse_rejected",
        "gatekeeper_run",
        "gatekeeper_directedness_gate",
        "calendar_writable_fallback",
        "glass_session"
    )

private fun repoRoot(): File {
    val userDir = checkNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
    var current: File? = File(userDir).absoluteFile
    while (current != null) {
        if (current.resolve("app/src/main/kotlin").isDirectory) return current
        current = current.parentFile
    }
    error("Unable to locate repository root from $userDir")
}
