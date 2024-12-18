package com.example.android_ecg

import BleManager
import ChartManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: BleManager
    private val disposables = CompositeDisposable()

    private lateinit var chartManager: ChartManager
    private lateinit var lineChart: LineChart

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        bleManager = BleManager(this)

        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)

        connectButton.setOnClickListener {
            bleManager.observeConnectionState()
                .firstElement()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { connectionState ->
                    if (connectionState is BleManager.ConnectionState.Connected) {
                        bleManager.disconnect()
                        connectButton.text = getString(R.string.connectButtonText_connect)
                    } else {
                        bleManager.connect()
                        connectButton.text = getString(R.string.connectButtonText_disconnect)
                    }
                }.let { disposables.add(it) }
        }

        lineChart = findViewById(R.id.ecgChart)
        chartManager = ChartManager(this, lineChart)
        setupBleConnection()
        bleManager.scanForDevice()
    }

    private fun setupBleConnection() {
        disposables.add(
            bleManager.observeConnectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { state ->
                    when (state) {
                        is BleManager.ConnectionState.Connected -> {
                            statusText.text = getString(R.string.statusText_connected)
                        }
                        is BleManager.ConnectionState.Disconnected -> {
                            statusText.text = getString(R.string.statusText_disconnected)
                        }
                        is BleManager.ConnectionState.Connecting -> {
                            statusText.text = getString(R.string.statusText_connecting)
                        }
                        is BleManager.ConnectionState.Error -> {
                            Toast.makeText(this, "ERROR: ${state.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        )

        disposables.add(
            bleManager.observeData()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { data ->
                    val value = bytesToU16(data)
                    Log.d("MainActivity", "Received value: $value")
                    chartManager.addEntryToChart(value.toFloat())
                }
        )
    }

    private fun bytesToU16(bytes: ByteArray): Int {
        if (bytes.size < 2) return 0
        return (bytes[1].toInt() and 0xFF shl 8) or (bytes[0].toInt() and 0xFF)
    }

    override fun onPause() {
        super.onPause()
        bleManager.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
        bleManager.cleanup()
    }
}