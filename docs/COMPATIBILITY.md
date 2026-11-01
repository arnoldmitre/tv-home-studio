# Compatibility and limitations

Checked 30 October 2026 against the built APKs, the actual test device and the linked upstream documentation. A matching CPU or API level alone does not establish working Home redirection.

| Device / OS | Status |
| --- | --- |
| Fire TV Stick 4K, AFTKA, Fire OS 7, Android 9 / API 28, armeabi-v7a | Physically tested. Full accessibility and HomeRedirectService overlay support. |
| Fire TV 3rd Gen (Pendant), AFTMM, Fire OS 6 / Android 7.1 / API 25, armeabi-v7a | Physically tested. Supported via native `HomeWatcherService` background logcat fallback. |
| Fire TV Stick HD, AFTSS, Fire OS 7.7.1.7, Android 9 / API 28, armeabi-v7a | Physically tested. See the bounded checks in [VALIDATION.md](VALIDATION.md). |
| Other Android-based Fire TV on API 25+, including Fire OS 6/7/8 and newer Android-based releases | Supported. Requires Amazon Home, authorized network ADB and READ_LOGS permissions. Uses HomeRedirectService or HomeWatcherService fallback depending on accessibility availability. |
| Fire OS 5 / API 22 | Unsupported by these builds: launcher minimum API 25. |
| Vega OS Fire TV | Unsupported. This project supplies Android APKs, not a Vega VPKG. Check the actual OS/generation, not just the product name. |
| Non-Fire Android TV / Google TV | Outside this fork's support. Phone and computer installers require Amazon Home; our Amazon package list is not for other brands. |
| Android phone | Studio requires Android 9 / API 28+ and a working system WebView. No iOS app. |
| Computer | Windows, macOS or Linux with Python 3.9+ and Android Platform Tools. Uses an explicitly selected ADB device. |

Amazon documents the Fire OS / Android API mappings in its [Fire OS overview](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html). Vega has a separate [VPKG package format](https://developer.amazon.com/docs/cloud-app-program/amazon-cap-migrate-vega-os).

## Does it work on armeabi-v7a?

Yes on our tested AFTSS: its reported ABI list is `armeabi-v7a,armeabi`, and it runs TV Home 4.5. Both the TV APK and phone APK contain Java/DEX code and no bundled `lib/<abi>/*.so` libraries, so these builds are not ARM64-only. They have no ABI split to choose. This establishes ARMv7 operation on this device, not compatibility with every ARMv7 phone or TV.

For installation failures, include the exact installer error, model, Fire OS build, API level and whether you installed the phone or TV APK. `INSTALL_FAILED_OLDER_SDK` means the OS is too old; `INSTALL_FAILED_UPDATE_INCOMPATIBLE` usually means the installed package has a different signing certificate. Preserve the existing installation and settings while diagnosing. A generic “app not installed” message does not establish an ABI problem.

## What the reference projects support

These are different projects with different support policies; their model counts are not our test coverage.

- [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv): broader Android TV / Google TV cleanup and launcher work. Its current Fire TV guidance is cleanup-only because Fire OS does not freely hand over Home.
- [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup): our Fire-specific code base. Keeps Amazon Home for Settings and redirects after Home is detected; its README explicitly acknowledges a momentary stock-Home flash.
- [udhaykumarbala/cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app): inspiration for the phone workflow, with many brand profiles. Its current Fire TV route is cleanup-only. This fork does not inherit its device profiles or claim its coverage.

Full attribution and license provenance remain in [CREDITS.md](CREDITS.md).

## Home returns and startup

All three designs use the same return service. Version 4.5 accepts Home requests from ordinary app UIDs and existing-task resume events, not just the system UID. Version 4.6 retains its view hierarchy but hides the window with INVISIBLE outside Home, allowing the render surface to be released. Weather, wallpaper and idle work are paused. The fallback activity UI is hidden while the service owns Home. Re-showing a released surface can still take time; no zero-flash guarantee is made.

Service repair now takes place in the foreground Home activity. Previously, disabling the service could make a background provider process killable before its delayed re-enable callback, leaving redirection off. If an older installation is stuck on stock Home, update, select **This home**, and run **Repair Home switching** in Studio. Boot recovery cannot work while that service is disabled.

Version 4.5 used a translucent retained window to fix playback power policy; 4.6 instead uses an opaque window only while visible and INVISIBLE outside Home. Alpha-zero opaque windows are never used as hidden covers. Normal system idle timeouts are untouched.

With Home idle enabled (default), only the focused Home surface requests keep-screen-on: after two idle minutes it shows the selected wallpaper, bold clock, day/date and selected-city weather, and after fifteen it requests system sleep. Any key dismisses the clock without opening an app; manual Sleep still works. If the service cannot request sleep, the Home keep-screen-on flag is released so normal OS idle policy can take over. The setting is available in TV Customize and phone Themes. Other apps retain their own playback and idle policy. Text shifts slightly once per minute. Bright or static wallpapers do not prevent OLED/AMOLED burn-in. Weather follows the saved city and theme visibility settings.

This is still a response to Fire OS navigation, not replacement firmware. We cannot promise zero stock-Home frames during cold boot before our process/service starts, memory pressure, disabled permissions, app/service crashes, firmware updates or unrecognized vendor navigation. Settings and an explicitly selected Default home remain reachable. Apps that start stock Home explicitly are handled where the expected activity signal is available; this is not a per-app allowlist, nor a guarantee about every future app.

Amazon Home stays enabled because it hosts Settings. No root, system APK removal, Back-button interception or firmware patches are used. Classic, Cinema and Panel retain their existing visual designs.

Other limits: classic ADB TCP connections only (no TLS pairing UI); optional Cast/Inputs/Restart depend on model and permissions; no promised IR programming or true stick power-off; OTA restriction is best effort and protected updaters may still run. Cleanup disables only selected optional packages, preserving data and restore state. This is community, vibe-coded software without an independent security audit.

### Startup in 4.7

The early Home watcher starts before app artwork is loaded. A new-boot connection attaches a lightweight Home cover before building the full surface; the cover is removed when Home draws, when another screen takes over, or after a bounded timeout. Same-boot reconnects retain the existing foreground-aware recovery. With the service connected, a delayed BOOT_COMPLETED delivery checks for Home instead of forcibly launching it over a playing app. This cannot hide frames shown before Fire OS binds the accessibility service. Amazon Home remains enabled for Settings and recovery.
