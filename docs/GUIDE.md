# TV Home Studio

TV Home Studio 1.8 is an Android phone companion for the included TV Home 4.8 launcher. This is a personal sideloaded build, inspired by the reversible workflow in https://github.com/udhaykumarbala/cleanyourtv-app. It does not use that project's app code or claim its device coverage.

## Install and connect

1. Install **TV-Home-Studio-Phone-1.8.apk on the Android phone** (Android 9 or newer). The separate TV-Home-Launcher-4.8.apk is for manual TV installation; the companion already includes it.
2. Put the phone and Fire TV on the same trusted network. On Fire TV, enable Settings → My Fire TV → Developer options → ADB debugging. If Developer options is hidden, select the device name in About seven times.
3. Enter the TV IP from About → Network, normally port 5555. Accept the phone's authorization prompt on the TV.
4. Tap Install TV Home. Then choose **This home**. **Default home** switches back to Amazon Home without uninstalling either launcher.
5. Review cleanup separately; only checked extras are disabled. Nothing is uninstalled or data-cleared.

Supported connection: classic authenticated ADB over TCP on Android-based Fire TV, Android 7.1/API 25 or newer. Manufacturer may be Amazon or an integrated Fire TV vendor. Vega OS and Android wireless-debugging TLS pairing ports are not supported. Network discovery is not included; enter the IP explicitly. Capabilities still depend on firmware.

## Designs and customization

- **Classic:** retains the familiar heading, four favorites and more-apps grid. Its top-right toolbar now uses quiet text/icons instead of a row of large cards. Focus an icon to see its label.
- **Cinema:** a wallpaper-first canvas, centered bold clock/date and floating app dock, with lavender accents, larger evenly spaced cards and a restrained focus lift/zoom. Clock/date/weather remain centered; the selected-app name and “Select to open” sit left-aligned above the dock.
- **Panel:** a blue sidebar with animated navigation cards, clock/weather anchored at the bottom, and a four-column grid with a restrained focus zoom.
- All three launcher screens share the **Home name**, default **HOME**. Change it in Studio → Themes; the name appears in the app previews and on every launcher design. Design names are shown only in Studio.
- Only one launcher package and one active design are used. Opening a preview never changes the TV. Customize an inactive design and Save without switching; tap **Apply** explicitly to switch. No theme creation or separate theme installation.
- Each design independently stores wallpapers, rotation, Weather, Clock, Cast, Power, Greeting, Connection/storage, Inputs policy and Focus animations. **Reset options to defaults** resets visibility/motion and stops rotation, keeping wallpapers; Save commits the reset. Customize and Settings remain available so hidden controls cannot trap you.
- The TV Customize menu has spaced, scrollable options. Switch designs in Customize → Themes on the TV or in the phone app. Wallpaper and element customization remains in the phone app. Home screen still offers only **Default home** and **This home**.
- Inputs supports Automatic, Show and Hide per design. Automatic checks Android's reported passthrough TV inputs; a stick normally reports none. Inputs appears before Power. Vendor-specific input systems may not expose inputs through this API.
- Favorites and hidden apps remain shared across designs. Hidden apps stay installed. TV Power retains Sleep and Restart; firmware permissions still apply. Phone remote, playback, sleep/restart and direct Settings controls have been removed.
- Existing 4.0 configuration migrates to the three fixed designs. The selected layout, its wallpapers and applicable options are preserved. If several old custom themes used one layout, its active theme wins; other duplicate profiles are not exposed in the new catalog. The original schema-1 configuration is retained privately for recovery.

### Wallpaper guidance

**Recommended: 1920 × 1080 pixels, 16:9 landscape.** Choose JPEG, PNG or WebP. 4K images are accepted and resized to fit within 1920 × 1080 on import. Portrait and other aspect ratios are center-cropped to fill the screen, so keep text and important subjects away from edges. The picker explains this and shows imported dimensions.

Each design accepts up to eight wallpapers, independently. Rotation runs only while Home is visible; Off uses the first image. No image is bundled; each design has a lightweight drawn background. Only the current wallpaper bitmap is cached. Old image files remain available for restore compatibility within a 64 MB TV storage budget.

### Weather

