import android.content.Context
import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.UUID


class BleManager(private val context: Context) {
    private val rxBleClient = RxBleClient.create(context)
    private var bleDevice: RxBleDevice? = null
    private var bleConnection: RxBleConnection? = null

    private val connectionStateSubject = BehaviorSubject.create<ConnectionState>()
    private val dataReceivedSubject = PublishSubject.create<ByteArray>()

    private var connectionDisposable: Disposable? = null
    private var notificationDisposable: Disposable? = null

    companion object {
        private const val DEVICE_MAC_ADDRESS = "48:E7:29:B7:C4:76"
        private val SERVICE_UUID = UUID.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
        private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("00001235-0000-1000-8000-00805F9B34FB")
        private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("00001234-0000-1000-8000-00805F9B34FB")
    }

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    init {
        if (!rxBleClient.isScanRuntimePermissionGranted) {
            Log.e("BleManager", "Brak uprawnień do skanowania BLE")
        }
    }

    fun connect() {
        bleDevice = rxBleClient.getBleDevice(DEVICE_MAC_ADDRESS)

        connectionDisposable?.dispose()
        connectionDisposable = bleDevice?.establishConnection(false)
            ?.doOnSubscribe { connectionStateSubject.onNext(ConnectionState.Connecting) }
            ?.subscribe({ connection ->
                bleConnection = connection
                connectionStateSubject.onNext(ConnectionState.Connected)
                setupNotification()
            }, { throwable ->
                connectionStateSubject.onNext(ConnectionState.Error(throwable.message ?: "Unknown error"))
                Log.e("BleManager", "Connection error", throwable)
            })
    }

    private fun setupNotification() {
        notificationDisposable?.dispose()
        notificationDisposable = bleConnection?.setupNotification(CHARACTERISTIC_NOTIFY_UUID)
            ?.flatMap { it }
            ?.subscribe({ bytes ->
                dataReceivedSubject.onNext(bytes)
            }, { throwable ->
                Log.e("BleManager", "Notification setup error", throwable)
            })
    }

    fun writeData(data: ByteArray) {
        bleConnection?.writeCharacteristic(CHARACTERISTIC_WRITE_UUID, data)
            ?.subscribe({
                Log.d("BleManager", "Data written successfully")
            }, { throwable ->
                Log.e("BleManager", "Write error", throwable)
            })
    }

    fun disconnect() {
        connectionDisposable?.dispose()
        notificationDisposable?.dispose()
        connectionStateSubject.onNext(ConnectionState.Disconnected)
    }

    fun observeConnectionState(): Observable<ConnectionState> = connectionStateSubject

    fun observeData(): Observable<ByteArray> = dataReceivedSubject
}