package com.vibepad.keyboard.macro

import android.content.res.AssetManager
import android.util.Log

/**
 * Android entry point that wires the pure [ProfileLoader] up to the APK asset tree.
 *
 * Scans `assets/profiles/` for every `.json` file, loads each through [ProfileLoader],
 * and returns both the valid profiles and any diagnostics so the UI can expose loader
 * issues in the "诊断" panel (see spec `macro-library` §"启动期发现破损 profile").
 */
class AssetProfileSource(
    private val assets: AssetManager,
    private val loader: ProfileLoader = ProfileLoader(),
) {

    /** Result of scanning the asset dir — both successes and failures. */
    data class ScanOutcome(
        val profiles: List<Profile>,
        val diagnostics: List<Diagnostic>,
    )

    data class Diagnostic(val assetPath: String, val message: String)

    fun loadAll(): ScanOutcome {
        val profiles = mutableListOf<Profile>()
        val diagnostics = mutableListOf<Diagnostic>()
        val files = try {
            assets.list(ASSET_DIR)?.filter { it.endsWith(".json", ignoreCase = true) }.orEmpty()
        } catch (e: Exception) {
            diagnostics += Diagnostic(ASSET_DIR, "Cannot list profile assets: ${e.message}")
            return ScanOutcome(profiles, diagnostics)
        }
        files.forEach { name ->
            val path = "$ASSET_DIR/$name"
            try {
                assets.open(path).use { stream ->
                    when (val res = loader.load(stream)) {
                        is ProfileLoader.Result.Ok -> profiles += res.profile
                        is ProfileLoader.Result.Invalid -> {
                            res.issues.forEach { issue ->
                                diagnostics += Diagnostic(path, "${issue.path}: ${issue.message}")
                            }
                        }
                        is ProfileLoader.Result.UnknownSchema -> diagnostics += Diagnostic(
                            path, "Unknown schema major version ${res.majorSeen} — skipping",
                        )
                        is ProfileLoader.Result.MalformedJson -> diagnostics += Diagnostic(
                            path, "Malformed JSON: ${res.message}",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read profile $path", e)
                diagnostics += Diagnostic(path, "IO error: ${e.message}")
            }
        }
        return ScanOutcome(profiles, diagnostics)
    }

    companion object {
        private const val TAG = "VibePad/Profiles"
        internal const val ASSET_DIR = "profiles"
    }
}
