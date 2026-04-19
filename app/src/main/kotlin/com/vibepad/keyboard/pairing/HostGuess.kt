package com.vibepad.keyboard.pairing

import com.vibepad.keyboard.input.HostTarget

/**
 * The verdict the inspector reaches when looking at a freshly-connected host.
 *
 * Three orthogonal facts:
 *  - [target]    — what OS, if any, the inspector thinks the host is.
 *  - [confidence] — how strongly we believe it (drives whether we surface a
 *                   "needs review" badge in Paired Hosts).
 *  - [source]    — which signal (name, BluetoothClass, or OUI prefix) produced
 *                   the verdict. Surfaced verbatim in the picker sheet so the
 *                   user can tell *why* we guessed what we guessed.
 *
 * `HostGuess.NONE` is the canonical "we have no idea" value — every empty
 * caller maps to this single instance to make equality checks cheap and to
 * keep the persisted JSON compact (one well-known shape rather than three
 * nullables).
 */
data class HostGuess(
    val target: HostTarget?,
    val confidence: Confidence,
    val source: Source,
) {
    companion object {
        val NONE: HostGuess = HostGuess(target = null, confidence = Confidence.NONE, source = Source.NONE)
    }
}

/**
 * Strength of the inspector's verdict.
 *
 *  - HIGH   — Bluetooth name matched a hard-coded vendor pattern (`MacBook`,
 *             `DESKTOP-XXXXXXX`, `Surface`, …). Almost certainly correct.
 *  - MEDIUM — reserved for future signals (CoD minor class, SDP DID record).
 *  - LOW    — only the OUI prefix suggested it. Vendor mapping is fuzzy
 *             (Apple OUIs also ship in iPads and AirPods), so we ask the user
 *             to confirm by surfacing a "needs review" affordance.
 *  - NONE   — no signal aligned. We render "Unknown" and fall back to the
 *             session default OS.
 */
enum class Confidence { HIGH, MEDIUM, LOW, NONE }

/**
 * Which heuristic produced the verdict. Only one wins per inspect call — the
 * earliest-priority signal that matches stops the cascade. See
 * [BtHostInspector] for the order.
 */
enum class Source { NAME, COD, OUI, NONE }
