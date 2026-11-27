package br.arthur.rainha.dpc

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // For the legacy QR provisioning flow, establish the safe baseline as soon as the
        // device-admin component is enabled. The Android 10+ compliance activity reapplies it.
        if (PolicyEngine.isDeviceOwner(context)) {
            Thread {
                try {
                    PolicyEngine.apply(context, ConfigStore.load(context))
                } catch (_: Exception) {
                    // Admin can inspect status and retry from the app after provisioning completes.
                }
            }.start()
        }
    }
}
