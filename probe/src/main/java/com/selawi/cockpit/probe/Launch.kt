package com.selawi.cockpit.probe

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Starts an activity and reports a missing app instead of crashing. */
fun Context.tryStart(intent: Intent, failure: String = "No app can open this"): Boolean {
    return try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
        false
    } catch (e: SecurityException) {
        Toast.makeText(this, "Blocked: ${e.message}", Toast.LENGTH_LONG).show()
        false
    } catch (e: RuntimeException) {
        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        false
    }
}

fun Context.isInstalled(packageName: String): Boolean =
    runCatching { packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)
