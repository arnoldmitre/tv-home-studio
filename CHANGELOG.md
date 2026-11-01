# Changelog

## 1.9.0 — 1 November 2026

- **Fire OS 6 / Android API 25 Support:** Lowered `minSdk` to 25 and eliminated `java.nio.file` in `StudioProvider` for full compatibility with Fire OS 6 devices (such as Fire TV 3rd Gen Pendant `AFTMM`).
- **Automatic TV IP Weather Detection:** Added `"auto"` weather mode to `StudioConfig` and HTTPS IP geolocation in `WeatherInfo` (via `ipwho.is` and `ipapi.co`), automatically resolving city coordinates with 24h caching. Added on-screen "Weather" toggle to the Customize menu.
- **Open-Source Wallpaper Providers:** Added on-screen "Wallpaper source" menu to select between **NASA Deep Space** photography (using NASA's official Image Library archive API) and **Bing Daily Landscapes**, with automatic multi-image rotation.
- **HomeWatcherService Fallback:** Added native background service fallback running `HomePressWatcher` via `READ_LOGS` with a 350ms transition anchor. Solves Home button redirection on Fire OS 6 devices where Amazon hides or restricts third-party accessibility service menus.
- **GitHub Actions Automated CI/CD:** Added automated workflow compiling and uploading launcher APKs on every push to main.

## 1.8.0 — 31 October 2026

- Add a separate screensaver wallpaper setting: use the active design’s image/rotation or upload one custom image shared by all designs. Theme wallpapers are unchanged.
- Add a single-image picker, preview, explicit Save and Use theme wallpaper reset in phone Themes → Home idle screen. Cancel and upload failures keep the previous setting.
- Preserve existing installations with theme-following as the default. Validate custom image IDs and reject saves before the image exists on the TV.
- Studio 1.8 embeds TV Home 4.8. Includes the swipe animation, weather-free screenshots and simpler download documentation from 1.7.1.

## 1.7.1 — 31 October 2026

- Add a 340 ms slide-up screensaver entrance and a 260 ms upward swipe dismissal. Respect each theme’s animation toggle. No continuous animation, idle timing, playback or Home-routing changes.
- Consume remote keys during dismissal; cancel/remove immediately on focus loss, leaving Home, rebuild or sleep.
- Replace the public theme, theme-picker and comparison screenshots; add the screensaver. All current screenshots hide weather and location. Expand the README feature catalog and simplify release pages.
- Studio 1.7.1 embeds TV Home 4.7.1.

## 1.7.0 — 31 October 2026

- Replace the black idle clock with the selected wallpaper, bold clock, weekday, date and selected-city weather. Reuse the wallpaper bitmap and weather cache, with minute-only updates and small text-position shifts.
- Preserve Home-only activation, first-key dismissal, two-minute idle / fifteen-minute sleep timing, manual Sleep and other apps’ playback policy. Weather Off and per-theme weather visibility are respected.
- Start the early Home watcher before artwork loading; attach a lightweight startup cover on a new boot before building the full launcher. Same-boot service reconnects keep foreground-aware recovery.
- When the service is connected, late BOOT_COMPLETED delivery checks the foreground instead of unconditionally launching Home over an app.
- Studio 1.7 embeds TV Home 4.7. Layout designs are unchanged. Stock Home can still appear before Fire OS connects the service; this is not a zero-flash guarantee.

## 1.6.0 — 30 October 2026

- Release the hidden Home window’s drawing surface with INVISIBLE instead of retaining an alpha-zero translucent buffer. Keep its view hierarchy for reuse and pause inactive work.
- Hide the duplicate activity UI while the service owns Home. Opaque rendering is used only for visible Home, avoiding unnecessary drawing underneath it.
- Optional Home-only idle screen: dim moving clock on black after 2 minutes, system sleep after 15 idle minutes. First key dismisses without opening an app. Toggle from TV Customize or phone Themes. No global sleep timeout changes; apps retain their own playback behavior.
- Preserve manual Sleep, service repair and early Home-return routing.
- Studio 1.6 embeds TV Home 4.6. No guarantee against burn-in or stock Home exposure during cold boot/service failure.


## 1.5.0 — 30 October 2026

- Handle app-initiated Home intents regardless of the caller app, including the YouTube Back-to-Home request observed on Fire TV.
- Watch Home activity-resume events for returns to an existing Home task without a new start intent.
- Preserve launch and Settings bypasses for indirect Home signals. No Back-button remapping, app allowlist, root or disabling of Amazon Home.
- Retain the rendered Home window between app visits, transparent and without input while hidden. Reveal that buffer before refreshing a changed app catalog or artwork.
- Prepare Home while the screen is off and remove the deliberate wake delay. Ignore window events queued before a newer Home request; retry a deferred Home activity request for a bounded interval.
- Run service repair from a resumed Home activity: Fire OS could kill a provider-only process between disabling and re-enabling accessibility, leaving redirection disabled after installation/repair.
- Apply the shared routing changes to Classic, Cinema and Panel without changing their designs.
- Use a translucent window format: an alpha-zero opaque overlay could still obscure a player for Fire OS power policy, suppressing its keep-screen-on request. No global sleep timers are disabled.
- Cinema's idle heading now sits lower above the dock with a time-of-day greeting above it. Focused app names retain their existing caption placement. Custom screensaver work is deferred.
- Document ARMv7 operation on AFTSS, APK architecture, OS minimums and differences from upstream device support. Add a minimum-API check to the computer installer.
- Studio 1.5 embeds TV Home 4.5. See [validation](docs/VALIDATION.md) for checks and remaining limits; this is not a zero-flash guarantee.

## 1.4.0 — 30 October 2026

Studio 1.4 bundles TV Home 4.4. First public preview.

- Restored TV Customize → Themes with Classic, Cinema and Panel, preserving saved profile options and creating an unused preset only when selected.
- Includes the preceding Cinema bold-clock, left-caption and focus-clearance fixes.
- Added prominent upstream attribution, full licenses, device screenshots, a computer installer with reversible cleanup, and phone/computer recovery guides.
- Persistent Home rendering and bounded wake recovery retained; no guarantee that Fire OS can never display stock Home.
