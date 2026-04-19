package com.vibepad.keyboard.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibepad.keyboard.VibePadApplication
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HidTransport
import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State owner for `PairedHostsScreen`.
 *
 * Three sources are joined into one UI state:
 *   1. `PairedHostsStore.flow()`   — the app-local ledger (with aliases)
 *   2. `BluetoothAdapter.bondedDevices` — polled every 2s so we see removals
 *      the user performed in system settings
 *   3. `HidTransport.state`        — to tag the currently-connected row
 *
 * The visible list is **the intersection** of (1) ∩ (2). A MAC only in the
 * ledger (user unpaired via system Settings) or only in bondedDevices (e.g. a
 * Bluetooth speaker) is hidden. That's the whole reason the ledger exists —
 * `bondedDevices` is too broad, and we don't want to offer "Forget" on a user's
 * headphones.
 *
 * Forget flow:
 *   - VM calls [RemoveBondHelper.forget] on IO
 *   - On `Success`, it also removes the row from the ledger so the list
 *     shrinks instantly (otherwise the next poll would do it, but with a
 *     ~2s delay).
 *   - On `FallbackOpenedSettings`, we leave the ledger intact; if the user
 *     actually completes the removal in Settings, the next bonded poll will
 *     drop the row via intersection. We emit a [Event.FallbackOpenedSettings]
 *     so the UI can show a Snackbar.
 */
class PairedHostsViewModel(
    private val store: PairedHostsStore,
    private val transport: HidTransport,
    private val bondedSource: BondedHostsSource,
    private val deviceLookup: (String) -> BluetoothDevice?,
    private val forget: (BluetoothDevice) -> RemoveBondHelper.Result,
    pollIntervalMs: Long = POLL_INTERVAL_MS,
) : ViewModel() {

    private val bondedTicker: Flow<Set<String>> = flow {
        while (true) {
            emit(bondedSource.currentMacs())
            delay(pollIntervalMs)
        }
    }

    val state: StateFlow<PairedHostsUiState> = combine(
        store.flow(),
        bondedTicker,
        transport.state,
    ) { recorded, bondedMacs, link ->
        val activeMac = (link as? HidLinkState.Connected)?.host?.address?.uppercase()
        val rows = recorded
            .filter { it.mac in bondedMacs }
            .map { host ->
                PairedHostRow(
                    mac = host.mac,
                    displayName = host.displayName,
                    systemName = host.systemName,
                    alias = host.alias,
                    lastSeenAt = host.lastSeenAt,
                    isActive = host.mac == activeMac,
                    hostOs = host.hostOs,
                )
            }
        if (rows.isEmpty()) PairedHostsUiState.Empty else PairedHostsUiState.List(rows)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PairedHostsUiState.Loading,
    )

    private val _hostOsPickerFor = MutableStateFlow<PairedHostRow?>(null)

    /** Non-null while [HostOsPickerSheet] should be visible for this row. */
    val hostOsPickerFor: StateFlow<PairedHostRow?> = _hostOsPickerFor.asStateFlow()

    /** Open the OS picker bottom sheet for the given row. */
    fun onOpenHostOsPicker(row: PairedHostRow) {
        _hostOsPickerFor.value = row
    }

    /** Dismiss without changing anything. */
    fun onDismissHostOsPicker() {
        _hostOsPickerFor.value = null
    }

    /**
     * Persist a manual OS choice. `null` means "use auto-detected" — clears
     * the override and falls back to the detector verdict on the next read.
     */
    fun onSelectHostOs(mac: String, target: HostTarget?) {
        viewModelScope.launch { store.recordOverride(mac, target) }
        _hostOsPickerFor.value = null
    }

    /**
     * Wipe the detector verdict for this MAC. Re-runs the inspector on the
     * next connect; the override (if any) is preserved.
     */
    fun onReDetect(mac: String) {
        viewModelScope.launch { store.clearDetection(mac) }
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun onForget(mac: String) {
        viewModelScope.launch {
            val device = deviceLookup(mac)
            if (device == null) {
                // Not bonded any more — treat as already-gone and just prune the ledger.
                store.remove(mac)
                _events.tryEmit(Event.ForgotLocally)
                return@launch
            }
            when (val result = forget(device)) {
                RemoveBondHelper.Result.Success -> {
                    store.remove(mac)
                    _events.tryEmit(Event.Forgot)
                }
                RemoveBondHelper.Result.FallbackOpenedSettings -> {
                    _events.tryEmit(Event.FallbackOpenedSettings)
                }
                is RemoveBondHelper.Result.Error -> {
                    _events.tryEmit(Event.Error(result.throwable))
                }
            }
        }
    }

    fun onRename(mac: String, alias: String) {
        viewModelScope.launch { store.setAlias(mac, alias) }
    }

    sealed interface Event {
        object Forgot : Event
        object ForgotLocally : Event
        object FallbackOpenedSettings : Event
        data class Error(val throwable: Throwable) : Event
    }

    companion object {
        internal const val POLL_INTERVAL_MS: Long = 2_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = context.applicationContext as VibePadApplication
                PairedHostsViewModel(
                    store = app.pairedHostsStore,
                    transport = app.hidTransport,
                    bondedSource = AndroidBondedHostsSource(app),
                    deviceLookup = { mac -> app.findBondedDeviceByMac(mac) },
                    forget = { device -> RemoveBondHelper.forget(app, device) },
                )
            }
        }
    }
}

/** Abstract the system read so tests can inject. */
interface BondedHostsSource {
    fun currentMacs(): Set<String>
}

/**
 * Reads `BluetoothAdapter.bondedDevices` guarded by BLUETOOTH_CONNECT on 12+.
 * Returns an upper-cased set so set membership checks match the store's
 * uppercased MAC keys.
 */
class AndroidBondedHostsSource(private val context: Context) : BondedHostsSource {
    @SuppressLint("MissingPermission")
    override fun currentMacs(): Set<String> {
        val adapter: BluetoothAdapter = context.getSystemService<BluetoothManager>()?.adapter ?: return emptySet()
        return try {
            adapter.bondedDevices.orEmpty().mapNotNull { it.address?.uppercase() }.toSet()
        } catch (_: SecurityException) {
            emptySet()
        }
    }
}

@SuppressLint("MissingPermission")
private fun Context.findBondedDeviceByMac(mac: String): BluetoothDevice? {
    val adapter = getSystemService<BluetoothManager>()?.adapter ?: return null
    return try {
        adapter.bondedDevices.orEmpty().firstOrNull { it.address.equals(mac, ignoreCase = true) }
    } catch (_: SecurityException) { null }
}

sealed interface PairedHostsUiState {
    object Loading : PairedHostsUiState
    object Empty : PairedHostsUiState
    data class List(val rows: kotlin.collections.List<PairedHostRow>) : PairedHostsUiState
}

data class PairedHostRow(
    val mac: String,
    val displayName: String,
    val systemName: String,
    val alias: String?,
    val lastSeenAt: Long,
    val isActive: Boolean,
    val hostOs: HostOsRecord = HostOsRecord.EMPTY,
) {
    /** The OS Helm Pad will treat this host as. Null means we have no opinion yet. */
    val effectiveHostTarget: HostTarget?
        get() = hostOs.userOverrideHostTarget ?: hostOs.detectedHostTarget

    /** True when neither the detector nor the user has expressed an opinion. */
    val needsReview: Boolean
        get() = hostOs.detectedHostTarget == null && hostOs.userOverrideHostTarget == null
}