Choose **Off** or **Choose city**. City search and current forecasts use Open-Meteo; the launcher never determines your location automatically and needs no GPS permission. Older Automatic settings migrate to Off; existing manual city selections are kept.

Forecasts are cached for one hour. Foreground failures retry after two minutes and may show a clearly labelled last known forecast up to a day old. The minute tick refreshes weather without rebuilding Home. **Refresh weather** retries while Home is visible. Refresh device status for a diagnostic error if requests continue to fail. Per-design Weather can hide the display without changing the selected city. No weather service runs while Home is detached.

## Wake and Home switching

This release clears stale sleep/transition state and uses a bounded six-second recovery period after wake, instead of one redirect attempt. It handles screen-on, unlock and screensaver exit; checks interactive state if a screen broadcast was missed; and reconnects after package replacement. It stops recovery for explicit app/Settings navigation. It does not poll continuously while idle.

If This home was selected before a launcher update, Studio restores its Home switching connection and waits for a fresh service connection. Overview shows the connection state and provides **Repair Home switching**. Repair refreshes this launcher's accessibility service while preserving other enabled services. Default home stays respected. The original Amazon launcher remains available for Settings. Real Fire OS behavior still requires device-specific verification.

## Cleanup and updates

Cleanup only includes the 13 optional packages named in the app: metrics/logging, advertising identifiers, promotional content, Easy Upgrade and shopping extras. Core Home, Settings, accounts, Wi-Fi, remote control, playback, Appstore, Alexa and casting packages are not in that list. The stock launcher stays installed and enabled because Fire OS Settings depends on it.

**Restrict updates** attempts to disable the known forced/OTA updater packages and sets RUN_ANY_IN_BACKGROUND to ignore for protected OTA packages. Every change is read back and refusal is shown. **Resume updates** enables those known packages and allows their background operation where Fire OS permits it. Neither action guarantees a complete update block or an immediate update. No root, firmware change, router modification, DNS service, VPN or hosts-file edit is used.

The app controls the implemented settings exposed through authorized ADB. It cannot override Fire OS restrictions or provide unrestricted firmware control.

## Restore and privacy

A restore point is saved locally, keyed to the TV's identifier, before changes. Each package retains its original enabled-state value (including default and already-disabled states), and update background policies retain their original value. Restore only changes the recorded packages and policies, plus the saved launcher/Home settings and permissions. APK installations are retained; apps and data are never removed by restore.

Save a JSON restore-point copy from Controls. Import is available on a phone without an existing restore point for that TV; mismatched device IDs and protected package entries are rejected. An existing local baseline is not overwritten. Restore points are settings/state backups, not firmware or app-data backups. Do not clear Studio's data before exporting one. ADB authorization keys are private app files and are not exported or included in the source archive.

No accounts, ads or analytics. TV operations use the local network. Open-Meteo receives requests only when city weather is enabled or a city search is requested. Picked photos are sent only to the connected TV. The app does not switch ADB off automatically, because that would prevent reconnecting from the phone; it can be switched off in TV Developer options when not needed.

## Build and source

See [BUILD.md](BUILD.md) for the source tree, prerequisites and build commands.

## Home idle screen

Enabled by default. On Home only, your selected wallpaper, a bold clock, day/date and selected-city weather appear after two idle minutes, followed by sleep at fifteen minutes. Text shifts slightly once per minute. Weather remains hidden when Off or disabled for that theme. The first remote key dismisses it without launching anything. Manual Sleep remains available. Set it Off in TV Customize → Home idle screen or phone Themes → Home idle screen to return to Fire OS idle settings. Global sleep/screensaver settings are never changed. Other apps control their own playback and idle behavior. A bright or static wallpaper is not burn-in protection.

### Separate screensaver wallpaper

In the phone app, open **Themes → Home idle screen → Change screensaver wallpaper**. Choose one image and tap **Save screensaver wallpaper**. It applies to the screensaver across all designs; each Home design keeps its own wallpapers. Closing the picker without saving leaves the current setting alone.

To switch back, choose **Use theme wallpaper**, then save. The screensaver follows the active design and its wallpaper rotation again. Recommended: **1920 × 1080, 16:9 landscape**, JPEG/PNG/WebP. Imports are resized and center-cropped. No image is bundled.
