package com.vibepad.keyboard.pairing

/**
 * Linear 6-step onboarding flow. Ordering here is also the navigation order — the
 * `ordinal` is used to compute the progress indicator and the "resume from first
 * incomplete step" behaviour when the user returns to a half-configured app.
 *
 * `PROFILE` is the last interactive step, added in
 * `add-codex-cursor-profiles` so new users can pick Codex / Cursor at setup time
 * instead of discovering the switcher later in Settings. It defaults to Claude
 * Code so users who only want the out-of-box profile can clear the step with a
 * single `Finish` tap.
 */
enum class OnboardingStep {
    INTRO,
    PERMISSIONS,
    BLUETOOTH,
    PAIRING,
    HOST_TARGET,
    PROFILE,
    DONE,
    ;

    fun next(): OnboardingStep = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    fun previous(): OnboardingStep = entries[(ordinal - 1).coerceAtLeast(0)]
}
