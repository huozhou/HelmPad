package com.vibepad.keyboard.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the height arithmetic the operator screen relies on to decide
 * whether it can fit without the verticalScroll fallback. These numbers are
 * straight algebra, but the moment a future change tweaks tile gap / outer
 * padding the budget will silently slide and the screen will overflow on a
 * borderline device — these tests are the early-warning siren.
 */
class LayoutBudgetTest {

    @Test
    fun `8 slots on 360dp width occupies the expected grid height`() {
        // Tile size = (360 - 16 - 24) / 4 = 80
        // 2 rows * 80 + 1 gap * 8 + outer padding 16 + 4 fudge = 188
        val height = LayoutBudget.macroGridHeight(360.dp, slotCount = 8)
        assertEquals(188.dp, height)
    }

    @Test
    fun `12 slots on 360dp width occupies three rows`() {
        // 3 rows * 80 + 2 gaps * 8 + 16 outer + 4 fudge = 276
        val height = LayoutBudget.macroGridHeight(360.dp, slotCount = 12)
        assertEquals(276.dp, height)
    }

    @Test
    fun `9 slots wraps to a third row even though only one tile lives there`() {
        val eight = LayoutBudget.macroGridHeight(360.dp, slotCount = 8)
        val nine = LayoutBudget.macroGridHeight(360.dp, slotCount = 9)
        // 9 slots needs row #3 just for the spillover tile, so it must be
        // strictly taller than the 8-slot case (one extra row + one extra gap).
        assertTrue("$nine should exceed $eight", nine > eight)
    }

    @Test
    fun `zero slots leaves only the outer padding`() {
        val height = LayoutBudget.macroGridHeight(360.dp, slotCount = 0)
        assertEquals(LayoutBudget.MacroGridOuterPadding * 2, height)
    }

    @Test
    fun `natural total height sums every operator-screen component`() {
        val total = LayoutBudget.naturalTotalHeight(360.dp, slotCount = 8)
        val grid = LayoutBudget.macroGridHeight(360.dp, slotCount = 8)
        val expected = LayoutBudget.AppBarHeight +
            LayoutBudget.MaxBannerHeight +
            LayoutBudget.DividerHeight +
            LayoutBudget.TouchpadMinHeight +
            LayoutBudget.ProfileHeaderHeight +
            grid +
            LayoutBudget.SafetyBuffer
        assertEquals(expected, total)
    }

    @Test
    fun `natural total height is monotonic in slot count`() {
        val a = LayoutBudget.naturalTotalHeight(360.dp, slotCount = 4)
        val b = LayoutBudget.naturalTotalHeight(360.dp, slotCount = 8)
        val c = LayoutBudget.naturalTotalHeight(360.dp, slotCount = 12)
        assertTrue(a <= b)
        assertTrue(b <= c)
    }

    @Test
    fun `natural total height shrinks when banner budget is removed`() {
        val withBanner = LayoutBudget.naturalTotalHeight(360.dp, slotCount = 8)
        val withoutBanner = LayoutBudget.naturalTotalHeight(360.dp, slotCount = 8, maxBannerHeight = 0.dp)
        assertEquals(LayoutBudget.MaxBannerHeight, withBanner - withoutBanner)
    }
}
