# ClawNova

ClawNova is a customized Android client built on [OpenClaw](https://github.com/openclaw/openclaw). It connects to an existing OpenClaw Gateway; it does not provide a Gateway or a model service itself. This repository retains the full OpenClaw source tree because the Android build uses shared packages and localization tools.

The Android interface has a redesigned agent home and chat flow, a bot profile, and locally customizable bot icons. Creating an agent requires a compatible Gateway and `operator.admin` access. The Standard variant uses the existing restricted implementations for SMS, call logs, and accessibility control and removes the notification listener service.

## Requirements

- Android 12 (API 31) or newer and an accessible OpenClaw Gateway.
- JDK 21, Android SDK and build tools; the Android project compiles against API 37.
- Node.js and pnpm versions specified in [package.json](package.json). Gradle uses the repository's Node dependencies for localization and the shared Mermaid renderer.

## Build a debug APK

From the repository root, install the dependencies and build the Standard flavor:

```bash
pnpm install --frozen-lockfile
cd apps/android
./gradlew :app:assembleStandardDebug
```

On Windows, use `gradlew.bat :app:assembleStandardDebug` instead. Configure the SDK through `ANDROID_HOME` or the local, untracked `apps/android/local.properties` file. The APK is generated under `apps/android/app/build/outputs/apk/standard/debug/`. Android Studio can also open `apps/android` after the dependencies are installed.

This is a debug build, signed with the local Android debug key. ClawNova Standard 0.1 uses the release application ID `dev.ricchiutidev.clawnova`; the debug build adds `.debug`. It installs separately from older ClawNova debug builds and the official OpenClaw app. Local settings and credentials do not migrate automatically. The existing Wear companion is not compatible with this release because its application ID and signing identity differ.

To update an original Standard APK through v6 in place, build `:app:assembleStandardLegacyDebug` with the original debug keystore. This variant uses `ai.openclaw.app.debug` and writes its APK to `apps/android/app/build/outputs/apk/standardLegacy/debug/`.

Release builds require a private signing key. Back up the key and its password securely: future updates must use the same signing identity. See the [Android guide](apps/android/README.md) for the underlying project and other build variants.

## Verification and limitations

The Standard debug build and focused tests were run during development. A Windows Robolectric camera/FileProvider test also failed on the unmodified baseline. The latest customization has not been verified against a real Gateway or on a physical device. Hardware features and Gateway permissions depend on the device and server configuration.

## Attribution

Based on OpenClaw, starting from upstream commit `936f38793bab7199ea2cbe11fb5ef559062254a1`. The OpenClaw [MIT license](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md) are retained. ClawNova is an independent customization; OpenClaw's name and documentation refer to the upstream project.
