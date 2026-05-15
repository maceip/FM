package com.google.research.air.cosmo.gatekeeping

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Closes the drift gap that remained after [GatekeepingModelLoader] / [ManifestGatekeepingModelLoader]
 * were deleted (architecture-delta C4 row 5). Adding a model now requires updating two files:
 *  - `app/src/main/assets/model-manifest.json` — used by `scripts/download-models.sh --verify-only`
 *    in CI to enforce sha256 + sizeBytes for bundled assets.
 *  - `GatekeeperModelIds.FILENAMES` (and the corresponding `const val` id) — the runtime
 *    asset-bytes loader's id → filename map.
 *
 * If the two get out of sync, BERT or AudioGatekeepingModel silently load null bytes at runtime
 * and surface as `bert_vocab_unavailable` / `audio_frontend_unavailable`. This test fails the
 * build if any manifest entry is missing from the Kotlin map (or vice versa).
 */
class GatekeeperModelManifestDriftTest {
    @Test
    fun manifestAndKotlinMapAgreeOnEveryEntry() {
        val manifest = readManifestEntries()
        val kotlinMap = enumerateKotlinFilenameMap()

        // Both directions: every manifest id → filename pair must be in the Kotlin map, and
        // vice versa. Asymmetric checks would let one side drift silently.
        assertEquals(
            "Manifest entries (id → filename) must equal GatekeeperModelIds.FILENAMES",
            manifest,
            kotlinMap
        )
    }

    private fun readManifestEntries(): Map<String, String> {
        val manifestFile = locateManifest()
        val root = JSONObject(manifestFile.readText())
        val models = root.getJSONArray("models")
        val out = mutableMapOf<String, String>()
        for (index in 0 until models.length()) {
            val entry = models.getJSONObject(index)
            val id = entry.getString("id")
            val filename = entry.getString("filename")
            check(out.put(id, filename) == null) { "duplicate model id in manifest: $id" }
        }
        return out
    }

    private fun enumerateKotlinFilenameMap(): Map<String, String> =
        // Mirror GatekeeperModelIds.FILENAMES via the public lookup; the constants list is the
        // single allowlist for runtime-loadable model ids.
        listOf(
            GatekeeperModelIds.BERT_VOCAB,
            GatekeeperModelIds.BERT_CPU,
            GatekeeperModelIds.BERT_EDGETPU_P24,
            GatekeeperModelIds.BERT_EDGETPU_P25,
            GatekeeperModelIds.BERT_NPU_MT6989,
            GatekeeperModelIds.AUDIO_FRONTEND,
            GatekeeperModelIds.AUDIO_GATEKEEPER,
            GatekeeperModelIds.INHERENT_V0,
            GatekeeperModelIds.INHERENT_V0_METADATA
        ).associateWith { id ->
            requireNotNull(GatekeeperModelIds.filenameFor(id)) {
                "GatekeeperModelIds.filenameFor($id) returned null — update FILENAMES"
            }
        }

    private fun locateManifest(): File {
        val userDir = checkNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        var current: File? = File(userDir).absoluteFile
        while (current != null) {
            val candidate = current.resolve("app/src/main/assets/model-manifest.json")
            if (candidate.isFile) return candidate
            current = current.parentFile
        }
        error("Unable to locate model-manifest.json from $userDir")
    }
}
