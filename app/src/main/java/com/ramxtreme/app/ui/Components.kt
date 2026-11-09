package com.ramxtreme.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val Blue = Color(0xFF2E6BFF)
val Violet = Color(0xFFA855F7)
val Night = Color(0xFF070B18)
val Panel = Color(0xFF111833)
val Track = Color(0xFF1C2547)
val Good = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFF87171)

private val Scheme = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Violet,
    onSecondary = Color.White,
    background = Night,
    onBackground = Color(0xFFE6EAFF),
    surface = Panel,
    onSurface = Color(0xFFE6EAFF),
    surfaceVariant = Track,
    onSurfaceVariant = Color(0xFF9AA6D6),
    error = Bad,
)

@Composable
fun RamXtremeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

@Composable
fun RxCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Violet)
            }
            content()
        }
    }
}

/** Une valeur absente s'affiche toujours « Non accessible », jamais un chiffre inventé. */
@Composable
fun StatRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value ?: "Non accessible",
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
fun RamGauge(fraction: Float?, center: String, sub: String) {
    val animated by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(800), label = "jauge")
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = Track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            drawArc(
                brush = Brush.linearGradient(listOf(Blue, Violet)),
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Courbe temps réel ; `values` sont des fractions 0..1. */
@Composable
fun Spark(values: List<Float>, color: Color, slots: Int = 60) {
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        if (values.size < 2) return@Canvas
        val step = size.width / (slots - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height * (1f - v.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
