# Lockdown DPC (starter)

A small Android Device Policy Controller (DPC) starter for **company-owned, fully managed devices**. It uses Android's native `DevicePolicyManager` APIs and follows the device-owner / dedicated-device pattern used by Android Enterprise and demonstrated in Google's TestDPC project.

This is an initial, on-device-managed implementation. It is not a production EMM and does not include a cloud service yet.

## What is included

- **QR provisioning hooks** for the Android Enterprise DPC setup flow:
  - Android 7–9 legacy managed-device action handler.
  - Android 10+ `ACTION_GET_PROVISIONING_MODE` and `ACTION_ADMIN_POLICY_COMPLIANCE` handlers.
- **Dedicated-device launcher** that shows only selected installed launcher apps.
- **Lock-task allowlist** containing the DPC plus selected apps. In kiosk mode, Android restricts the foreground task to those packages.
- **Settings restrictions** for factory reset from Settings, safe boot, user creation/removal, debugging, USB file transfer, sideloading, app uninstallation/app controls, Bluetooth/location configuration, account changes, date/time, credentials and VPN configuration. Wi-Fi configuration is not restricted by this preset, and Play Store app installs remain possible for staging. To use either during staging, the admin must temporarily allow the relevant system app (for example Settings or Play Store) in the app list. Only allowlisted packages can be launched in kiosk mode.
- **Chrome managed configuration** using `DevicePolicyManager.setApplicationRestrictions()` for homepage, URL block/allow lists and Incognito mode.
- **Local admin panel** protected by an administrator PIN. The PIN is stored as a salted PBKDF2-HMAC-SHA1 hash.
- **Boot receiver** that reapplies the saved policies after a reboot.
- **QR generator utility** that reads the signing-certificate checksum from a signed APK using `apksigner` and creates the provisioning QR code.

## Important scope and limitations

1. **Device owner is required.** This is not intended for an ordinary personal device or a normal Play Store install. It must be enrolled as a fully managed device, normally during initial setup / after a factory reset.
2. **Test with a spare device.** An incorrect device-owner policy can make a device difficult to operate. Keep a recovery and reprovisioning plan before deploying to users.
3. **Installed apps only.** The starter lets an admin allowlist apps already installed on the device. It does not yet distribute apps or silently install them from Managed Google Play. In Android 9+, the lock-task configuration leaves the power menu available for recovery while suppressing Home, Recents, notifications and keyguard during kiosk mode.
4. **No cloud console yet.** The first version stores policy locally and provides an on-device admin screen. `PolicyConfig` is deliberately transport-neutral, so a future HTTPS policy API can feed the same model. The recommended cloud design is described below.
5. **Chrome policy coverage varies.** Only policies supported by the installed Chrome for Android version will be enforced. The code uses Chrome's Android restriction names (`HomepageLocation`, `URLBlocklist`, `URLAllowlist`, `IncognitoModeAvailability`).
6. **System/OEM differences exist.** Android manufacturers can add settings and privileged system apps outside this starter's restriction preset. Lock-task mode is the main app allowlist enforcement; this project does not attempt to hide every system package.
7. **Physical recovery is not defeated.** `DISALLOW_FACTORY_RESET` blocks factory reset from Settings, but cannot guarantee protection against recovery/bootloader reflashing or OEM service procedures. For fleet deployments, combine device-owner management with OEM zero-touch enrollment / firmware controls where available.
8. **Admin PIN recovery is manual.** There is no remote reset or PIN throttle in this starter. Add an authenticated recovery workflow and attempt throttling before real deployment.
9. **Custom DPC approval / Play EMM API.** This project uses local Android framework APIs. Managed Google Play app distribution, managed Google Play accounts, Play EMM API capabilities and broad commercial EMM deployment are separate integrations and may require Android Enterprise approval. Current Android guidance notes that new custom DPC registrations for the Google Play EMM API are no longer being accepted by default.

## Build

Open this folder in Android Studio (recent version with Android Gradle Plugin 8.7.3 support) and sync the project. It targets `compileSdk 35`, `targetSdk 35`, `minSdk 24`.

From a machine with Android SDK / Build Tools and Gradle 8.9 installed:

```bash
cd lockdown-dpc
gradle :app:assembleDebug
```

The generated debug APK is normally at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This archive does not bundle Gradle wrapper scripts/JAR; use Android Studio or a local Gradle 8.9 installation with the Android Gradle Plugin declared in `build.gradle.kts`.

### Before first real deployment

- Change `applicationId` and `namespace` from `com.example.lockdowndpc` to your permanent package name **before** generating any QR codes or enrolling devices.
- Build a release APK signed with your permanent release key. Keep that key stable for all future DPC updates; Android requires updates to be signed by the same signer.
- Host the APK at a stable HTTPS URL accessible during Android Setup Wizard.
- Replace the example settings and UI branding.
- Review every restriction against the device's actual usage requirements; for example, disabling Wi-Fi configuration can strand a device that needs network changes.

