import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.UUID

class BleManager(private val context: Context) {
    private val rxBleClient = RxBleClient.create(context)
    private var bleDevice: RxBleDevice? = null
    private var bleConnection: RxBleConnection? = null
    private val compositeDisposable = CompositeDisposable()

    private val connectionStateSubject = BehaviorSubject.create<ConnectionState>()
    private val bondStateSubject = BehaviorSubject.create<BondState>()
    private val dataReceivedSubject = PublishSubject.create<ByteArray>()

    private var connectionDisposable: Disposable? = null
    private var notificationDisposable: Disposable? = null

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = getBluetoothDeviceFromIntent(intent)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                if (device?.address == DEVICE_MAC_ADDRESS) {
                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> bondStateSubject.onNext(BondState.Bonded)
                        BluetoothDevice.BOND_BONDING -> bondStateSubject.onNext(BondState.Bonding)
                        BluetoothDevice.BOND_NONE -> bondStateSubject.onNext(BondState.NotBonded)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun getBluetoothDeviceFromIntent(intent: Intent): BluetoothDevice? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        }
    }

    companion object {
        private const val DEVICE_MAC_ADDRESS = "48:E7:29:B7:C4:76"
        private val SERVICE_UUID = UUID.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
        private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("00001235-0000-1000-8000-00805F9B34FB")
        private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("00001234-0000-1000-8000-00805F9B34FB")

        // Required permissions for bonding
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    }

    sealed class ConnectionState {
        data object Connected : ConnectionState()
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    sealed class BondState {
        data object Bonded : BondState()
        data object Bonding : BondState()
        data object NotBonded : BondState()
        data class Error(val message: String) : BondState()
        data object MissingPermission : BondState()
    }

    init {
        if (!rxBleClient.isScanRuntimePermissionGranted) {
            Log.e("BleManager", "Missing BLE scan permissions")
        }

        if (hasRequiredPermissions()) {
            try {
                context.registerReceiver(
                    bondStateReceiver,
                    IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                )
            } catch (e: SecurityException) {
                Log.e("BleManager", "Failed to register bond state receiver", e)
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ActivityCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e("BleManager", "Bluetooth not supported")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Prompt user to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            context.startActivity(enableBtIntent)
            return
        }

        scanForDevice()
    }
    private var scanDisposable: Disposable? = null

    fun scanForDevice() {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress(DEVICE_MAC_ADDRESS)
            .build()

        scanDisposable?.dispose() // Cancel any existing scan
        scanDisposable = rxBleClient.scanBleDevices(scanSettings, scanFilter)
            .take(1) // Only take the first matching device
            .subscribe({ scanResult ->
                Log.d("BleManager", "Device found: ${scanResult.bleDevice.macAddress}")
                bleDevice = scanResult.bleDevice
                connect()
            }, { error ->
                Log.e("BleManager", "Scan error", error)
                connectionStateSubject.onNext(ConnectionState.Error("Scan failed: ${error.message}"))
            })

        // Add the disposable to the composite disposable to manage its lifecycle
        scanDisposable?.let { compositeDisposable.add(it) }
    }

    fun connect() {
        if (!hasRequiredPermissions()) {
            connectionStateSubject.onNext(ConnectionState.Error("Missing required Bluetooth permissions"))
            return
        }

        bleDevice = rxBleClient.getBleDevice(DEVICE_MAC_ADDRESS)

        connectionDisposable?.dispose()
        connectionDisposable = bleDevice?.establishConnection(false)
            ?.doOnSubscribe { connectionStateSubject.onNext(ConnectionState.Connecting) }
            ?.subscribe({ connection ->
                bleConnection = connection
                connectionStateSubject.onNext(ConnectionState.Connected)
                checkAndRequestBond()
                setupNotification()
            }, { throwable ->
                connectionStateSubject.onNext(ConnectionState.Error(throwable.message ?: "Unknown error"))
                Log.e("BleManager", "Connection error", throwable)
            })?.also { compositeDisposable.add(it) }
    }

    private fun checkAndRequestBond() {
        if (!hasRequiredPermissions()) {
            bondStateSubject.onNext(BondState.MissingPermission)
            return
        }

        try {
            bleDevice?.bluetoothDevice?.let { device ->
                when (device.bondState) {
                    BluetoothDevice.BOND_NONE -> {
                        bondStateSubject.onNext(BondState.NotBonded)
                        // Request bonding with try-catch for SecurityException
                        try {
                            val bondingInitiated = device.createBond()
                            if (bondingInitiated) {
                                Log.d("BleManager", "Bond request initiated")
                            } else {
                                bondStateSubject.onNext(BondState.Error("Failed to initiate bonding"))
                            }
                        } catch (e: SecurityException) {
                            Log.e("BleManager", "Security exception during bonding", e)
                            bondStateSubject.onNext(BondState.Error("Permission denied: ${e.message}"))
                        }
                    }
                    BluetoothDevice.BOND_BONDED -> {
                        bondStateSubject.onNext(BondState.Bonded)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        bondStateSubject.onNext(BondState.Bonding)
                    }

                    else -> {
                        bondStateSubject.onNext(BondState.Error("Unknown bond state: $device.bondState"))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("BleManager", "Security exception checking bond state", e)
            bondStateSubject.onNext(BondState.Error("Permission denied: ${e.message}"))
        }
    }

    private fun setupNotification() {
        if (!hasRequiredPermissions()) {
            return
        }

        notificationDisposable?.dispose()
        notificationDisposable = bleConnection?.setupNotification(CHARACTERISTIC_NOTIFY_UUID)
            ?.flatMap { it }
            ?.subscribe({ bytes ->
                dataReceivedSubject.onNext(bytes)
            }, { throwable ->
                Log.e("BleManager", "Notification setup error", throwable)
            })?.also { compositeDisposable.add(it) }
    }

    fun disconnect() {
        compositeDisposable.clear()
        connectionStateSubject.onNext(ConnectionState.Disconnected)
    }

    fun observeConnectionState(): Observable<ConnectionState> = connectionStateSubject

    fun observeBondState(): Observable<BondState> = bondStateSubject

    fun observeData(): Observable<ByteArray> = dataReceivedSubject

    fun cleanup() {
        if (hasRequiredPermissions()) {
            try {
                context.unregisterReceiver(bondStateReceiver)
            } catch (e: SecurityException) {
                Log.e("BleManager", "Failed to unregister bond state receiver", e)
            } catch (e: IllegalArgumentException) {
                // Receiver wasn't registered
                Log.w("BleManager", "Receiver wasn't registered", e)
            }
        }
        compositeDisposable.clear()
    }
}