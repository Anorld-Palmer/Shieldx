# ADMIN CONTROL — Android starter project

This is a starter Android app for devices you are authorised to administer. It includes:
- Installed-app list (subject to Android package visibility and OEM restrictions)
- A local VPN firewall that drops traffic for selected packages
- Device-owner-only app hide/suspend controls
- Device-owner-only runtime permission grant/deny/default controls
- Android's standard uninstall confirmation screen

## Build
Open this folder in Android Studio with Android SDK 35 installed, or upload it to a GitHub repository and add a GitHub Actions Android build workflow. Use JDK 17 and Android Gradle Plugin 8.7.3.

## Important limitations
- Basic Device Admin is not the same as Device Owner.
- Device-owner provisioning generally requires a new/factory-reset device during initial setup. Do not factory-reset a device unless you have backed up data and are authorised to manage it.
- Device-owner policies do not make every system package removable or every permission controllable.
- Uninstall is user-confirmed. Android does not allow a normal app to silently uninstall arbitrary packages.
- The VPN firewall uses a local TUN interface and discards packets for selected apps; it does not forward or inspect content. It is a starter implementation, not a certified enterprise security product. Test on a spare device first.
- VPN behaviour, package visibility, IPv6, OEM restrictions and system app handling vary by Android version and manufacturer.
- The app excludes itself from the blocked list. It requires the user's VPN consent.
- Do not use this to manage devices without their owner's permission.

## Device-owner provisioning for development
For development only, on an eligible test device that has been freshly reset and before completing device setup, connect ADB and use:

`adb shell dpm set-device-owner com.admincontrol/.AdminReceiver`

This can fail if the device is already provisioned, has accounts or has OEM-specific restrictions. Provisioning requirements vary by Android version. Do not run this on a phone you rely on without understanding the reset and management implications.

## Next steps
Before real deployment, add a proper managed-device provisioning flow, audit logging, app update handling, reliable firewall restart/boot handling, and testing on the exact Android devices you manage.