## Generate a provisioning QR code

The QR generator uses the APK signing certificate SHA-256 digest, URL-safe Base64 encoded, for `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM`. It is not the APK file's SHA-256 checksum.

Install the Python dependency:

```bash
python -m pip install -r qr-tools/requirements.txt
```

Build and sign the release APK, then run:

```bash
python qr-tools/generate_qr.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --apk-url https://downloads.example.com/lockdown-dpc.apk \
  --package com.example.lockdowndpc \
  --out lockdown-device-qr.png \
  --json-out provisioning.json
```

`apksigner` from the Android SDK Build Tools must be on `PATH`. If the application ID was changed, use the new value for `--package`.

### Enroll a device

1. Factory reset a test device.
2. At the Android welcome screen, tap the same spot six times to open QR provisioning (the exact prompt can vary by manufacturer / Setup Wizard version).
3. Connect to Wi-Fi if prompted and scan `lockdown-device-qr.png`.
4. Complete the fully managed device setup.
5. The DPC becomes the device owner and opens the managed home screen. On first run, open Admin, select the already-installed apps, configure Chrome, and set the admin PIN.

QR provisioning is supported for fully managed / dedicated devices on Android 7.0+. The DPC's integrated provisioning-mode and compliance activities cover the modern Android 10+ flow.

## Test without QR (development only)

On a factory-reset test device, install the debug APK during initial setup (or install via ADB before assigning ownership, subject to device setup state) and run:

```bash
adb shell dpm set-device-owner com.example.lockdowndpc/.AdminReceiver
```

Then launch **Lockdown DPC**. `dpm set-device-owner` will fail if the device has accounts, is already provisioned in an incompatible state, or already has another device owner. Use a dedicated test device.

Useful diagnostics:

```bash
adb shell dumpsys device_policy
adb shell dumpsys activity | grep -i locktask
```

## Configure Chrome

The admin screen accepts one URL pattern per line. A typical allow-only example is:

**Blocked URLs**

```text
*
```

**Allowed exceptions**

```text
https://portal.example.com
https://help.example.com
```

Chrome's URL allowlist entries are exceptions to the blocklist. The most specific match determines the result. Review Google's URL filter syntax and test against the exact Chrome version on the target devices. This policy applies to Chrome; it does not create a network-wide firewall and does not automatically constrain other browsers or apps with embedded WebViews.

## How this relates to TestDPC

The supplied `googlesamples/android-testdpc` repository is a valuable reference and testing tool. Its current README describes a Bazel-based sample DPC. This starter is a smaller Kotlin implementation rather than a fork of its source tree, so it is easier to extend as a dedicated-device product. It uses the same Android device-admin / device-owner foundation and native policy APIs.

## Suggested cloud-console architecture (next increment)

To add remote management without rewriting the policy engine:

1. Add a small HTTPS backend with tenant/admin authentication, device records, policy versions and audit history.
2. During QR enrollment, pass a **non-secret one-time enrollment ID** in `PROVISIONING_ADMIN_EXTRAS_BUNDLE`; exchange it for a device-bound credential after the DPC reaches the backend.
3. Have the DPC periodically fetch a signed `PolicyConfig` document over TLS. Verify the signature in the app before applying it. Do not place long-lived bearer tokens, passwords or private keys in the QR payload.
4. Use Firebase Cloud Messaging only as a wake-up signal; the DPC should fetch the authoritative policy over HTTPS.
5. Report applied policy version and enforcement errors to the backend. Store an offline last-known-good policy so the device remains managed without connectivity.
6. Build a browser admin UI for device groups, approved package lists, Chrome policy fields, system restrictions, enrollment QR codes, policy rollout and audit logs.

## Project layout

```text
app/src/main/                  Android DPC implementation
  AndroidManifest.xml           provisioning actions, launcher and receiver
  java/.../PolicyEngine.kt      DevicePolicyManager enforcement
  java/.../PolicyConfig.kt      local policy model and storage
  java/.../AdminActivity.kt     on-device admin console
  java/.../KioskActivity.kt     allowlisted launcher
  java/.../ProvisioningActivity.kt QR / Setup Wizard integration
qr-tools/generate_qr.py         creates provisioning QR from signed APK
```

## References

- [Android dedicated devices overview](https://developer.android.com/work/dpc/dedicated-devices)
- [Build a device policy controller](https://developer.android.com/work/dpc/build-dpc)
- [Enroll and provision a device](https://developers.google.com/android/management/provision-device)
- [DevicePolicyManager API](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [UserManager restrictions](https://developer.android.com/reference/android/os/UserManager)
- [Chrome Enterprise policy list](https://chromeenterprise.google/policies/)
- [Chrome URL blocklist / allowlist guide](https://support.google.com/chrome/a/answer/7532419)
- [Google TestDPC source](https://github.com/googlesamples/android-testdpc)
