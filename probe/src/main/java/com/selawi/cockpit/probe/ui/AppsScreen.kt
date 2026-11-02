package com.selawi.cockpit.probe.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.selawi.cockpit.probe.tryStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppEntry(val label: String, val component: ComponentName, val icon: ImageBitmap?)

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Apps", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Note("Tap an app to open it. Use the car's home button or the button below to get back to BYD's own screen.")
        ButtonRow {
            BigButton("Open BYD home") {
                context.tryStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "No home screen found")
            }
        }
        val list = apps
        if (list == null) {
            Note("Loading apps…")
        } else {
            Note("${list.size} apps")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(list, key = { it.component.flattenToString() }) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PanelHigh)
                            .clickable {
                                context.tryStart(
                                    Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_LAUNCHER)
                                        .setComponent(app.component)
                                        .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                                    "${app.label} could not be opened",
                                )
                            }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(64.dp)) }
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            val icon = runCatching { info.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull()
            AppEntry(
                label = info.loadLabel(pm).toString(),
                component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                icon = icon,
            )
        }
        .sortedBy { it.label.lowercase() }
}
