package br.arthur.rainha.dpc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import org.json.JSONArray

object PolicyEngine {
    const val CHROME_PACKAGE = "com.android.chrome"

    private val settingsRestrictions = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_REMOVE_USER,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_USB_FILE_TRANSFER,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_CONFIG_BLUETOOTH,
        UserManager.DISALLOW_CONFIG_LOCATION,
        UserManager.DISALLOW_MODIFY_ACCOUNTS,
        UserManager.DISALLOW_CONFIG_CREDENTIALS,
        UserManager.DISALLOW_CONFIG_VPN
    )

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun apply(context: Context, config: PolicyConfig) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        check(dpm.isDeviceOwnerApp(context.packageName)) {
            "This app must be provisioned as the device owner on a fully managed device."
        }
        val admin = ComponentName(context, AdminReceiver::class.java)

        // The DPC's own package must be permitted so its HOME activity can host the kiosk.
        val lockTaskPackages = (config.allowedPackages + context.packageName).distinct().toTypedArray()
        dpm.setLockTaskPackages(admin, lockTaskPackages)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val features = if (config.kioskMode) {
                // Keep the power menu available for operational recovery; Home, Recents,
                // notifications, status-bar info and keyguard remain disabled in lock task.
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                        (if (config.disableStatusBar) 0 else DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)
            } else {
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            }
            dpm.setLockTaskFeatures(admin, features)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dpm.setStatusBarDisabled(admin, config.disableStatusBar)
        }

        // Explicitly add or clear every restriction managed by this starter, so that an admin
        // can later relax a setting instead of leaving stale restrictions behind.
        val applicableRestrictions = settingsRestrictions.toMutableList().apply {
            // Added in API 28; avoid sending an unknown restriction to older framework versions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(UserManager.DISALLOW_CONFIG_DATE_TIME)
        }
        applicableRestrictions.forEach { restriction ->
            if (config.restrictSystemSettings) {
                dpm.addUserRestriction(admin, restriction)
            } else {
                dpm.clearUserRestriction(admin, restriction)
            }
        }

        applyChromeRestrictions(context, dpm, admin, config)
        pinHomeLauncher(dpm, admin, context)
    }

    private fun applyChromeRestrictions(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
        config: PolicyConfig
    ) {
        val chromeInstalled = try {
            context.packageManager.getPackageInfo(CHROME_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!chromeInstalled) return

        val restrictions = Bundle()
        if (config.chromeHomepage.isNotBlank()) {
            restrictions.putString("HomepageLocation", config.chromeHomepage.trim())
        }
        if (config.chromeBlocklist.isNotEmpty()) {
            // Chrome's Android restriction schema represents list policies as JSON strings.
            restrictions.putString("URLBlocklist", JSONArray(config.chromeBlocklist).toString())
        }
        if (config.chromeAllowlist.isNotEmpty()) {
            restrictions.putString("URLAllowlist", JSONArray(config.chromeAllowlist).toString())
        }
        if (config.disableChromeIncognito) {
            restrictions.putInt("IncognitoModeAvailability", 1)
        }
        dpm.setApplicationRestrictions(admin, CHROME_PACKAGE, restrictions)
    }

    private fun pinHomeLauncher(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        context: Context
    ) {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            filter,
            ComponentName(context, KioskActivity::class.java)
        )
    }
}
