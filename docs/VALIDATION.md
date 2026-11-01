# Validation — Studio 1.9 / TV Home 4.9

1 November 2026. Physically validated across two hardware devices: Fire TV Stick 4K (AFTKA / Fire OS 7 / API 28) and Fire TV 3rd Gen Pendant (AFTMM / Fire OS 6.2.8.7 / API 25).

- Both APKs (TV Launcher and Phone Companion) compile in GitHub Actions with zero errors; the Companion bundles the matching TV launcher APK.
- **Fire OS 6 compatibility:** `minSdk` lowered to 25 and `java.nio.file` replaced in `StudioProvider`. Sideloaded and executed on Fire OS 6 (API 25) without SDK incompatibility.
- **Automatic IP Weather:** Tested on physical devices; successfully queries IP geolocation and Open-Meteo, resolving location and current weather conditions with 24h caching.
- **Wallpaper Sources:** Verified NASA Image Library API integration and Bing Daily Landscapes. High-definition space photography and daily landscapes download, decode, cache, and rotate properly.
- **HomeWatcherService Fallback:** On Fire OS 6 where Amazon strips third-party accessibility menus, verified background service running `HomePressWatcher` with `READ_LOGS`. Catches `START u0 ... cat=[android.intent.category.HOME]` and seamlessly anchors TV Home Studio within 30–350ms.
- **Phone Companion:** Verified compilation of `TV-Home-Studio-Phone-Companion.apk` with `minSdk 28` / target SDK 34 and updated API 25 device connection support in `TvController`.

---

# Validation — Studio 1.8 / TV Home 4.8

31 October 2026. Separate screensaver wallpaper tested on the same AFTSS / Fire OS 7.7.1.7 stick.

- Both APKs build and Android Lint reports zero errors. Signing certificates match earlier releases; Studio embeds the exact standalone TV APK.
- Twenty-five configuration tests pass, including migration/default theme-following, independent custom image persistence, effective-display invalidation, reset, and invalid image IDs.
- Phone interface checks pass for single-image selection, cancel without saving, failed upload retaining the old setting, retry/save, unchanged theme wallpapers, persistence across design switches and Use theme wallpaper reset. The bridge is mocked for these phone UI cases; no native phone picker end-to-end test is claimed.
- On the TV, a nonexistent image was rejected before configuration changed. A different existing uploaded image was then assigned to the screensaver; the actual two-minute idle screen showed it while Cinema's Home wallpaper remained unchanged after dismissal. Reset to theme-following saved successfully. Original TV settings were restored afterward.
- The three Home screenshots and theme picker remain the weather-free 1.7.1 captures. The screensaver and comparison images are updated with the separate background; neither contains weather or location.
- No startup, wake, sleep timing or app-routing code changed in this release; previous checks are recorded below.

---

# Validation — Studio 1.7.1 / TV Home 4.7.1

31 October 2026. The animation change was checked on the same AFTSS / Fire OS 7.7.1.7 stick.

- Both APKs build; Android Lint reports zero errors and phone JavaScript syntax checks pass. The embedded TV APK matches the standalone APK, and signing certificates match prior releases.
- Recorded the real two-minute idle transition and remote dismissal. Sampled frames show the screensaver sliding into place and swiping upward to reveal Home.
- Repeated OK presses during dismissal did not open the focused app. Home remained connected and visible; theme, wallpaper, weather, favorites and hidden-app settings were preserved.
- Startup, wake and app-routing code is unchanged from 1.7.0; those checks are documented below rather than claimed as new tests of this patch.
- New GitHub screenshots were captured with weather Off and per-theme weather visibility disabled, then original device settings restored.

---

# Validation — Studio 1.7 / TV Home 4.7

31 October 2026. Physical AFTSS / Fire OS 7.7.1.7 / ARMv7 checks; bounded observations, not a guarantee for other firmware.

