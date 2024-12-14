package com.example.android_ecg
import BleManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: BleManager
    private val disposables = CompositeDisposable()

    private lateinit var lineChart: LineChart
    private val dataPoints = ArrayList<Entry>()
    private var xValue = 0f

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
            bleManager.observeConnectionState().blockingFirst().let { connectionState ->
                if (connectionState is BleManager.ConnectionState.Connected) {
                    bleManager.disconnect()
                } else {
                    bleManager.connect()
                }
            }
        }
        lineChart = findViewById(R.id.ecgChart)

        setupChart()
        setupBleConnection()


    }
    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setDrawGridBackground(true) // Enable grid background to allow background drawing

            // Load your background image
            val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.ecarg_logo)
            backgroundDrawable?.alpha = 50 // Set transparency (0-255, where 0 is fully transparent and 255 is opaque)

            // Set the background drawable
            background = backgroundDrawable

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                setDrawAxisLine(true)
            }
            axisRight.isEnabled = false

            // Optional: Customize grid background color to blend with the image
            setGridBackgroundColor(Color.argb(20, 255, 255, 255)) // Light semi-transparent white

            val dataSet = LineDataSet(ArrayList(), "ECG")
            dataSet.apply {
                color = Color.RED
                setDrawCircles(false)
                setDrawValues(false)
                lineWidth = 2f
            }
            data = LineData(dataSet)
        }
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
                    addEntryToChart(value.toFloat())
                }
        )
    }

    private fun bytesToU16(bytes: ByteArray): Int {
        if (bytes.size < 2) return 0
        return (bytes[1].toInt() and 0xFF shl 8) or (bytes[0].toInt() and 0xFF)
    }


    private fun addEntryToChart(value: Float) {
        dataPoints.add(Entry(xValue, value))
        xValue += 1f

        if (dataPoints.size > 100) {
            dataPoints.removeAt(0)
            dataPoints.forEachIndexed { index, entry ->
                entry.x = index.toFloat()
            }
            xValue = dataPoints.size.toFloat()
        }

        val dataSet = LineDataSet(dataPoints, "ECG")
        dataSet.apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }

        lineChart.data = LineData(dataSet)
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
        lineChart.moveViewToX(xValue)
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