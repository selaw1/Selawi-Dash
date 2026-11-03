package com.selawi.cockpit.link

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF3DDCFF), onPrimary = Color.Black)) {
                LinkScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the access screen: start linking straight away.
        if (hasAccess()) CockpitClient.start(this)
    }

    private fun hasAccess() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}

@Composable
private fun LinkScreen() {
    val context = LocalContext.current
    val status by CockpitClient.status.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf(LinkPrefs.code(context)) }
    var address by remember { mutableStateOf(LinkPrefs.rawAddress(context)) }
    val access = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07090C))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cockpit Link", color = Color.White, fontSize = 30.sp)
        Text(
            "Shows Google Maps directions from this phone on the Selawi Cockpit in your car.",
            color = Color(0xFF8B98A8),
        )

        Text("1. Allow access", color = Color.White, fontSize = 20.sp)
        Text(
            if (access) "Allowed. Cockpit Link can read Google Maps' directions." else
                "Cockpit Link reads only Google Maps' navigation notification.",
            color = if (access) Color(0xFF66BB6A) else Color(0xFF8B98A8),
        )
        if (!access) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Allow notification access") }
        }

        Text("2. Pair with the car", color = Color.White, fontSize = 20.sp)
        Text(
            "Connect this phone and the car to the same Wi-Fi (the car's hotspot or this phone's). " +
                "Enter the code from the cockpit's Settings → Phone.",
            color = Color(0xFF8B98A8),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text("Pairing code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Car address (only if it is not found automatically)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            LinkPrefs.save(context, code, address)
            CockpitClient.restart(context)
        }) { Text("Save and connect") }

        Text("Status", color = Color.White, fontSize = 20.sp)
        Text(status, color = Color(0xFF3DDCFF))
        Button(onClick = {
            CockpitClient.send(
                """{"type":"nav","active":true,"title":"300 m","text":"Turn right onto Test Street","sub":"Test from Cockpit Link"}""",
            )
        }) { Text("Send a test direction") }
        Button(onClick = { CockpitClient.send("""{"type":"nav","active":false}""") }) { Text("Clear test direction") }
    }
}
