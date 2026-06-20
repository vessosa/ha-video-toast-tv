# HA Video Toast TV

Android TV overlay notifications with live camera feeds from [Home Assistant](https://www.home-assistant.io/), triggered by HA automations. This is the Android TV companion to [HA Video Toast](https://github.com/vessosa/ha-video-toast).

Looking for the PC/computer version? Use the original Python desktop app: [github.com/vessosa/ha-video-toast](https://github.com/vessosa/ha-video-toast). It is designed for Windows desktop notifications and includes the full PC tray/settings experience.

![License](https://img.shields.io/badge/license-MIT-blue) ![Android](https://img.shields.io/badge/android-TV-blue) [![Donate](https://img.shields.io/badge/donate-PayPal-00457C?logo=paypal)](https://paypal.me/vessosa)

---

## Table of Contents

- [Features](#features)
- [How It Works](#how-it-works)
- [Requirements](#requirements)
- [Building](#building)
- [Sideloading](#sideloading)
- [First Run Setup](#first-run-setup)
- [Home Assistant Setup](#home-assistant-setup)
- [Configuration](#configuration)
- [ADB Helpers](#adb-helpers)
- [Google Play Notes](#google-play-notes)
- [Related Projects](#related-projects)
- [Contributing](#contributing)
- [License](#license)
- [Author](#author)
- [Support](#support)

---

## Features

- **Live MJPEG video** in Android TV overlay toasts
- **Home Assistant WebSocket listener** using a long-lived access token
- **Same event format** as the desktop HA Video Toast app: `ha_video_toast`
- **Stacked toasts** over other Android TV apps when overlay permission is granted
- **Responsive toast sizing** based on the current TV resolution
- **Optional fullscreen overlay** for important alerts
- **Starts after boot** once configured
- **TV setup screen** for Home Assistant URL and token
- **QR/local web setup page** for easier phone/browser configuration
- **Home Assistant discovery** by scanning the local subnet for port `8123`
- **ADB/deep-link setup** for advanced users

---

## How It Works

```text
Home Assistant automation
        |
        v  event.fire  ha_video_toast  {camera: camera.doorbell}
HA WebSocket API  (ws://ha-url:8123/api/websocket)
        |
        v
HAListenerService  --->  OverlayToastManager  --->  MjpegView
  foreground service      stacked overlay           HA camera proxy stream
  boot receiver           fullscreen mode           /api/camera_proxy_stream/<entity>
```

The app connects to Home Assistant through the WebSocket API and subscribes to the custom event type `ha_video_toast`. When an automation fires that event, the app opens an Android overlay window and streams the camera feed through Home Assistant's built-in MJPEG camera proxy. No RTSP URL is required.

---

## Requirements

- Android TV / Google TV device running Android 8.0 or newer
- Home Assistant with at least one camera entity
- A Home Assistant long-lived access token
- "Display over other apps" permission enabled for this app
- Android SDK + JDK 17 if building from source
- ADB if sideloading from your computer

Tested during development on NVIDIA Shield TV and Sony BRAVIA Android TV devices.

---

## Building

Clone the repository:

```bash
git clone https://github.com/vessosa/ha-video-toast-tv
cd ha-video-toast-tv
```

Use JDK 17. If your default `java` is not JDK 17, set `JAVA_HOME` before building:

```bash
export JAVA_HOME=/path/to/jdk-17
```

Create `local.properties` with your Android SDK path:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Build Notes

- The project is a native Kotlin Android app.
- `local.properties` is local machine configuration and should not be committed.
- Do not commit a personal `org.gradle.java.home=/path/to/jdk` line. Use `JAVA_HOME` locally instead.
- The debug APK is suitable for private sideloading.
- For a public release APK/AAB, create your own signing key and configure a release signing setup outside the repository.

---

## Sideloading

### Option A - ADB over Network

Enable Developer Options and ADB debugging on the Android TV device. On many TVs this is under:

```text
Settings -> System -> About -> Android TV OS build
```

Press the build entry several times to enable Developer Options, then enable USB debugging or network debugging.

Connect and install:

```bash
adb connect TV_IP_ADDRESS:5555
adb -s TV_IP_ADDRESS:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app:

```bash
adb -s TV_IP_ADDRESS:5555 shell monkey -p com.vessosa.havideotoasttv 1
```

### Option B - USB / File Manager

Copy `app-debug.apk` to the TV using USB storage, a file manager, or another sideloading tool. Open the APK on the TV and approve installation from unknown sources when Android asks.

---

## First Run Setup

Open **HA Video Toast TV** on the Android TV.

Enter:

- **Home Assistant URL**, for example `http://192.168.1.100:8123`
- **Long-lived access token**

Then select **Save + Start**.

### Required Permissions

Select **Overlay Permission** in the app, then enable **Display over other apps** for HA Video Toast TV.

This permission is required because Android does not allow normal apps to draw over Netflix, YouTube, Plex, Live TV, launchers, or other apps unless the user explicitly grants overlay access. This cannot be granted automatically by the app and should not require ADB.

On Android 13 or newer, also allow notifications. Android requires a visible foreground-service notification for long-running background work.

### QR / Local Web Setup

The app shows a QR code with a local setup URL. Scan it from a phone or open the URL from another browser on the same network, then submit the Home Assistant URL and token. The app saves the configuration and starts the listener.

### Finding Home Assistant

Select **Find HA** to scan the current local subnet for a Home Assistant instance listening on port `8123`. If found, the first result is filled into the URL field.

---

## Home Assistant Setup

### 1. Generate a Long-Lived Access Token

In Home Assistant:

```text
Profile -> Security -> Long-lived access tokens -> Create token
```

Paste the token into the TV app or the QR setup page.

### 2. Create an Automation

Add this action to any automation:

```yaml
action: event.fire
event_type: ha_video_toast
event_data:
  camera: camera.your_camera
  duration: 15
```

Example - doorbell person detected:

```yaml
alias: Doorbell person detected
trigger:
  - platform: state
    entity_id: binary_sensor.doorbell_person
    to: "on"
action:
  - action: event.fire
    event_type: ha_video_toast
    event_data:
      camera: camera.doorbell
      duration: 20
```

Optional fullscreen overlay:

```yaml
action: event.fire
event_type: ha_video_toast
event_data:
  camera: camera.doorbell
  duration: 15
  fullscreen: true
```

---

## Configuration

The TV UI currently exposes:

| Option | Purpose |
|--------|---------|
| Home Assistant URL | Base URL for Home Assistant, such as `http://192.168.1.100:8123` |
| Long-lived access token | Token used for WebSocket auth and camera proxy requests |
| Save + Start | Saves configuration and starts/reloads the listener service |
| Find HA | Scans the local subnet for Home Assistant on port `8123` |
| Overlay Permission | Opens Android's "Display over other apps" permission screen |
| Test Toast | Shows a sample overlay toast |
| Fullscreen Test | Shows a sample fullscreen overlay |

Internal defaults:

| Setting | Default |
|---------|---------|
| Duration | 15 seconds |
| Max toasts | 4 |
| Corner | Bottom-right |
| Size | Automatically calculated from TV resolution |

Settings are stored in the app's private Android preferences on the TV. They are not stored in this repository.

---

## ADB Helpers

Configure by deep link:

```bash
adb -s TV_IP_ADDRESS:5555 shell am start \
  -a com.vessosa.havideotoasttv.CONFIGURE \
  -d 'havideotoasttv://config?ha_url=http%3A%2F%2F192.168.1.100%3A8123&token=YOUR_TOKEN'
```

Check whether the app is installed:

```bash
adb -s TV_IP_ADDRESS:5555 shell pm list packages com.vessosa.havideotoasttv
```

Start the app:

```bash
adb -s TV_IP_ADDRESS:5555 shell monkey -p com.vessosa.havideotoasttv 1
```

Read recent logs:

```bash
adb -s TV_IP_ADDRESS:5555 logcat -d -t 300 | grep -i havideotoast
```

Uninstall:

```bash
adb -s TV_IP_ADDRESS:5555 uninstall com.vessosa.havideotoasttv
```

---

## Google Play Notes

This project is mainly intended for private sideloading.

The core feature depends on Android's `SYSTEM_ALERT_WINDOW` permission, shown to users as **Display over other apps**. Android requires users to grant this manually through system settings. Google Play also treats special permissions and long-running foreground services as policy-sensitive.

A Play Store release may be possible, but it would need careful review preparation:

- clear listing text explaining that the app displays Home Assistant camera alerts over other TV apps
- a privacy policy explaining that HA URL/token are stored locally on the TV
- Play Console declarations for foreground service usage
- a demo video showing why overlay permission is required
- a proper Android TV banner asset

For sideloading, none of this prevents normal use as long as the user grants overlay permission.

---

## Related Projects

- [HA Video Toast](https://github.com/vessosa/ha-video-toast) - Python desktop app for PC/computer use, with Windows tray integration, settings UI, multi-monitor support, and live Home Assistant camera toasts.
- [HA Video Toast TV](https://github.com/vessosa/ha-video-toast-tv) - This Android TV version, built for sideloading on Android TV / Google TV devices.

---

## Contributing

Contributions are welcome. Feel free to open issues or submit pull requests.

- Desktop project: [github.com/vessosa/ha-video-toast](https://github.com/vessosa/ha-video-toast)
- Android TV project: [github.com/vessosa/ha-video-toast-tv](https://github.com/vessosa/ha-video-toast-tv)

---

## License

MIT - see [LICENSE](LICENSE).

## Author

**Luiz Vessosa**  
GitHub: [github.com/vessosa](https://github.com/vessosa)

## Support

If this project saved you time, a coffee is always appreciated:  
[![Donate via PayPal](https://img.shields.io/badge/Donate-PayPal-00457C?logo=paypal&style=for-the-badge)](https://paypal.me/vessosa)
