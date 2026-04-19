package com.vibepad.keyboard.hid

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.content.getSystemService
import com.vibepad.keyboard.VibePadApplication
import com.vibepad.keyboard.pairing.BtHostInspector
import com.vibepad.keyboard.pairing.Confidence
import com.vibepad.keyboard.pairing.PairedHostsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service owning the [HidTransport].
 *
 * Why a service (not a singleton in Application):
 *   - Activities die on rotation / task switch. Without a foreground service the HID
 *     registration is torn down seconds after the UI disappears. Android then reclaims
 *     the `BluetoothHidDevice` proxy for another process.
 *   - A persistent notification acts as the user's "link is alive" signal AND is the
 *     only Android-approved way to keep `BluetoothHidDevice` registered when the app
 *     is backgrounded.
 *
 * API 34 requirements:
 *   - Manifest declares `foregroundServiceType="connectedDevice"`.
 *   - Manifest declares `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission.
 *   - `startForeground` must be called within 5 seconds of `startService`.
 *
 * API 28–30:
 *   - Manifest type still valid; older SDK ignores the typed permission.
 */
class HidForegroundService : Service() {

    private val transport: HidTransport by lazy {
        (application as VibePadApplication).hidTransport
    }
    private val pairedHostsStore: PairedHostsStore by lazy {
        (application as VibePadApplication).pairedHostsStore
    }
    private val btHostInspector: BtHostInspector by lazy {
        (application as VibePadApplication).btHostInspector
    }
    private val serviceScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val binder = LocalBinder()
    private var stateObserverJob: Job? = null
    private var recordObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        HidNotifications.ensureChannel(this)
        // Move to foreground immediately with a minimal notification; we can't do HID
        // work while the system considers us a background service (API 31+ restriction).
        startForeground(HidNotifications.NOTIFICATION_ID, HidNotifications.build(this, transport.state.value))
        stateObserverJob = transport.state
            .onEach { state -> HidNotifications.update(this@HidForegroundService, state) }
            .launchIn(serviceScope)

        // Side-effect: every time we transition into Connected with a new host
        // address, stamp a row in PairedHostsStore. `distinctUntilChangedBy` dedups
        // the inevitable reconnect storm so we don't issue a DataStore write per
        // link flap.
        recordObserverJob = transport.state
            .filterIsInstance<HidLinkState.Connected>()
            .distinctUntilChangedBy { it.host.address }
            .onEach { connected ->
                serviceScope.launch(Dispatchers.IO) {
                    runCatching {
                        pairedHostsStore.recordConnection(
                            mac = connected.host.address,
                            systemName = connected.host.name,
                            nowMs = System.currentTimeMillis(),
                        )
                        // Run the OS detector at most once per host until the
                        // user clears the verdict via Paired Hosts → "Re-detect".
                        // We re-run only when both fields are at their initial
                        // value; that means we *don't* re-run on a NONE result
                        // either — the user can promote it manually with the
                        // override picker or by hitting Re-detect.
                        val existing = pairedHostsStore.hostOs(connected.host.address)
                        if (existing.detectedHostTarget == null && existing.detectedConfidence == Confidence.NONE) {
                            val probe = probeFromBondedAdapter(connected.host)
                            val guess = btHostInspector.inspect(probe)
                            pairedHostsStore.recordDetection(connected.host.address, guess)
                        }
                    }
                }
            }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                transport.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> transport.start() // ACTION_START or null (restart after process death)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stateObserverJob?.cancel()
        recordObserverJob?.cancel()
        serviceScope.cancel()
        // Note: we intentionally do NOT release() the shared transport here — other
        // process components (the operator UI) may still be using it. Full teardown
        // happens when the process dies.
        super.onDestroy()
    }

    /**
     * Resolve a [BtHostInspector.Probe] for the freshly-connected host. We
     * grab the BluetoothDevice from `bondedDevices` (which is the same list
     * the OS hands us at pairing time) so we can read its `name` and
     * `bluetoothClass.majorDeviceClass`. If lookup fails (e.g. the user
     * unbonded between the connect and our handler running), we still build
     * a Probe from the address alone — the OUI signal can still match.
     */
    @SuppressLint("MissingPermission")
    private fun probeFromBondedAdapter(host: HostDevice): BtHostInspector.Probe {
        val adapter = getSystemService<BluetoothManager>()?.adapter
        val bonded = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        val device = bonded.firstOrNull { it.address.equals(host.address, ignoreCase = true) }
        return BtHostInspector.Probe(
            name = device?.let { runCatching { it.name }.getOrNull() } ?: host.name,
            majorClass = device?.let { runCatching { it.bluetoothClass?.majorDeviceClass }.getOrNull() },
            address = host.address.uppercase(),
        )
    }

    /**
     * Binder exposing only the [HidTransport] surface — never the service instance.
     * UI layer binds, reads [transport], and drops the binding on `onStop`.
     */
    inner class LocalBinder : Binder() {
        val transport: HidTransport get() = this@HidForegroundService.transport
        val state: StateFlow<HidLinkState> get() = this@HidForegroundService.transport.state
    }

    companion object {
        const val ACTION_START = "com.vibepad.keyboard.hid.START"
        const val ACTION_STOP = "com.vibepad.keyboard.hid.STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, HidForegroundService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, HidForegroundService::class.java).setAction(ACTION_STOP)
    }
}
