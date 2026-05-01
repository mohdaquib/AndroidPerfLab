package com.aquib.androidperflab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aquib.androidperflab.ui.theme.AndroidPerfLabTheme

private val BarColorBefore = Color(0xFFE53935)
private val BarColorAfter  = Color(0xFF43A047)

private data class Metric(
    val label: String,
    val before: Float,
    val after: Float,
    val unit: String = "ms",
)

private data class Section(
    val title: String,
    val items: List<Metric>,
)

private val SECTIONS = listOf(
    Section(
        title = "App Startup",
        items = listOf(
            Metric("Cold Start TTID", before = 1200f, after = 250f),
            Metric("Warm Start TTID", before = 320f, after = 160f),
            Metric("Hot Start TTID",  before = 80f,   after = 55f),
        ),
    ),
    Section(
        title = "Scroll Rendering",
        items = listOf(
            Metric("P99 Frame Duration", before = 28f, after = 8f),
            Metric("P90 Frame Duration", before = 18f, after = 6f),
        ),
    ),
    Section(
        title = "Recompositions (Detail Screen)",
        items = listOf(
            Metric("Like Button Click",       before = 12f, after = 1f, unit = "×"),
            Metric("Bookmark Button Click",   before = 11f, after = 1f, unit = "×"),
            Metric("Tick Effect / 500ms",     before = 9f,  after = 1f, unit = "×"),
        ),
    ),
)

@Composable
fun BenchmarkDashboard(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val textMeasurer = rememberTextMeasurer()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back to feed") }
        Text(
            text = "Benchmark Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LegendDot(color = BarColorBefore, label = "Before")
            LegendDot(color = BarColorAfter,  label = "After (optimized)")
        }
        SECTIONS.forEach { section ->
            SectionCard(section = section, textMeasurer = textMeasurer)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawRect(color) }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionCard(section: Section, textMeasurer: TextMeasurer) {
    val maxValue = remember(section) {
        section.items.maxOf { maxOf(it.before, it.after) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            section.items.forEach { metric ->
                MetricBars(metric = metric, maxValue = maxValue, textMeasurer = textMeasurer)
            }
        }
    }
}

@Composable
private fun MetricBars(metric: Metric, maxValue: Float, textMeasurer: TextMeasurer) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = metric.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        BarRow(
            rowLabel = "Before",
            value = metric.before,
            maxValue = maxValue,
            unit = metric.unit,
            barColor = BarColorBefore,
            textMeasurer = textMeasurer,
        )
        BarRow(
            rowLabel = "After",
            value = metric.after,
            maxValue = maxValue,
            unit = metric.unit,
            barColor = BarColorAfter,
            textMeasurer = textMeasurer,
        )
    }
}

@Composable
private fun BarRow(
    rowLabel: String,
    value: Float,
    maxValue: Float,
    unit: String,
    barColor: Color,
    textMeasurer: TextMeasurer,
) {
    val label = remember(value, unit) { formatValue(value, unit) }
    val outsideStyle: TextStyle = remember(barColor) {
        TextStyle(fontSize = 11.sp, color = barColor, fontWeight = FontWeight.Medium)
    }
    val insideStyle: TextStyle = remember {
        TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
    val outsideMeasured: TextLayoutResult = remember(label, barColor) {
        textMeasurer.measure(label, outsideStyle)
    }
    val insideMeasured: TextLayoutResult = remember(label) {
        textMeasurer.measure(label, insideStyle)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = rowLabel,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelSmall,
            color = barColor,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
        ) {
            // Reserve 35% of the canvas width so the value label always has room.
            val maxBarWidth = size.width * 0.65f
            val barW = ((value / maxValue) * maxBarWidth).coerceAtLeast(2f)
            val barH = size.height * 0.75f
            val barTop = (size.height - barH) / 2f

            drawRect(
                color = barColor,
                topLeft = Offset(0f, barTop),
                size = Size(barW, barH),
            )

            val labelX = barW + 6f
            val labelY = (size.height - outsideMeasured.size.height) / 2f
            if (labelX + outsideMeasured.size.width <= size.width) {
                drawText(outsideMeasured, topLeft = Offset(labelX, labelY))
            } else {
                // Bar fills the reserved area — render value inside in white.
                val insideX = (barW - insideMeasured.size.width - 4f).coerceAtLeast(4f)
                val insideY = (size.height - insideMeasured.size.height) / 2f
                drawText(insideMeasured, topLeft = Offset(insideX, insideY))
            }
        }
    }
}

private fun formatValue(value: Float, unit: String): String =
    if (value == value.toLong().toFloat()) "${value.toLong()}$unit"
    else "%.1f$unit".format(value)

@Preview(showBackground = true)
@Composable
private fun BenchmarkDashboardPreview() {
    AndroidPerfLabTheme {
        BenchmarkDashboard(onBack = {})
    }
}
