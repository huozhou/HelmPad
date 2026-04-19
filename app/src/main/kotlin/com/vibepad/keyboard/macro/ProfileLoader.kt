package com.vibepad.keyboard.macro

import com.vibepad.keyboard.input.InputAction
import com.vibepad.keyboard.input.LiteralTokenizer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Loads and validates [Profile]s from JSON streams.
 *
 * Pure Kotlin — no Android dependencies. The Android layer provides the streams via
 * `AssetManager.open(...)`; tests pass in-memory `ByteArrayInputStream`s.
 *
 * Validation happens eagerly at load time so a broken bundled profile is discovered
 * immediately, not on the first button press.
 */
class ProfileLoader(
    private val json: Json = DefaultJson,
) {

    /** Structured outcome — either a valid profile or a diagnostics envelope. */
    sealed interface Result {
        data class Ok(val profile: Profile) : Result
        data class Invalid(val issues: List<Issue>) : Result
        data class UnknownSchema(val majorSeen: Int) : Result
        data class MalformedJson(val message: String) : Result
    }

    /** A single validation problem. Includes a [path] for diagnostics UI. */
    data class Issue(val path: String, val message: String)

    fun load(stream: InputStream): Result {
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return loadFromString(text)
    }

    fun loadFromString(text: String): Result {
        val profile = try {
            json.decodeFromString(Profile.serializer(), text)
        } catch (se: SerializationException) {
            return Result.MalformedJson(se.message ?: "Malformed profile JSON")
        } catch (ise: IllegalArgumentException) {
            return Result.MalformedJson(ise.message ?: "Malformed profile JSON")
        }
        val major = parseMajorVersion(profile.schemaVersion)
            ?: return Result.Invalid(listOf(Issue("schemaVersion", "Not a semver string: ${profile.schemaVersion}")))
        if (major != Profile.CURRENT_SCHEMA_MAJOR) return Result.UnknownSchema(major)
        val issues = validate(profile)
        return if (issues.isEmpty()) Result.Ok(profile) else Result.Invalid(issues)
    }

    /**
     * Returns all issues found. Empty list = profile is valid.
     *
     * Checks:
     *  - Non-empty id / labels / iconRef on every macro.
     *  - Unique macro ids.
     *  - Exactly [Profile.SLOT_COUNT] slots.
     *  - Every `Literal` action's text is ASCII-tokenizable.
     */
    internal fun validate(profile: Profile): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (profile.id.isBlank()) issues += Issue("id", "Profile id must not be blank")
        if (profile.name.isBlank()) issues += Issue("name", "Profile name must not be blank")
        if (profile.slots.size != Profile.SLOT_COUNT) {
            issues += Issue("slots", "Expected ${Profile.SLOT_COUNT} slots, got ${profile.slots.size}")
        }
        val seenIds = HashSet<String>()
        profile.slots.forEachIndexed { idx, m ->
            val base = "slots[$idx]"
            if (m.id.isBlank()) issues += Issue("$base.id", "Macro id must not be blank")
            if (!seenIds.add(m.id)) issues += Issue("$base.id", "Duplicate macro id: ${m.id}")
            if (m.label.isBlank()) issues += Issue("$base.label", "Macro label must not be blank")
            if (m.iconRef.isBlank()) issues += Issue("$base.iconRef", "iconRef must not be blank")
            issues += validateAction(m.action, "$base.action")
        }
        return issues
    }

    private fun validateAction(action: InputAction, path: String): List<Issue> {
        return when (action) {
            is InputAction.Chord -> emptyList()
            is InputAction.Literal -> try {
                LiteralTokenizer.tokenize(action.text)
                emptyList()
            } catch (ex: LiteralTokenizer.TokenizationException) {
                listOf(Issue("$path.text", "Unsupported character at index ${ex.index} (codepoint 0x${
                    ex.codepoint.toString(16)
                }). Only US-QWERTY ASCII is allowed."))
            }
            is InputAction.Sequence -> action.steps.flatMapIndexed { idx, step ->
                validateAction(step, "$path.steps[$idx]")
            }
        }
    }

    private fun parseMajorVersion(version: String): Int? {
        val firstDot = version.indexOf('.')
        val head = if (firstDot == -1) version else version.substring(0, firstDot)
        return head.toIntOrNull()
    }

    companion object {
        /** JSON codec configured for the profile schema: strict keys, no wild casts. */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = false
            prettyPrint = false
            classDiscriminator = "type"
        }
    }
}
