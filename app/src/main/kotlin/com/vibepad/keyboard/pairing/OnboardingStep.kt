package com.vibepad.keyboard.pairing

/**
 * Linear 5-step onboarding flow. Ordering here is also the navigation order — the
 * `ordinal` is used to compute the progress indicator and the "resume from first
 * incomplete step" behaviour when the user returns to a half-configured app.
 */
enum class OnboardingStep {
    INTRO,
    PERMISSIONS,
    BLUETOOTH,
    PAIRING,
    HOST_TARGET,
    DONE,
    ;

    fun next(): OnboardingStep = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    fun previous(): OnboardingStep = entries[(ordinal - 1).coerceAtLeast(0)]
}
