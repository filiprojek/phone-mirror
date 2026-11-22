# PhoneMirror

Turn one Android phone into a live camera and another into its remote display over local WiFi — no server.

- **Camera role:** pick a lens, toggle the torch, zoom; keeps streaming in the background (foreground service).
- **Display role:** finds cameras via mDNS, manual IP or QR code; pinch-zoom/pan, rotate/mirror, snapshot, stats overlay; can drive the camera's lens, torch and zoom remotely.
- **Transport:** hardware H.264 over a single TCP connection (port 47800), one viewer at a time, the camera phone approves every viewer.

## Layout

| Module | What |
|---|---|
| `core/` | Pure Kotlin/JVM: wire protocol, socket transport, session state machines, view/zoom math. Fast unit tests. |
| `app/`  | Android app: Camera2, MediaCodec, NSD, foreground service, Compose UI. |

## Build

Requires JDK 17+ and an Android SDK (`ANDROID_HOME`) with platform 37.

```sh
./gradlew :core:test :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug                   # APK in app/build/outputs/apk/debug/
./gradlew connectedDebugAndroidTest            # on-device tests (needs adb)
```
