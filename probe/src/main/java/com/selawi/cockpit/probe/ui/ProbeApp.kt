package com.selawi.cockpit.probe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selawi.cockpit.probe.BuildConfig
import com.selawi.cockpit.probe.MainActivity

val Background = Color(0xFF0B0F14)
val Panel = Color(0xFF151C25)
val PanelHigh = Color(0xFF1E2833)
val Accent = Color(0xFF4FC3F7)
val Muted = Color(0xFF8C9AAB)
val Good = Color(0xFF66BB6A)
val Bad = Color(0xFFEF5350)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Background,
    surface = Panel,
    onSurface = Color.White,
    onBackground = Color.White,
)

private enum class Tab(val label: String) {
    Device("Device"), Screen("Screen"), Apps("Apps"), Maps("Maps"), Media("Media"), Voice("Voice")
}

@Composable
fun ProbeApp(activity: MainActivity) {
    MaterialTheme(colorScheme = colors) {
        var tab by rememberSaveable { mutableStateOf(Tab.Device) }
        Row(
            Modifier
                .fillMaxSize()
                .background(Background)
                .safeDrawingPadding(),
        ) {
            Column(
                Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Cockpit Probe", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Accent else Panel)
                            .clickable { tab = t }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(t.label, color = if (selected) Color.Black else Color.White, fontSize = 20.sp)
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                when (tab) {
                    Tab.Device -> DeviceScreen(activity)
                    Tab.Screen -> ScreenTestScreen(activity)
                    Tab.Apps -> AppsScreen()
                    Tab.Maps -> MapsScreen()
                    Tab.Media -> MediaScreen()
                    Tab.Voice -> VoiceScreen()
                }
            }
        }
    }
}

/** Scrollable page with a title. */
@Composable
fun Page(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelHigh, contentColor = Color.White),
    ) {
        Text(text, fontSize = 18.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun InfoLine(label: String, value: String, valueColor: Color = Color.White) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Muted, fontSize = 16.sp, modifier = Modifier.width(260.dp))
        Text(value, color = valueColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Note(text: String) {
    Text(text, color = Muted, fontSize = 15.sp)
}
