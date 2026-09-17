package kr.me.nulld.iduohome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val TimeToSignalsGap = 6.dp

@Composable
fun StatusRail(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    iconSize: Dp = 40.dp,
    locationInUse: Boolean = false,
    foreground: Color = Color.White,
    timeToSignalsGap: Dp = TimeToSignalsGap,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_050L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    val timeFormatter = remember(format) { DateTimeFormatter.ofPattern(format) }
    val batteryVisual = batteryGaugeVisual(status.charging, status.fastCharging)
    val lightForeground = foreground.luminance() > .5f
    val batteryColor = when (batteryVisual) {
        BatteryGaugeVisual.NORMAL -> foreground
        BatteryGaugeVisual.CHARGING -> if (lightForeground) Color(0xFF4ADE80) else Color(0xFF15803D)
        BatteryGaugeVisual.FAST_CHARGING -> if (lightForeground) Color(0xFF60A5FA) else Color(0xFF2563EB)
    }
    val description = listOfNotNull(
        if (locationInUse) "Location in use" else null,
        now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, $format")),
        status.battery?.let {
            "Battery $it percent" + when (batteryVisual) {
                BatteryGaugeVisual.NORMAL -> ""
                BatteryGaugeVisual.CHARGING -> ", charging"
                BatteryGaugeVisual.FAST_CHARGING -> ", fast charging"
            }
        } ?: "Battery unavailable",
        if (status.wifiConnected) "Wi-Fi connected${status.wifiLevel?.let { ", signal $it of 4" } ?: ""}" else "Wi-Fi disconnected",
        if (status.airplane) "Airplane mode" else status.cellularLevel?.let { "Cellular signal $it of 4" } ?: "Cellular signal unavailable",
    ).joinToString(". ")
    val fontScale = LocalDensity.current.fontScale
    val shadowColor = if (lightForeground) Color.Black else Color.White
    val labelStyle = TextStyle(shadow = Shadow(shadowColor.copy(alpha = .3f), Offset(0f, 1f), 3f))
    val wifiVisual = wifiSignalVisual(status.wifiConnected, status.wifiLevel)
    val cellularVisual = cellularSignalVisual(status.cellularLevel, status.airplane)
    BoxWithConstraints(modifier.testTag("status-rail").semantics(mergeDescendants = true) { contentDescription = description }) {
        val availableWidth = (maxWidth - 4.dp).coerceAtLeast(28.dp)
        val visualSize = minOf(iconSize, availableWidth, if (compact) 40.dp else 52.dp)
        val timeSize = minOf(18f, availableWidth.value / (2.65f * fontScale)).sp
        val detailSize = minOf(11f, availableWidth.value / (3.45f * fontScale)).sp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Compact windows omit the reserve so status stays clear of the fixed dock.
            if (!compact) Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                // Callers currently leave this false; the slot waits for a truthful activity signal.
                if (locationInUse) Icon(Icons.Rounded.LocationOn, null, tint = foreground,
                    modifier = Modifier.size(18.dp))
            }
            Text(now.format(timeFormatter), color = foreground, fontSize = timeSize, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = labelStyle)
            Spacer(Modifier.height(timeToSignalsGap))
            Canvas(Modifier.size(visualSize)) {
                val w = size.width
                val center = Offset(w / 2, w / 2)
                val radius = w * .44f
                val ringWidth = w * .072f
                val stroke = Stroke(width = ringWidth, cap = StrokeCap.Round)
                val arcSize = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)
                drawArc(shadowColor.copy(alpha = .15f), 150f, 240f, false, topLeft, arcSize,
                    style = Stroke(width = ringWidth * 1.25f, cap = StrokeCap.Round))
                drawArc(foreground.copy(alpha = .32f), 150f, 240f, false, topLeft, arcSize, style = stroke)
                status.battery?.let { drawArc(batteryColor, 150f, 240f * it / 100, false, topLeft, arcSize, style = stroke) }
                // Wi-Fi glyph inside the battery arc; no fabricated bars for unknown readings.
                if (wifiVisual is WifiSignalVisual.Connected) {
                    for (i in 1..3) {
                        val r = w * (.12f + i * .067f)
                        val wifiTopLeft = Offset(center.x - r, w * .61f - r)
                        val wifiSize = Size(r * 2, r * 2)
                        drawArc(shadowColor.copy(alpha = .16f), 230f, 80f, false, wifiTopLeft, wifiSize,
                            style = Stroke(w * .073f, cap = StrokeCap.Round))
                        val strengthAlpha = signalAlpha(wifiVisual.elements[i])
                        drawArc(foreground.copy(alpha = strengthAlpha),
                            230f, 80f, false, wifiTopLeft, wifiSize, style = Stroke(w * .058f, cap = StrokeCap.Round))
                    }
                    drawCircle(shadowColor.copy(alpha = .16f), w * .052f, Offset(center.x, w * .60f))
                    drawCircle(foreground.copy(alpha = signalAlpha(wifiVisual.elements[0])),
                        w * .043f, Offset(center.x, w * .60f))
                } else {
                    drawLine(shadowColor.copy(alpha = .16f), Offset(w * .39f, w * .42f), Offset(w * .61f, w * .58f),
                        w * .073f, StrokeCap.Round)
                    drawLine(foreground.copy(alpha = .75f), Offset(w * .39f, w * .42f), Offset(w * .61f, w * .58f),
                        w * .058f, StrokeCap.Round)
                }
                val activeDots = (cellularVisual as? CellularSignalVisual.Available)?.activeDots ?: 0
                for (i in 0..4) {
                    val angle = Math.toRadians((130 - i * 20).toDouble())
                    val lit = i < activeDots
                    val dotCenter = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                    drawCircle(shadowColor.copy(alpha = .14f), w * .052f, dotCenter)
                    drawCircle(foreground.copy(alpha = if (lit) 1f else .3f), w * .043f, dotCenter)
                }
            }
            if (!compact) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (status.airplane) "Airplane" else status.battery?.let { "$it%" } ?: "—",
                        color = foreground.copy(alpha = .94f), fontSize = detailSize, fontWeight = FontWeight.Medium,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = labelStyle)
                    if (status.charging && !status.airplane && status.battery != null) {
                        Spacer(Modifier.width(1.dp))
                        Icon(Icons.Rounded.Bolt, null, tint = foreground.copy(alpha = .94f), modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

private fun signalAlpha(emphasis: SignalElementEmphasis): Float = when (emphasis) {
    SignalElementEmphasis.DIM -> .3f
    SignalElementEmphasis.NEUTRAL -> .62f
    SignalElementEmphasis.LIT -> 1f
}
