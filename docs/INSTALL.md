# Installation

## Before either method

See the [compatibility matrix](COMPATIBILITY.md). ARMv7 works on the tested AFTSS; Fire OS 5 and Vega OS are unsupported by these builds. Fire OS 6+ is supported.

Use an **Android-based Fire TV**, not Vega OS. Put your phone/computer and TV on the same trusted network. Enable **Settings → My Fire TV → Developer options → ADB debugging**. If Developer options is hidden, open About and select the device name seven times. Find the TV's IP under About → Network. Accept only the authorization prompt from your own phone/computer.

Downloads: [Releases](https://github.com/arnoldmitre/tv-home-studio/releases). The release includes SHA256SUMS.txt. The phone APK and TV APK are different applications.

## With an Android phone

1. Install **TV-Home-Studio-Phone-1.8.apk** on an Android 9+ phone. Allow installation from the browser/file manager when Android requests it.
2. Open Studio, enter the TV's IP and port (normally 5555), connect, and accept the TV authorization prompt.
3. Select **Install TV Home**. Studio installs its bundled TV Home 4.8 and grants the Home-switching permissions over ADB.
4. Choose **This home**. **Default home** returns to Amazon Home.
5. In Themes, apply Classic, Cinema or Panel. Customize uploads wallpapers and changes options; editing an inactive theme does not switch the TV until you choose Apply.
6. On the TV itself, open **Customize → Themes** to switch with the remote. Saved wallpapers and options follow the theme.
7. Cleanup is separate and optional: Review cleanup → select extras → Disable selected extras. Export your restore point from Studio before clearing its data or changing phones.

Update both by installing the new phone APK over the old phone app, then using Update/reinstall launcher. Do not uninstall to work around a signature mismatch: see Recovery below.

## With a computer

Install [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) and Python 3.9+ (or use the direct commands below). Download **TV-Home-Launcher-4.8.apk**, then clone or download this repository. Commands assume `adb` is on PATH. On Windows you can use `py` in place of `python`.

Replace `192.168.1.60` with your TV's IP. The examples use a placeholder, not an automatically discovered address.

```text
adb connect 192.168.1.60:5555
adb -s 192.168.1.60:5555 get-state
python tools/tvhome.py --serial 192.168.1.60:5555 install --apk TV-Home-Launcher-4.8.apk --home this
python tools/tvhome.py --serial 192.168.1.60:5555 status
```

If adb is not on PATH, pass `--adb` with its full path before the subcommand. The tool refuses devices without Amazon Home. It never chooses a device implicitly when multiple targets are connected.

### Direct ADB installation, without Python

```text
adb connect 192.168.1.60:5555
adb -s 192.168.1.60:5555 install -r TV-Home-Launcher-4.8.apk
adb -s 192.168.1.60:5555 shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.1.60:5555 shell pm grant com.gathin.tvhome android.permission.READ_LOGS
adb -s 192.168.1.60:5555 shell content call --uri content://com.gathin.tvhome.studio --method home --arg this
```

The provider replies with a base64-encoded JSON payload on success, or an error. Selecting This home starts the launcher; after an update, `repairHome` can reconnect its service if needed. Keep the stock launcher installed/enabled.

### Theme switching

Use **Customize → Themes** on the TV, or:

```text
python tools/tvhome.py --serial 192.168.1.60:5555 theme classic
python tools/tvhome.py --serial 192.168.1.60:5555 theme cinema
python tools/tvhome.py --serial 192.168.1.60:5555 theme panel
```

Wallpaper uploads and per-theme customization are available in the phone app. A computer-only installation works with the default drawn backgrounds; no wallpaper is bundled.

### Optional computer cleanup

List what is available, then specify only the packages you want disabled. There is deliberately no automatic disable-everything command.

```text
python tools/tvhome.py --serial 192.168.1.60:5555 list-cleanup
python tools/tvhome.py --serial 192.168.1.60:5555 cleanup --packages com.amazon.sneakpeek com.amazon.shoptv.client --backup tvhome-restore.json
python tools/tvhome.py --serial 192.168.1.60:5555 restore-cleanup --backup tvhome-restore.json
```

These are examples, not packages you must disable. The tool saves original states before attempting changes, checks the result, reports protected/missing packages and restores only allowlisted packages for the same device. Keep the JSON private and backed up. Computer restore files and Studio's phone restore files are separate formats; neither is a firmware or app-data backup.

Update restriction/resume controls are available in Studio. They attempt only known OTA package and background-policy changes and report refusal; privileged updates may still happen. This project does not modify your router, DNS, hosts file or firmware.

## Recovery

Return to the original Home:

```text
python tools/tvhome.py --serial 192.168.1.60:5555 home default
```

Or without Python:

```text
adb -s 192.168.1.60:5555 shell content call --uri content://com.gathin.tvhome.studio --method home --arg default
```

Reconnect this launcher's Home service (while This home is selected):

```text
python tools/tvhome.py --serial 192.168.1.60:5555 repair-home
```

Studio also has **Repair Home switching**. If the provider is unavailable and Home is stuck, `adb -s YOUR_TARGET shell am force-stop com.gathin.tvhome` stops this launcher so the original Home can handle the remote. Reopen TV Home from Apps later to retry. Do not disable Amazon Home or erase the entire enabled-accessibility-services setting.

The package ID `com.gathin.tvhome` is inherited from the source base. If another fork with that ID is installed and a different signature, Android will refuse an in-place update. Export/back up its configuration before deciding whether to uninstall it; our installer never silently uninstalls or clears it. Locally rebuilt APKs normally use a different signing key from the published APKs.

If authorization is rejected, verify the IP, ADB setting and same-network connectivity. Do not expose port 5555 to the internet. ADB can be turned off after setup, but Studio/computer control will then be unavailable until you enable it again.
