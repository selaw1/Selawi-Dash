package com.selawi.cockpit.probe

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

data class InfoRow(val label: String, val value: String)
data class InfoSection(val title: String, val rows: List<InfoRow>)

/** Collects everything we need to know about the head unit. Every probe is allowed to fail. */
object DeviceReport {

    fun collect(activity: Activity): List<InfoSection> = listOf(
        section("System") { system() },
        section("Screen") { screen(activity) },
        section("Hardware features") { features(activity) },
        section("Google services") { google(activity) },
        section("Voice") { voice(activity) },
        section("Network") { network(activity) },
        section("Known apps") { knownApps(activity) },
        section("Vendor packages") { vendorPackages(activity) },
        section("System properties") { systemProperties() },
    )

    fun asText(sections: List<InfoSection>): String = buildString {
        appendLine("Cockpit Probe ${BuildConfig.VERSION_NAME} report")
        for (s in sections) {
            appendLine()
            appendLine("== ${s.title} ==")
            for (r in s.rows) appendLine("${r.label}: ${r.value}")
        }
    }

    private fun section(title: String, block: MutableList<InfoRow>.() -> Unit): InfoSection {
        val rows = mutableListOf<InfoRow>()
        try {
            rows.block()
        } catch (t: Throwable) {
            rows += InfoRow("error", "${t.javaClass.simpleName}: ${t.message}")
        }
        return InfoSection(title, rows)
    }

    private fun MutableList<InfoRow>.row(label: String, value: Any?) {
        add(InfoRow(label, value?.toString() ?: "-"))
    }

