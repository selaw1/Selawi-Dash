package com.selawi.cockpit.core.platform

import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Starts an activity; a missing or blocked app shows a message instead of crashing the cockpit. */
fun Context.tryStart(intent: Intent, failure: String = "This app could not be opened"): Boolean {
    return try {
        if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        true
    } catch (e: RuntimeException) {
        Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
        false
    }
}

/** Returns to BYD's own home screen. */
fun Context.openCarHome() {
    tryStart(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        "The car's home screen could not be opened",
    )
}
