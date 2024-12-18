import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.android_ecg.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class ChartManager(private val context: Context, private val lineChart: LineChart) {
    private val dataPoints = ArrayList<Entry>()
    private var xValue = 0f

    init {
        setupChart()
    }

    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setDrawGridBackground(true)

            val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.ecarg_logo)
            backgroundDrawable?.alpha = 50
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

            setGridBackgroundColor(Color.argb(20, 255, 255, 255))

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

    fun addEntryToChart(value: Float) {
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
}