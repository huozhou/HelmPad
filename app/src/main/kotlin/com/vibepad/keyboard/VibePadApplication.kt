package com.vibepad.keyboard

import android.app.Application
import com.vibepad.keyboard.hid.AndroidHidTransport
import com.vibepad.keyboard.hid.HidTransport
import com.vibepad.keyboard.macro.AssetProfileSource
import com.vibepad.keyboard.macro.SelectionsStore
import com.vibepad.keyboard.pairing.BtHostInspector
import com.vibepad.keyboard.pairing.OnboardingCompletionStore
import com.vibepad.keyboard.pairing.OuiVendorHints
import com.vibepad.keyboard.pairing.PairedHostsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide owner of singletons. Every class that needs a [HidTransport] —
 * whether it's the foreground service or the operator UI — reads the same instance
 * from here. That prevents the double-`registerApp` issue we'd otherwise see when
 * both the service and the activity tried to own their own transports.
 *
 * No DI framework in v1. If the dependency graph grows past this handful, introduce
 * Hilt or Koin here and migrate callers one module at a time.
 */
class VibePadApplication : Application() {

    val hidTransport: HidTransport by lazy { AndroidHidTransport(applicationContext) }

    val selectionsStore: SelectionsStore by lazy { SelectionsStore(applicationContext) }

    val completionStore: OnboardingCompletionStore by lazy { OnboardingCompletionStore(applicationContext) }

    val pairedHostsStore: PairedHostsStore by lazy { PairedHostsStore(applicationContext) }

    val ouiVendorHints: OuiVendorHints by lazy { OuiVendorHints(applicationContext) }

    val btHostInspector: BtHostInspector by lazy { BtHostInspector(ouiVendorHints) }

    val profileSource: AssetProfileSource by lazy { AssetProfileSource(assets) }

    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Warm the OUI table so the inspector's signal-3 path is ready by the
        // time the user pairs their first host. Failure is non-fatal — the
        // inspector still has the name + class signals.
        appScope.launch { ouiVendorHints.load() }
    }
}