- Both APKs build; Android Lint reports zero errors. The phone frontend check passes with no JS errors or horizontal overflow.
- Thirty Home-return parser/routing cases, twelve wake-policy cases, six boot-session cases, the idle timing boundaries/reset and six computer-helper tests pass.
- The real two-minute Home idle deadline displayed the selected wallpaper, bold clock, day, date and selected-city weather. The first OK dismissed it without opening an app. Weather was temporarily enabled for that visual check, then the saved Off setting was restored. No user wallpaper is bundled or uploaded with this release.
- A restart returned to our Home without remote input. The service connected at about 25 seconds uptime. ADB became available at about 30 seconds; sampled frames from that point showed our lightweight startup cover followed by the complete Cinema Home. Earlier startup frames were not captured, so this is not proof that Amazon Home never appeared.
- Classic, Cinema and Panel each passed opening YouTube, returning via Back to the resumed custom Home, opening Settings and sleep/wake. Saved theme settings were restored afterward. In all three, the Home window was INVISIBLE with no render surface and no keep-screen-on flag while YouTube was open; no startup cover remained.
- The fifteen-minute sleep deadline is covered by the unchanged policy test; an uninterrupted fifteen-minute physical wait was not repeated. No movie-length playback endurance or controlled FPS benchmark is claimed.
- Published APK signatures match the preceding release. Neither APK has native ABI-specific libraries, and the TV APK embedded in Studio matches the standalone TV APK exactly.
- Global Fire OS sleep/screensaver timers remain unchanged. The new idle presentation reuses the wallpaper bitmap and weather cache; it has no continuous animation. Bright/static wallpapers do not guarantee burn-in prevention.

---

# Validation — Studio 1.6 / TV Home 4.6

30 October 2026. Tested on the same AFTSS / Fire OS 7.7.1.7 / ARMv7 stick. Bounded checks, not universal performance or zero-flash guarantees.

- Both APKs compile and Android Lint passes with zero errors. Phone frontend checks pass; no JavaScript errors or horizontal overflow.
- Thirty Home-return checks, six computer-helper checks, and idle-policy boundary/reset checks pass.
- Physical idle test: after two minutes on Home, the black screen with dim clock appeared. The first key dismissed it without opening an app. The fifteen-minute deadline is covered by the policy test; an uninterrupted fifteen-minute physical wait was not performed.
- Classic, Cinema and Panel each passed opening YouTube, returning through Back to our resumed Home activity, opening Settings, and sleep/wake recovery. Sampled return-video frames showed direct YouTube-to-custom-Home transitions; sampling cannot exclude an unsampled frame. Theme settings were restored after testing.
- While YouTube was open, the Home overlay reported `mViewVisibility=0x4` (INVISIBLE), `mHasSurface=false`, no input focus and no keep-screen-on flag. This confirms actual surface release rather than alpha-only hiding. The underlying Home activity UI is hidden while the overlay owns Home.
- No global sleep/screensaver timeout was changed. Apps still control their own keep-awake behavior. Manual sleep/wake and Home return remain subject to Fire OS permissions and startup timing.
- APKs contain no native ABI-specific libraries. The TV APK embedded in Studio matches the standalone TV APK.

Performance depends on app, memory pressure and firmware. Removing the retained buffer addresses identified graphics overhead; no controlled frame-rate benchmark or long movie-length endurance test is claimed. The idle clock reduces static bright imagery, not guaranteed burn-in prevention.

---

# Validation — Studio 1.5 / TV Home 4.5

30 October 2026. Checks are bounded; they do not establish perfect behavior on every app or firmware.

