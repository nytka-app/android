# Build from source

Build the APK and add a UI language. Back to the [README](../../README.md).

JDK 21 and the Android SDK with platform 37.0; see [CONTRIBUTING.md](../../CONTRIBUTING.md).

```bash
./gradlew assembleOssDebug
adb install app/build/outputs/apk/oss/debug/app-oss-debug.apk
```

`oss` is the flavor Releases publishes; `play` is kept for a later store listing. `play` alone adds the
on-device recogniser (sherpa-onnx, a prebuilt arm64 native library from JitPack, about 27 MB in the APK), so
[Transcribe on this phone](../reference/device.md#transcribe-on-this-phone) needs
`./gradlew installPlayDebug`.

The UI is in English and in Ukrainian: `app/src/main/res/values/strings.xml` holds the English,
`values-uk/strings.xml` the Ukrainian. A new language is a new `values-<locale>` folder whose
`<string>` and `<plurals>` names match the English ones. Add the language to
`app/src/main/res/xml/locales_config.xml` as well, or Android 13's per-app language setting will not
list it.
