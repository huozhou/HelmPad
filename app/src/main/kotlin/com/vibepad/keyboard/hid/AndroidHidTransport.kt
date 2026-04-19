package com.vibepad.keyboard.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.vibepad.keyboard.input.HidFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Production [HidTransport] implementation backed by Android's `BluetoothHidDevice`
 * profile.
 *
 * Lifecycle:
 *   1. [start] -> `getProfileProxy(HID_DEVICE)` -> Proxying.
 *   2. `onServiceConnected` -> [registerApp] -> Advertising.
 *   3. Paired host connects -> `onConnectionStateChanged(STATE_CONNECTED)` -> Connected.
 *   4. Link drops -> Reconnecting with exponential backoff + periodic `connect()`.
 *   5. [stop] -> unregisterApp + closeProfileProxy + Idle.
 *
 * Permissions:
 *   - API 31+ requires `BLUETOOTH_CONNECT`. We check it up-front. Missing permission
 *     routes the state machine to `Unavailable(PERMISSIONS_MISSING)` — the UI layer
 *     shows an actionable prompt and the user can grant + [retry].
 *   - API 28–30 uses the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` permissions declared
 *     in the manifest; no runtime check is necessary.
 *
 * Threading:
 *   - Callbacks from the system arrive on a dedicated single-thread executor. We
 *     immediately marshal into [scope] via `MutableStateFlow` writes, which are
 *     thread-safe.
 */
class AndroidHidTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : HidTransport {

    private val _state = MutableStateFlow<HidLinkState>(HidLinkState.Idle)
    override val state: StateFlow<HidLinkState> = _state.asStateFlow()

    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vibepad-hid-callback").apply { isDaemon = true }
    }

    private val throttler = HidSendThrottler(scope) { frame ->
        val dev = pluggedDevice ?: return@HidSendThrottler
        val proxy = hidProxy ?: return@HidSendThrottler
        val reportBytes = frame.toByteArray()
        try {
            if (hasBluetoothConnectPermission()) {
                @SuppressLint("MissingPermission")
                val ok = proxy.sendReport(dev, frame.reportId, reportBytes)
                if (!ok) Log.w(TAG, "sendReport returned false for reportId=${frame.reportId}")
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "sendReport threw SecurityException — likely lost permission", se)
            setState(HidLinkState.Unavailable(UnavailableReason.PERMISSIONS_MISSING))
        }
    }

    private var hidProxy: BluetoothHidDevice? = null
    private var pluggedDevice: BluetoothDevice? = null
    private var lastKnownHost: HostDevice? = null
    private var reconnectJob: Job? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE || proxy !is BluetoothHidDevice) return
            hidProxy = proxy
            try {
                if (!hasBluetoothConnectPermission()) {
                    setState(HidLinkState.Unavailable(UnavailableReason.PERMISSIONS_MISSING))
                    return
                }
                @SuppressLint("MissingPermission")
                val requested = proxy.registerApp(
                    BluetoothHidDeviceAppSdpSettings(
                        /* name = */ SDP_NAME,
                        /* description = */ SDP_DESCRIPTION,
                        /* provider = */ SDP_PROVIDER,
                        /* subclass = */ HidDescriptor.SDP_SUBCLASS_COMBO,
                        /* descriptors = */ HidDescriptor.BYTES,
                    ),
                    /* inQos = */ null,
                    /* outQos = */ null,
                    callbackExecutor,
                    hidCallback,
                )
                if (!requested) {
                    Log.e(TAG, "registerApp returned false synchronously")
                    setState(HidLinkState.Failed(FailureCause.REGISTER_APP_REJECTED))
                }
            } catch (se: SecurityException) {
                setState(HidLinkState.Unavailable(UnavailableReason.PERMISSIONS_MISSING))
            } catch (t: Throwable) {
                Log.e(TAG, "registerApp threw", t)
                setState(HidLinkState.Failed(FailureCause.UNEXPECTED))
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidProxy = null
                if (_state.value !is HidLinkState.Idle) {
                    setState(HidLinkState.Unavailable(UnavailableReason.DEVICE_NO_HID_PERIPHERAL))
                }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered) {
                setState(HidLinkState.Failed(FailureCause.REGISTER_APP_REJECTED))
                return
            }
            this@AndroidHidTransport.pluggedDevice = pluggedDevice
            throttler.start()
            if (pluggedDevice == null) {
                setState(HidLinkState.Advertising)
            } else {
                val host = pluggedDevice.toHostDeviceOrNull() ?: return
                lastKnownHost = host
                setState(HidLinkState.Connected(host))
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    pluggedDevice = device
                    val host = device?.toHostDeviceOrNull()
                    if (host != null) {
                        lastKnownHost = host
                        cancelReconnect()
                        setState(HidLinkState.Connected(host))
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pluggedDevice = null
                    beginReconnectBackoff()
                }
                // CONNECTING / DISCONNECTING are transient; don't churn UI state.
                else -> Unit
            }
        }
    }

    override fun start() {
        if (_state.value !is HidLinkState.Idle && _state.value !is HidLinkState.Unavailable
            && _state.value !is HidLinkState.Failed) {
            return
        }
        if (!hasBluetoothConnectPermission()) {
            setState(HidLinkState.Unavailable(UnavailableReason.PERMISSIONS_MISSING))
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            setState(HidLinkState.Unavailable(UnavailableReason.BLUETOOTH_OFF))
            registerBluetoothStateReceiver()
            return
        }
        setState(HidLinkState.Proxying)
        registerBluetoothStateReceiver()
        val ok = try {
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (se: SecurityException) {
            setState(HidLinkState.Unavailable(UnavailableReason.PERMISSIONS_MISSING))
            return
        }
        if (!ok) setState(HidLinkState.Failed(FailureCause.PROXY_TIMEOUT))
    }

    override fun stop() {
        cancelReconnect()
        throttler.stop()
        val proxy = hidProxy
        val adapter = bluetoothAdapter()
        try {
            if (proxy != null && hasBluetoothConnectPermission()) {
                @SuppressLint("MissingPermission")
                proxy.unregisterApp()
            }
        } catch (se: SecurityException) { /* best-effort */ }
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        hidProxy = null
        pluggedDevice = null
        unregisterBluetoothStateReceiver()
        setState(HidLinkState.Idle)
    }

    override fun sendKeyboard(frame: HidFrame.Keyboard) {
        if (_state.value !is HidLinkState.Connected) return
        throttler.offer(frame)
    }

    override fun sendMouse(frame: HidFrame.Mouse) {
        if (_state.value !is HidLinkState.Connected) return
        throttler.offer(frame)
    }

    override fun retry() {
        stop()
        start()
    }

    // ---- Reconnect handling -----------------------------------------------------

    private fun beginReconnectBackoff() {
        val previousHost = lastKnownHost
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempt = 1
            while (true) {
                setState(HidLinkState.Reconnecting(attempt, previousHost))
                val backoffMs = computeBackoffMs(attempt)
                delay(backoffMs)
                // After the backoff, attempt to reinitiate the HID connection to the
                // last-known host. If the host comes back online first, the callback
                // will beat us and cancel this loop.
                val proxy = hidProxy
                val device = previousHost?.let { findBondedDevice(it.address) }
                if (proxy != null && device != null && hasBluetoothConnectPermission()) {
                    try {
                        @SuppressLint("MissingPermission")
                        proxy.connect(device)
                    } catch (se: SecurityException) {
                        setState(HidLinkState.Unavailable(UnavailableReason.PERMISSIONS_MISSING))
                        return@launch
                    }
                }
                attempt++
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ---- Bluetooth state receiver (detect user flipping the radio off) ----------

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR,
                ) ?: return
                when (state) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        setState(HidLinkState.Unavailable(UnavailableReason.BLUETOOTH_OFF))
                    }
                    BluetoothAdapter.STATE_ON -> {
                        // If we were previously Unavailable because of BT-off, reattempt.
                        if (_state.value is HidLinkState.Unavailable) retry()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bluetoothStateReceiver = receiver
    }

    private fun unregisterBluetoothStateReceiver() {
        bluetoothStateReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: IllegalArgumentException) { /* ignore */ }
        }
        bluetoothStateReceiver = null
    }

    // ---- Helpers ----------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun findBondedDevice(address: String): BluetoothDevice? {
        if (!hasBluetoothConnectPermission()) return null
        val adapter = bluetoothAdapter() ?: return null
        return try {
            adapter.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }
        } catch (_: SecurityException) { null }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toHostDeviceOrNull(): HostDevice? {
        if (!hasBluetoothConnectPermission()) return null
        val readableName = try { name } catch (_: SecurityException) { null }
        return HostDevice(name = readableName ?: address ?: "Unknown", address = address ?: return null)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setState(next: HidLinkState) {
        _state.value = next
    }

    /** Closes the internal coroutine scope. Call from the owning service's onDestroy. */
    fun release() {
        stop()
        callbackExecutor.shutdown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "VibePad/HID"

        private const val SDP_NAME = "Helm Pad"
        private const val SDP_DESCRIPTION = "Claude Code companion keyboard"
        private const val SDP_PROVIDER = "VibePad"

        /** Exponential backoff curve used by the reconnect loop. */
        internal fun computeBackoffMs(attempt: Int): Long {
            val scaled = 1_000L * (1L shl (attempt - 1).coerceIn(0, 5))
            return scaled.coerceAtMost(30_000L)
        }
    }
}
