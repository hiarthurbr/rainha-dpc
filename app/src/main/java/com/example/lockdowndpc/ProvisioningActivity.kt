package com.example.lockdowndpc

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView

/** Handles the Android 10+ integrated DPC flow and the legacy managed-device action. */
class ProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Applying device policy…"
            textSize = 18f
            setPadding(32, 40, 32, 40)
        })
        persistNonSecretEnrollmentExtras()

        when (intent.action) {
            DevicePolicyManager.ACTION_GET_PROVISIONING_MODE -> returnProvisioningMode()
            DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE,
            "android.app.action.PROVISION_MANAGED_DEVICE" -> completeProvisioning()
            else -> finish()
        }
    }

    private fun persistNonSecretEnrollmentExtras() {
        // QR payloads may carry a non-secret enrollment reference for a future management server.
        // Never put passwords, API tokens or private keys in this bundle.
        val extras = intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        val enrollmentId = extras?.getString("enrollmentId")?.trim().orEmpty()
        if (enrollmentId.isNotEmpty()) {
            getSharedPreferences("enrollment", MODE_PRIVATE).edit()
                .putString("enrollment_id", enrollmentId)
                .apply()
        }
    }

    private fun returnProvisioningMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val allowedModes = intent.getIntArrayExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
        )
        val fullyManagedMode = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        if (allowedModes != null && !allowedModes.contains(fullyManagedMode)) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            fullyManagedMode
        ).putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun completeProvisioning() {
        Thread {
            val ready = try {
                if (PolicyEngine.isDeviceOwner(this)) {
                    PolicyEngine.apply(this, ConfigStore.load(this))
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
            runOnUiThread {
                if (ready) {
                    setResult(RESULT_OK)
                    startActivity(Intent(this, KioskActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
        }.start()
    }
}
