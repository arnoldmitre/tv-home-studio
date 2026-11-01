# TV Home Studio

**Three launcher designs for your Fire TV. No root required.**

**Credits:** [cleanyourtv](https://github.com/Gathin23/cleanyourtv) → [fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup); phone workflow inspired by [cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app). [Licenses and attribution](docs/CREDITS.md).

**Completely vibe coded with Codex.** Community software, tested on a Fire TV Stick HD.

## Choose how to install

| I want to use… | Download | What it does |
| --- | --- | --- |
| **My Android phone** — easiest | **[Phone app](https://github.com/sreeego/tv-home-studio/releases/download/v1.8.0/TV-Home-Studio-Phone-1.8.apk)** | Installs the launcher on your Fire TV and lets you customize it from your phone. |
| **My computer** | **[TV launcher](https://github.com/sreeego/tv-home-studio/releases/download/v1.8.0/TV-Home-Launcher-4.8.apk)** | The launcher APK to install directly on Fire TV using ADB. |

**Pick one method. The phone app already includes the TV launcher.**

### Using a phone

1. Install the **Phone app** on your Android phone.
2. Enable **ADB debugging** on Fire TV. Connect both devices to the same local network.
3. In the phone app, connect to the TV's IP address and approve the TV's authorization prompt.
4. Tap **Install TV Home**, then choose **This home**.

[Full phone setup →](docs/INSTALL.md#with-an-android-phone)

### Using a computer

1. Download the **TV launcher** and install Android SDK Platform Tools (ADB) on your computer.
2. Enable **ADB debugging** on Fire TV and connect it to the same local network.
3. Follow the computer guide to install the APK and enable Home switching.

[Computer commands and setup →](docs/INSTALL.md#with-a-computer)

**Requirements:** Android 9+ phone; Android-based Fire TV API 26+. Fire OS 5/6 and Vega OS are unsupported. [Compatibility details](docs/COMPATIBILITY.md).

## See the designs

![Classic, Cinema, Panel and screensaver — weather and location hidden](docs/images/comparison.png)

Open full screenshots: **[Classic](docs/images/classic.png)** · **[Cinema](docs/images/cinema.png)** · **[Panel](docs/images/panel.png)** · **[Screensaver](docs/images/screensaver.png)** · [Theme picker](docs/images/theme-menu.png)

Real captures from a Fire TV Stick HD, with weather and location hidden. Wallpapers and third-party apps shown are not bundled.

## What you get

- **Three designs:** Classic, Cinema and Panel; switch on the TV or from your phone.
- **Your own look:** separate wallpapers and optional elements for each design, wallpaper rotation, a custom Home name and animation controls.
- **Organized apps:** favorites, ordering, hidden apps and an app library.
- **Everyday controls:** Cast, Settings, supported TV inputs, Sleep and Restart.
- **Weather and idle screen:** optional city weather, a wallpaper screensaver with its own optional image and swipe animation, and Home-only idle sleep.
- **Device care:** optional reversible cleanup, update restrictions, restore points and Home-switching repair.

<details>
<summary><strong>All launcher features</strong></summary>

| Feature | What you can do |
| --- | --- |
| **Classic** | Use favorites above an app grid, with mint focus accents and a compact toolbar. |
| **Cinema** | Use a floating horizontal app dock, a bold centered clock and an app caption above the dock. |
| **Panel** | Use sidebar navigation cards, an app grid and a clock at the bottom of the sidebar. |
| Theme switching | Choose a design from **TV → Customize → Themes** or explicitly apply it in the phone app. One APK includes all three designs; only one is active. |
| Independent theme settings | Each design remembers its wallpapers, rotation, optional elements and animation setting. Editing an inactive design does not switch the TV. Reset a design's options without deleting its wallpapers. |
| Your Home name | Replace “HOME” with your own name, shared across the three layouts and the phone app. |
| Wallpapers | Choose online sources (**Bing Daily Landscapes** or **NASA APOD**) directly from the TV menu, or upload your own JPEG, PNG or WebP images; use up to eight per design, automatic rotation or **Next wallpaper** on the TV. Recommended: **1920 × 1080, 16:9 landscape**. 4K imports are resized; other shapes are center-cropped. No wallpaper is bundled. |
| Optional elements | Show or hide the clock, greeting, weather, connection/free-space status, Cast and Power for each design. Turn focus and screensaver animations on or off. |
| App library and favorites | Open TV apps, pin up to four favorites, reorder them left/right and remove favorites. Hold OK or press Menu on an app for its options. |
| Hidden apps | Hide apps from Home and bring them back through Hidden apps or the phone. Hiding does not uninstall or disable them. Open an app's system settings from its menu. |
| Remote navigation | Navigate with the TV remote's D-pad and OK. Back clears the selection or returns to Home; the heading changes to the selected app name and returns to its greeting when no app is selected. |
| Weather | Choose a city or leave weather Off. Cached forecasts, a manual refresh option and per-theme visibility. No automatic IP-location lookup. |
| Wallpaper screensaver | Choose the active theme’s wallpaper or upload a separate screensaver image shared across all designs. On Home only, show that image, bold clock, weekday, date and optional city weather after two idle minutes. Brief slide-up entrance and upward swipe dismissal; subtle text-position changes once per minute. The first key dismisses without opening an app. |
| Idle and power | Sleep after fifteen idle minutes on Home, or use manual **Sleep** / **Restart** from Power. Turn the Home idle screen off from TV Customize or phone Themes. Other apps keep their own playback/idle behavior; global Fire OS timeouts are unchanged. |
| Cast, Settings and network | Open Fire TV Display Mirroring, system Settings or network settings directly from Home. Availability depends on the device. |
| TV inputs | Show an Inputs menu for supported integrated TVs. Choose automatic detection, always show or hide; sticks normally have no TV inputs to expose. |
| Home selection and recovery | Switch between **Default home** and **This home**, with shared Home-button/Back-return handling, startup and wake recovery across all layouts. The phone provides a Home-switching repair action. |
| Lightweight rendering | Native Java views, cached artwork/wallpaper/weather, no video wallpaper or continuous screensaver animation. Hidden Home releases its drawing surface and pauses idle work while another app is in front. |

</details>

<details>
<summary><strong>All phone app and computer-tool features</strong></summary>

- **Install or update the launcher** over authorized ADB on your local network. The phone APK contains the matching TV APK and sets up the required Home-switching permissions.
- **Customize without reinstalling:** switch designs, upload wallpapers, change element visibility, set your Home name and city, manage favorites/hidden apps, and toggle the idle screen.
- **Reversible cleanup:** select optional system extras to disable or re-enable. The tool records their original states; it does not uninstall packages or clear app data.
- **Update controls:** restrict known Amazon updater components or resume access. Fire OS can protect those components, so this is best effort, not a guaranteed OTA block. Router/DNS settings are not changed.
- **Restore points:** save, export and import the recorded baseline; restore changes made by Studio and review change history. Apps and their data are retained.
- **Manual computer workflow:** Python + ADB installation, theme/Home selection, status, Home repair, selected cleanup and restoration. The Python helper needs only the standard library.
- **Privacy:** no project accounts, ads or analytics. ADB authorization keys and restore points stay in local app storage. Weather uses Open-Meteo only when a city is selected. No phone remote-control clone is included.

</details>

## Things to know

- **Amazon Home can still appear** before the service starts or during some system transitions. It stays enabled for Settings and recovery; this is not a guaranteed zero-flash replacement.
- Update restrictions and Cast/Inputs/Power actions depend on Fire OS permissions and device support.
- Bright or static wallpapers are not burn-in protection. The screensaver runs only on our Home; other apps keep their own playback/idle behavior.

<details>
<summary><strong>How Home return works and its limitations</strong></summary>

The launcher avoids the old timed handoff between two Home windows. It detects app-initiated Home returns, including Back from YouTube, prepares for wake and starts a lightweight cover while the full launcher loads at boot. All themes use the same routing.

**Amazon Home can still appear before Fire OS connects our service, or after a service/permission failure or some system transitions.** This is not a zero-flash guarantee. Amazon Home stays enabled for Settings and recovery; there is no root or firmware patch.

Sleep, Restart, Cast and Inputs depend on the TV's firmware and permissions. The wallpaper screensaver is not burn-in protection: bright/static wallpapers can still retain imagery. No universal performance or movie-length playback guarantee is claimed.

</details>

<details>
<summary><strong>Compatibility and tested hardware</strong></summary>

| Device/software | Status |
| --- | --- |
| Fire TV Stick HD — AFTSS, Fire OS 7.7.1.7 / Android 9, **armeabi-v7a** | Physically tested. Both APKs contain no native ABI-specific libraries; ARMv7 needs no special download. |
| Other Android-based Fire TVs, Android 8 / API 26 or newer | Unverified; firmware, ADB and accessibility restrictions vary. |
| Android phone | Android 9 or newer. |
| Fire OS 5/6, Vega OS, non-Fire Android TV / Google TV | Outside this fork's supported installer scope. Fire OS 5/6 are below the launcher's minimum API. |
| Wireless debugging | Classic authorized ADB over TCP, usually port 5555. Android's newer TLS pairing flow is not supported. |

Upstream device coverage is not a claim of support by this fork. [Detailed compatibility](docs/COMPATIBILITY.md) · [What was tested](docs/VALIDATION.md)

</details>

## Help and source

[Latest release](https://github.com/sreeego/tv-home-studio/releases/latest) · [User guide](docs/GUIDE.md) · [Recovery](docs/INSTALL.md#recovery) · [Changelog](CHANGELOG.md) · [Build from source](docs/BUILD.md) · [Validation](docs/VALIDATION.md) · [Privacy](docs/PRIVACY.md)

MIT for this project's code, with upstream notices retained. AdbLib is BSD 3-Clause. [Full credits and licenses](docs/CREDITS.md). Wallpapers, app artwork and trademarks belong to their owners; no streaming content or IPTV playlists are distributed.
