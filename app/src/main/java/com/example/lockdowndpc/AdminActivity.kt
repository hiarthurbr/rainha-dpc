package com.example.lockdowndpc

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface

class AdminActivity : Activity() {
    private val appChecks = mutableMapOf<String, CheckBox>()
    private lateinit var homepage: EditText
    private lateinit var blocklist: EditText
    private lateinit var allowlist: EditText
    private lateinit var incognito: CheckBox
    private lateinit var kiosk: CheckBox
    private lateinit var statusBar: CheckBox
    private lateinit var restrictSettings: CheckBox
    private lateinit var pin: EditText
    private lateinit var confirmPin: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PolicyEngine.isDeviceOwner(this)) {
            setContentView(messageView("This app is not the device owner. Provision the device as fully managed before applying device-wide policies."))
            return
        }

        val config = ConfigStore.load(this)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(text("Device policy", 25f, true))
        content.addView(text("Choose which apps appear in the managed home screen. The kiosk policy permits only this DPC and the selected packages to enter lock-task mode.", 14f))
        content.addView(section("Approved apps"))
        content.addView(text("Select installed apps to make them available. Apps installed later must be selected here before users can launch them from the managed home screen.", 13f))

        val installed = InstalledApps.launchableApps(this)
        if (installed.isEmpty()) {
            content.addView(text("No launcher apps were found. Install the required apps first, then reopen this screen.", 13f))
        }
        installed.forEach { info ->
            val packageName = info.activityInfo.packageName
            val check = CheckBox(this).apply {
                text = "${info.loadLabel(packageManager)}  ($packageName)"
                isChecked = config.allowedPackages.contains(packageName)
                textSize = 14f
            }
            appChecks[packageName] = check
            content.addView(check)
        }

        content.addView(section("Chrome configuration"))
        homepage = editText("https://example.org", config.chromeHomepage, false).also {
            content.addView(text("Homepage URL", 13f, true))
            content.addView(it)
        }
        blocklist = editText("One URL pattern per line", config.chromeBlocklist.joinToString("\n"), true).also {
            content.addView(text("Blocked URLs (optional)", 13f, true))
            content.addView(it)
        }
        allowlist = editText("One URL pattern per line", config.chromeAllowlist.joinToString("\n"), true).also {
            content.addView(text("Allowed exceptions (optional)", 13f, true))
            content.addView(it)
        }
        incognito = CheckBox(this).apply {
            text = "Disable Chrome Incognito mode"
            isChecked = config.disableChromeIncognito
        }
        content.addView(incognito)
        content.addView(text("Chrome policy names are passed as Android managed-configuration keys. Support depends on the installed Chrome version and policy; see the README for examples.", 12f))

        content.addView(section("Device lockdown"))
        kiosk = CheckBox(this).apply {
            text = "Enable kiosk / lock-task mode"
            isChecked = config.kioskMode
        }
        statusBar = CheckBox(this).apply {
            text = "Disable status bar and notification shade"
            isChecked = config.disableStatusBar
        }
        restrictSettings = CheckBox(this).apply {
            text = "Restrict sensitive system settings"
            isChecked = config.restrictSystemSettings
        }
        content.addView(kiosk)
        content.addView(statusBar)
        content.addView(restrictSettings)
        content.addView(text("The settings restriction preset blocks factory reset, safe boot, adding/removing users, USB file transfer, debugging, sideloading, app uninstall and app controls, Bluetooth/location configuration, account changes, credentials and VPN configuration; date/time is restricted on Android 9+. Wi-Fi configuration is not restricted by this preset, and Play Store app installs remain possible for staging. To use either during staging, the admin must temporarily allow the relevant system app (for example Settings or Play Store) in the app list. Only allowlisted packages can be launched in kiosk mode.", 12f))

        content.addView(section("Admin access PIN"))
        content.addView(text("Set a PIN of at least 6 digits. The managed home screen asks for it before opening this admin screen. Keep it somewhere safe; this starter has no remote PIN recovery.", 13f))
        pin = editText("New admin PIN (6+ digits)", "", false).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        confirmPin = editText("Confirm admin PIN", "", false).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        content.addView(pin)
        content.addView(confirmPin)

        val save = Button(this).apply {
            text = "Save and apply policies"
            setOnClickListener { saveAndApply() }
        }
        content.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(22)
        })
        val cancel = Button(this).apply {
            text = "Return to managed home"
            setOnClickListener { finish() }
        }
        content.addView(cancel)
    }

    private fun saveAndApply() {
        val newPin = pin.text.toString().trim()
        val confirm = confirmPin.text.toString().trim()
        if (!PinStore.hasPin(this) && newPin.isBlank()) {
            toast("Set an admin PIN before leaving setup.")
            return
        }
        if (newPin.isNotBlank()) {
            if (newPin.length < 6 || !newPin.all { it.isDigit() }) {
                toast("PIN must contain at least 6 digits.")
                return
            }
            if (newPin != confirm) {
                toast("PIN confirmation does not match.")
                return
            }
        }

        val config = PolicyConfig(
            allowedPackages = appChecks.filterValues { it.isChecked }.keys.toSet(),
            chromeHomepage = homepage.text.toString().trim(),
            chromeBlocklist = parseLines(blocklist.text.toString()),
            chromeAllowlist = parseLines(allowlist.text.toString()),
            disableChromeIncognito = incognito.isChecked,
            kioskMode = kiosk.isChecked,
            disableStatusBar = statusBar.isChecked,
            restrictSystemSettings = restrictSettings.isChecked
        )

        ConfigStore.save(this, config)
        if (newPin.isNotBlank()) PinStore.setPin(this, newPin)
        Toast.makeText(this, "Applying policies…", Toast.LENGTH_SHORT).show()
        Thread {
            val error = try {
                PolicyEngine.apply(this, config)
                null
            } catch (e: Exception) {
                e
            }
            runOnUiThread {
                if (error == null) {
                    toast("Policies applied.")
                    finish()
                } else {
                    toast("Could not apply policies: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }.start()
    }

    private fun parseLines(value: String): List<String> = value
        .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()

    private fun editText(hintText: String, value: String, multiline: Boolean): EditText = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 14f
        setPadding(dp(10), dp(8), dp(10), dp(8))
        if (multiline) {
            minLines = 2
            maxLines = 6
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun section(label: String) = TextView(this).apply {
        text = label
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun messageView(message: String) = TextView(this).apply {
        text = message
        textSize = 18f
        setPadding(dp(24), dp(36), dp(24), dp(36))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
