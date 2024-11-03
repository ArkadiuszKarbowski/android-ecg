package com.example.android_ecg

import BleManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: BleManager
    private val disposables = CompositeDisposable()

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

        val statusText = findViewById<TextView>(R.id.statusText)
        val connectButton = findViewById<Button>(R.id.connectButton)
        connectButton.setOnClickListener {
            bleManager.connect()
        }

        disposables.add(
            bleManager.observeConnectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { state ->
                    when (state) {
                        is BleManager.ConnectionState.Connected -> {
                            statusText.text = "Status: Połączono"
                        }
                        is BleManager.ConnectionState.Disconnected -> {
                            statusText.text = "Status: Rozłączono"
                        }
                        is BleManager.ConnectionState.Connecting -> {
                            statusText.text = "Status: Łączenie..."
                        }
                        is BleManager.ConnectionState.Error -> {
                            Toast.makeText(this, "Błąd: ${state.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        )

        disposables.add(
            bleManager.observeData()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { data ->
                    // Tutaj przetwarzaj otrzymane dane
                    Log.d("MainActivity", "Received data: ${data.contentToString()}")
                }
        )
    }

    override fun onPause() {
        super.onPause()
        bleManager.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

//    private fun checkPermissionsAndConnect() {
//        // Sprawdź uprawnienia i połącz się z urządzeniem
//        if (checkPermissions()) {
//            bleManager.connect()
//        } else {
//            requestPermissions()
//        }
//    }
}