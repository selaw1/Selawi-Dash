package com.selawi.cockpit.probe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.selawi.cockpit.probe.DeviceReport
import com.selawi.cockpit.probe.InfoSection
import com.selawi.cockpit.probe.MainActivity
import com.selawi.cockpit.probe.tryStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DeviceScreen(activity: MainActivity) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        sections = null
        sections = withContext(Dispatchers.IO) { DeviceReport.collect(activity) }
    }

    Page("Device report") {
        Note("Everything the cockpit needs to know about this head unit. Copy, save or share it, or take a photo of this screen.")
        ButtonRow {
            BigButton("Refresh") { refresh++ }
            BigButton("Copy") {
                sections?.let { copy(context, DeviceReport.asText(it)) }
            }
            BigButton("Save to Downloads") {
                sections?.let { save(context, DeviceReport.asText(it)) }
            }
            BigButton("Share") {
                sections?.let {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, DeviceReport.asText(it))
                    context.tryStart(Intent.createChooser(send, "Share report"), "No app can share text")
                }
            }
        }
        val current = sections
        if (current == null) {
            Note("Collecting…")
        } else {
            current.forEach { section ->
                Text(section.title, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                section.rows.forEach { row ->
                    val color = when {
                        row.value.startsWith("not") || row.value.startsWith("failed") || row.value == "no" || row.value == "false" -> Bad
                        row.value == "yes" || row.value == "true" || row.value == "available" -> Good
                        else -> Color.White
                    }
                    InfoLine(row.label, row.value, color)
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Cockpit Probe report", text))
    Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
}

private fun save(context: Context, text: String) {
    val name = "cockpit-probe-${System.currentTimeMillis() / 1000}.txt"
    val where = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("no Downloads folder")
            context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            "Downloads/$name"
        } else {
            val file = File(context.getExternalFilesDir(null), name)
            file.writeText(text)
            file.absolutePath
        }
    }
    Toast.makeText(
        context,
        where.fold({ "Saved to $it" }, { "Save failed: ${it.message}" }),
        Toast.LENGTH_LONG,
    ).show()
}
