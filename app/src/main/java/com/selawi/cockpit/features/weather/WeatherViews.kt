package com.selawi.cockpit.features.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private fun hourLabel(t: LocalTime, is24h: Boolean): String =
    if (is24h) t.format(DateTimeFormatter.ofPattern("HH")) else t.format(DateTimeFormatter.ofPattern("ha")).lowercase()

/** Slim forecast strip: the next six hours, then sunrise and sunset. */
@Composable
fun ForecastStrip(weather: Weather?, modifier: Modifier = Modifier, is24h: Boolean = false) {
    CockpitCard(modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (weather == null || weather.hours.isEmpty()) {
                Text("Forecast loading…", style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextTertiary, modifier = Modifier.weight(1f).padding(start = 10.dp))
            } else {
                Row(Modifier.weight(1f)) {
                    weather.hours.forEach { hour ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(hourLabel(hour.time, is24h), style = MaterialTheme.typography.labelSmall, color = CockpitColors.TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SkyIcon(hour.sky, hour.isDay, Modifier.size(20.dp), size = 20.dp)
                                Text("${hour.temperature}°", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp), color = CockpitColors.TextPrimary)
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(36.dp).background(CockpitColors.Stroke))
            val fmt = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")
            Column(Modifier.padding(end = 10.dp)) {
                Text("Sunrise " + (weather?.sunrise?.format(fmt) ?: "–"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp), color = CockpitColors.TextSecondary)
                Text("Sunset " + (weather?.sunset?.format(fmt) ?: "–"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp), color = CockpitColors.TextSecondary)
            }
        }
    }
}

/** Small drawn weather symbol: sun or moon, cloud, rain, snow, fog or storm. */
@Composable
fun SkyIcon(sky: Sky, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val sun = Color(0xFFFFC94D)
    val moon = CockpitColors.TextSecondary
    val cloud = CockpitColors.TextSecondary
    val water = CockpitColors.Accent
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (sky) {
            Sky.Clear -> if (isDay) drawSun(sun, s) else drawMoon(moon, s)
            Sky.PartlyCloudy -> {
                if (isDay) drawSun(sun, s * 0.75f, Offset(s * 0.32f, s * 0.32f)) else drawMoon(moon, s * 0.7f, Offset(s * 0.3f, s * 0.3f))
                drawCloud(cloud, s, dy = s * 0.12f)
            }
            Sky.Cloudy -> drawCloud(cloud, s)
            Sky.Fog -> for (i in 0..2) {
                val y = s * (0.35f + i * 0.17f)
                drawLine(cloud, Offset(s * 0.15f, y), Offset(s * 0.85f, y), strokeWidth = s * 0.07f)
            }
            Sky.Rain, Sky.Storm, Sky.Snow -> {
                drawCloud(cloud, s, dy = -s * 0.12f)
                for (i in 0..2) {
                    val x = s * (0.3f + i * 0.2f)
                    if (sky == Sky.Snow) drawCircle(water, s * 0.045f, Offset(x, s * 0.82f))
                    else drawLine(if (sky == Sky.Storm) sun else water, Offset(x, s * 0.7f), Offset(x - s * 0.06f, s * 0.92f), strokeWidth = s * 0.06f)
                }
            }
        }
    }
}

private fun DrawScope.drawSun(color: Color, s: Float, center: Offset = Offset(s / 2, s / 2)) {
    drawCircle(color, s * 0.2f, center)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val inner = Offset(center.x + (s * 0.3f * cos(a)).toFloat(), center.y + (s * 0.3f * sin(a)).toFloat())
        val outer = Offset(center.x + (s * 0.42f * cos(a)).toFloat(), center.y + (s * 0.42f * sin(a)).toFloat())
        drawLine(color, inner, outer, strokeWidth = s * 0.06f)
    }
}

private fun DrawScope.drawMoon(color: Color, s: Float, center: Offset = Offset(s / 2, s / 2)) {
    drawCircle(color, s * 0.3f, center, style = Stroke(width = s * 0.08f))
    drawCircle(color, s * 0.22f, Offset(center.x + s * 0.08f, center.y - s * 0.04f))
}

private fun DrawScope.drawCloud(color: Color, s: Float, dy: Float = 0f) {
    drawCircle(color, s * 0.17f, Offset(s * 0.35f, s * 0.58f + dy))
    drawCircle(color, s * 0.22f, Offset(s * 0.55f, s * 0.48f + dy))
    drawCircle(color, s * 0.15f, Offset(s * 0.74f, s * 0.6f + dy))
    drawRect(color, Offset(s * 0.35f, s * 0.58f + dy), androidx.compose.ui.geometry.Size(s * 0.39f, s * 0.17f))
}