    private fun MutableList<InfoRow>.system() {
        row("Manufacturer", Build.MANUFACTURER)
        row("Brand", Build.BRAND)
        row("Model", Build.MODEL)
        row("Device", Build.DEVICE)
        row("Product", Build.PRODUCT)
        row("Board", Build.BOARD)
        row("Hardware", Build.HARDWARE)
        if (Build.VERSION.SDK_INT >= 31) {
            row("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        row("Android version", Build.VERSION.RELEASE)
        row("API level", Build.VERSION.SDK_INT)
        row("Security patch", Build.VERSION.SECURITY_PATCH)
        row("Build display", Build.DISPLAY)
        row("Build ID", Build.ID)
        row("Incremental", Build.VERSION.INCREMENTAL)
        row("Fingerprint", Build.FINGERPRINT)
        row("Build type", "${Build.TYPE} / ${Build.TAGS}")
        row("Supported ABIs", Build.SUPPORTED_ABIS.joinToString())
        row("CPU cores", Runtime.getRuntime().availableProcessors())
        row("Locale", Locale.getDefault().toLanguageTag())
        row("Time zone", TimeZone.getDefault().id)
        row("Probe version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    private fun MutableList<InfoRow>.screen(activity: Activity) {
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = activity.windowManager.defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(real)
        row("Physical pixels", "${real.widthPixels} x ${real.heightPixels}")
        row("Density", "${real.densityDpi} dpi (scale ${real.density})")
        row("xdpi / ydpi", "%.1f / %.1f".format(real.xdpi, real.ydpi))
        val config = activity.resources.configuration
        row("Size in dp", "${config.screenWidthDp} x ${config.screenHeightDp}")
        row("Smallest width", "${config.smallestScreenWidthDp} dp")
        row("Font scale", config.fontScale)
        row("Refresh rate", "%.1f Hz".format(display.refreshRate))
        row("Modes", display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${"%.0f".format(it.refreshRate)}" })
        if (Build.VERSION.SDK_INT >= 30) {
            val b = activity.windowManager.currentWindowMetrics.bounds
            row("App window", "${b.width()} x ${b.height()}")
        }
        row("Night mode", if ((config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light")
        val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays.forEach { d ->
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            d.getRealMetrics(m)
            row("Display ${d.displayId}", "${d.name} ${m.widthPixels}x${m.heightPixels} flags=${d.flags}")
        }
    }

    private val featureNames = listOf(
        PackageManager.FEATURE_MICROPHONE,
        PackageManager.FEATURE_BLUETOOTH,
        PackageManager.FEATURE_BLUETOOTH_LE,
        PackageManager.FEATURE_WIFI,
        PackageManager.FEATURE_WIFI_DIRECT,
        PackageManager.FEATURE_LOCATION,
        PackageManager.FEATURE_LOCATION_GPS,
        PackageManager.FEATURE_LOCATION_NETWORK,
        PackageManager.FEATURE_TELEPHONY,
        PackageManager.FEATURE_CAMERA_ANY,
        PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH,
        PackageManager.FEATURE_PICTURE_IN_PICTURE,
        PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT,
        PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
        PackageManager.FEATURE_AUTOMOTIVE,
        PackageManager.FEATURE_APP_WIDGETS,
        PackageManager.FEATURE_OPENGLES_EXTENSION_PACK,
    )

    private fun MutableList<InfoRow>.features(context: Context) {
        val pm = context.packageManager
        featureNames.forEach { row(it.removePrefix("android.hardware.").removePrefix("android.software."), if (pm.hasSystemFeature(it)) "yes" else "no") }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        row("RAM", "${mem.totalMem / 1_048_576} MB total, ${mem.availMem / 1_048_576} MB free")
        row("App memory class", "${am.memoryClass} MB (large ${am.largeMemoryClass} MB)")
        row("OpenGL ES", am.deviceConfigurationInfo.glEsVersion)
        val stat = StatFs(context.filesDir.absolutePath)
        row("Free storage", "${stat.availableBytes / 1_048_576} MB")
    }

    private fun MutableList<InfoRow>.google(context: Context) {
        val api = GoogleApiAvailability.getInstance()
        val code = api.isGooglePlayServicesAvailable(context)
        row("Play Services", if (code == ConnectionResult.SUCCESS) "available" else "not usable: ${api.getErrorString(code)}")
        row("Play Services version", versionOf(context, "com.google.android.gms"))
        row("Play Store", versionOf(context, "com.android.vending"))
        row("Google Maps app", versionOf(context, "com.google.android.apps.maps"))
        row("Google app", versionOf(context, "com.google.android.googlequicksearchbox"))
        // Replacements for Google services, e.g. ReVanced GmsCore (a microG build).
        row("ReVanced GmsCore", versionOf(context, "app.revanced.android.gms"))
        row("Vanced microG", versionOf(context, "com.mgoogle.android.gms"))
        row("microG Services Framework", versionOf(context, "com.google.android.gsf"))
        row("Maps SDK key in this build", if (BuildConfig.HAS_MAPS_KEY) "yes" else "no")
    }

    private fun MutableList<InfoRow>.voice(context: Context) {
        val pm = context.packageManager
        row("Speech recognition available", SpeechRecognizer.isRecognitionAvailable(context))
        if (Build.VERSION.SDK_INT >= 31) {
            row("On-device recognition", SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        }
        val recognizers = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        row("Recognition services", recognizers.joinToString { it.serviceInfo.packageName }.ifEmpty { "none" })
        val tts = pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        row("TTS engines", tts.joinToString { it.serviceInfo.packageName }.ifEmpty { "none" })
        val assistant = runCatching {
            android.provider.Settings.Secure.getString(context.contentResolver, "assistant")
        }.getOrNull()
        row("Default assistant", assistant)
    }

    private fun MutableList<InfoRow>.network(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) {
            row("Active network", "none")
        } else {
            val transports = buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            }
            row("Active network", transports.joinToString().ifEmpty { "other" })
            row("Internet capability", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            row("Validated", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            row("Metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            row("Bandwidth down/up", "${caps.linkDownstreamBandwidthKbps} / ${caps.linkUpstreamBandwidthKbps} kbps")
        }
        row("HTTPS google.com", httpCheck("https://www.google.com/generate_204"))
        row("HTTPS openfreemap.org", httpCheck("https://tiles.openfreemap.org/styles/liberty"))
    }

    private fun httpCheck(url: String): String = runCatching {
        val start = System.nanoTime()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        val code = c.responseCode
        c.disconnect()
        "HTTP $code in ${(System.nanoTime() - start) / 1_000_000} ms"
    }.getOrElse { "failed: ${it.javaClass.simpleName} ${it.message}" }

    private val knownPackages = listOf(
        "Waze" to "com.waze",
        "Spotify" to "com.spotify.music",
        "YouTube" to "com.google.android.youtube",
        "YouTube ReVanced" to "app.revanced.android.youtube",
        "YouTube Music" to "com.google.android.apps.youtube.music",
        "YT Music ReVanced" to "app.revanced.android.apps.youtube.music",
        "SoundCloud" to "com.soundcloud.android",
        "Anghami" to "com.anghami",
        "WhatsApp" to "com.whatsapp",
        "Telegram" to "org.telegram.messenger",
        "Chrome" to "com.android.chrome",
        "Android System WebView" to "com.google.android.webview",
        "BYD home (launcher3)" to "com.android.launcher3",
    )

    private fun MutableList<InfoRow>.knownApps(context: Context) {
        knownPackages.forEach { (name, pkg) -> row(name, versionOf(context, pkg)) }
        val launchers = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        )
        row("Home apps", launchers.joinToString { "${it.activityInfo.packageName}/${it.activityInfo.name}" })
        val apps = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        row("Launchable apps", apps.size)
    }

    private fun MutableList<InfoRow>.vendorPackages(context: Context) {
        @Suppress("DEPRECATION")
        val all = context.packageManager.getInstalledPackages(0).map { it.packageName }.sorted()
        row("Installed packages", all.size)
        val vendor = all.filter { p -> listOf("byd", "dilink", "denza", "xdja").any { p.contains(it, ignoreCase = true) } }
        row("BYD-related (${vendor.size})", vendor.joinToString())
    }

    private val propPattern = Regex(
        "ro\\.build\\.(display\\.id|version\\.(release|sdk|incremental)|description|product)|" +
            "ro\\.product\\.(model|brand|manufacturer|device|name|cpu\\.abilist)|" +
            "ro\\.board\\.platform|ro\\.hardware|ro\\.sf\\.lcd_density|ro\\.build\\.characteristics|" +
            "byd|dilink",
        RegexOption.IGNORE_CASE,
    )

    private fun MutableList<InfoRow>.systemProperties() {
        val output = Runtime.getRuntime().exec(arrayOf("getprop")).inputStream.bufferedReader().use { it.readText() }
        output.lineSequence()
            .filter { propPattern.containsMatchIn(it) }
            .take(80)
            .forEach { line ->
                val key = line.substringAfter('[').substringBefore(']')
                val value = line.substringAfterLast('[').substringBeforeLast(']')
                row(key, value)
            }
    }

    private fun versionOf(context: Context, pkg: String): String = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        val enabled = context.packageManager.getApplicationInfo(pkg, 0).enabled
        "installed ${info.versionName}" + if (enabled) "" else " (disabled)"
    }.getOrDefault("not installed")
}