- Both APKs build; Android Lint reports zero errors.
- Thirty standalone Java Home-return parser/policy checks pass, including ordinary app UIDs, existing-task resume signals, exact Home component matching and Settings/launch bypasses.
- Six computer-helper tests pass, including API-level rejection, explicit device selection, safe quoting, original-state preservation and refusal of core-package/mismatched restore entries.
- Phone frontend checks pass for explicit Apply, independent theme settings, reset, wallpaper guidance, weather choice, cleanup, hidden apps and absence of a remote-control tab; no JS errors or horizontal overflow.
- APK archive inspection confirms no native `lib/` entries in either APK. The physical AFTSS reports `armeabi-v7a,armeabi`, Android API 28, Fire OS build PS7717.5741N. This establishes ARMv7 operation on the test device.
- Both APK signatures match the prior release certificate. The launcher's bytes embedded in Studio match the standalone APK.
- A restart test recovered to TV Home automatically. By the first successful ADB observation, at approximately 30 seconds uptime, boot was complete and our MainActivity was resumed. This is **not** a video verification that stock Home was never visible earlier in boot.
- Service-repair testing exposed the old background-process re-enable race; the final build performs repair from a resumed Home activity.
- On the physical TV, Classic, Cinema and Panel each passed app opening, repeated YouTube Back through its menus to our resumed Home activity, Settings opening, and sleep/wake with the service connected. This complete three-theme pass preceded the final translucent-window and Cinema-caption changes.
- The final window-format change addresses the power-policy problem where an alpha-zero opaque window still obscured the player. It does not change the device's sleep or screensaver timeouts. An extended unattended movie-length test has not been completed.
- Final-build playback check: YouTube's window had `KEEP_SCREEN_ON`, was reported as `mHoldScreenWindow`, and held the WindowManager screen-bright wake lock. `mObscuringWindow` was null rather than our hidden Home surface. Home navigation returned afterward. This verifies the power-policy correction, not every streaming app's own wake-lock behavior.

Return-window logs varied under load. An early retained-window capture showed a 39 ms callback versus 885 ms with removal/recreation, but these are individual log timings, not end-to-end display latency or a performance guarantee. Later checks identified catalog refresh and deferred-activity cases and led to further fixes. Sleep interrupted screen recording on this firmware, so wake checks use resulting activity/service state rather than claiming frame-by-frame wake coverage.

See [COMPATIBILITY.md](COMPATIBILITY.md) for unsupported devices and remaining limitations. Private device logs, configuration backups and streaming-app screenshots are not published.

---

# Earlier validation — Studio 1.4 / TV Home 4.4

30 October 2026. Experimental first public release; tests are bounded, not a guarantee across devices.

- Both Android modules compile; Android Lint reports zero errors.
- The phone frontend checks pass: explicit Apply, edits to inactive themes, reset, wallpaper guidance, city weather, cleanup selection, hidden apps, no remote-control tab; no JavaScript errors or horizontal overflow.
- TV Home 4.4 was installed in place on a physical AFTSS Fire TV Stick HD. The new **Customize → Themes** picker was operated through remote key events to select Classic, Cinema and Panel. All three selections were read back successfully. Neutral/focused screenshots were captured directly from the device.
- The prior active theme, all wallpaper references/options, weather choice, Home name, favorites, hidden apps and Home-selection state were restored and compared after capture. Home service was connected with its persistent window visible.
- The computer helper has local tests for explicit-device targeting/quoting, state restoration, refusal of protected or mismatched backup entries and preservation of original states after repeated cleanup attempts. These tests use fakes, not destructive operations on the TV.
- APK signatures, embedded launcher identity, bundled attribution and absence of wallpaper binaries are checked before upload.

The previous 4.3 emulator check covers complete Cinema toolbar focus bounds, all three native layouts, app focus, visible Power, Customize and empty catalogs. Earlier local tests covered ADB framing/authentication/file transfer, config migration, weather parsing and bounded wake policy. Those older suites were not all rerun for the theme-picker release.

Not established: zero dropped frames, zero Amazon Home flashes under every condition, operation on every Fire TV model, reliable integrated-TV input switching, complete OTA blocking, or a professional security audit. There is no root or firmware modification. Stock Home remains available for Settings and recovery.
