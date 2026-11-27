package br.arthur.rainha.dpc

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class KioskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        render()
        if (PolicyEngine.isDeviceOwner(this) && !PinStore.hasPin(this)) {
            startActivity(Intent(this, AdminActivity::class.java))
        }
        enterLockTaskIfConfigured()
    }

    private fun render() {
        val config = ConfigStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(20))
        }
        val title = TextView(this).apply {
            text = "Approved apps"
            textSize = 26f
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(title)

        if (!PolicyEngine.isDeviceOwner(this)) {
            root.addView(TextView(this).apply {
                text =
                    "Not yet enrolled as a fully managed device. Factory-reset the device and provision it with the generated QR code, or use the ADB test command in README.md."
                textSize = 15f
            })
        }

        val available = InstalledApps.launchableApps(this)
            .filter { config.allowedPackages.contains(it.activityInfo.packageName) }
        if (available.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No approved apps are configured. Open Admin to select the apps users should be able to use."
                textSize = 15f
                setPadding(0, dp(16), 0, dp(16))
            })
        }

        available.forEach { info ->
            val packageName = info.activityInfo.packageName
            val appButton = Button(this).apply {
                text = "${info.loadLabel(packageManager)}\n$packageName"
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage(packageName)
                    if (launch == null) {
                        Toast.makeText(this@KioskActivity, "No launch activity for $packageName", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            startActivity(launch)
                        } catch (e: Exception) {
                            Toast.makeText(this@KioskActivity, "Could not open app: ${e.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
            root.addView(appButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply {
                bottomMargin = dp(8)
            })
        }

        val spacer = TextView(this).apply { text = "" }
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val admin = Button(this).apply {
            text = "Admin settings"
            setOnClickListener { requestAdminAccess() }
        }
        root.addView(admin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun requestAdminAccess() {
        if (!PinStore.hasPin(this)) {
            startActivity(Intent(this, AdminActivity::class.java))
            return
        }
        val input = EditText(this).apply {
            hint = "Admin PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Administrator access")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unlock") { _, _ ->
                if (PinStore.verify(this, input.text.toString())) {
                    startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun enterLockTaskIfConfigured() {
        val config = ConfigStore.load(this)
        if (!PolicyEngine.isDeviceOwner(this)) return
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mode = activityManager.lockTaskModeState
        if (!config.kioskMode) {
            if (mode == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                try {
                    stopLockTask()
                } catch (_: Exception) {
                    // The system may already have exited lock-task mode.
                }
            }
            return
        }
        if (!dpm.isLockTaskPermitted(packageName)) return
        if (mode == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask()
            } catch (_: Exception) {
                // Policy may be applied while the system is still finalizing provisioning.
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
