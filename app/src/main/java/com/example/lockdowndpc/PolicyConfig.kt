package com.example.lockdowndpc

import android.content.Context

/** Local policy model. Keep this model transport-neutral so a future server can send the same JSON. */
data class PolicyConfig(
    val allowedPackages: Set<String> = emptySet(),
    val chromeHomepage: String = "",
    val chromeBlocklist: List<String> = emptyList(),
    val chromeAllowlist: List<String> = emptyList(),
    val disableChromeIncognito: Boolean = true,
    val kioskMode: Boolean = true,
    val disableStatusBar: Boolean = true,
    val restrictSystemSettings: Boolean = true
)

object ConfigStore {
    private const val PREFS = "managed_policy"
    private const val KEY_ALLOWED = "allowed_packages"
    private const val KEY_CHROME_HOME = "chrome_home"
    private const val KEY_CHROME_BLOCK = "chrome_block"
    private const val KEY_CHROME_ALLOW = "chrome_allow"
    private const val KEY_CHROME_INCOGNITO = "chrome_incognito"
    private const val KEY_KIOSK = "kiosk"
    private const val KEY_STATUS = "status_bar"
    private const val KEY_RESTRICT = "restrict_settings"

    fun load(context: Context): PolicyConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PolicyConfig(
            allowedPackages = p.getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet(),
            chromeHomepage = p.getString(KEY_CHROME_HOME, "") ?: "",
            chromeBlocklist = splitLines(p.getString(KEY_CHROME_BLOCK, "") ?: ""),
            chromeAllowlist = splitLines(p.getString(KEY_CHROME_ALLOW, "") ?: ""),
            disableChromeIncognito = p.getBoolean(KEY_CHROME_INCOGNITO, true),
            kioskMode = p.getBoolean(KEY_KIOSK, true),
            disableStatusBar = p.getBoolean(KEY_STATUS, true),
            restrictSystemSettings = p.getBoolean(KEY_RESTRICT, true)
        )
    }

    fun save(context: Context, config: PolicyConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_ALLOWED, config.allowedPackages.toSet())
            .putString(KEY_CHROME_HOME, config.chromeHomepage.trim())
            .putString(KEY_CHROME_BLOCK, config.chromeBlocklist.joinToString("\n"))
            .putString(KEY_CHROME_ALLOW, config.chromeAllowlist.joinToString("\n"))
            .putBoolean(KEY_CHROME_INCOGNITO, config.disableChromeIncognito)
            .putBoolean(KEY_KIOSK, config.kioskMode)
            .putBoolean(KEY_STATUS, config.disableStatusBar)
            .putBoolean(KEY_RESTRICT, config.restrictSystemSettings)
            .apply()
    }

    private fun splitLines(value: String): List<String> = value
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
}
