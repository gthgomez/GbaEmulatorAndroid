package com.gba.emulator.shell.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) {
            return current
        }
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
