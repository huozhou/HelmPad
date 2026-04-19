package com.vibepad.keyboard.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the outcome classifier inside [RemoveBondHelper]. We can't safely
 * subclass `BluetoothDevice` in unit tests (its constructor is hidden on the
 * SDK stubs), so [RemoveBondHelper.classifyInvocation] takes a hand-rolled
 * invocation lambda instead — that's the unit this suite pins down.
 */
class RemoveBondHelperTest {

    @Test
    fun `invocation returning true maps to Success`() {
        assertEquals(
            RemoveBondHelper.ReflectionOutcome.Success,
            RemoveBondHelper.classifyInvocation { true },
        )
    }

    @Test
    fun `invocation returning false maps to Refused`() {
        assertEquals(
            RemoveBondHelper.ReflectionOutcome.Refused,
            RemoveBondHelper.classifyInvocation { false },
        )
    }

    @Test
    fun `invocation returning null maps to Refused`() {
        assertEquals(
            RemoveBondHelper.ReflectionOutcome.Refused,
            RemoveBondHelper.classifyInvocation { null },
        )
    }

    @Test
    fun `NoSuchMethodException maps to Missing`() {
        assertEquals(
            RemoveBondHelper.ReflectionOutcome.Missing,
            RemoveBondHelper.classifyInvocation {
                throw NoSuchMethodException("removeBond absent on this ROM")
            },
        )
    }

    @Test
    fun `SecurityException maps to SecurityBlocked`() {
        assertEquals(
            RemoveBondHelper.ReflectionOutcome.SecurityBlocked,
            RemoveBondHelper.classifyInvocation {
                throw SecurityException("MIUI blocked hidden API")
            },
        )
    }

    @Test
    fun `arbitrary Throwable maps to Refused`() {
        assertEquals(
            RemoveBondHelper.ReflectionOutcome.Refused,
            RemoveBondHelper.classifyInvocation {
                throw RuntimeException("flaky stack")
            },
        )
    }
}
