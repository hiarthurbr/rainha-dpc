package com.example.lockdowndpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PolicyEngine.isDeviceOwner(context)) return
        val pendingResult = goAsync()
        Thread {
            try {
                PolicyEngine.apply(context, ConfigStore.load(context))
            } catch (_: Exception) {
                // Avoid a boot-looping receiver. The policy can be reviewed from the DPC admin screen.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
