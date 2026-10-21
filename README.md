<h1 align="center">Koolest TV Launcher</h1>

<p align="center">
  A fast, ad-free home screen for Android TV and Google TV, in the look of the 30nama TV app.
</p>

<p align="center">
  <a href="https://github.com/benymaxparsa/Koolest-TV-Launcher/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/benymaxparsa/Koolest-TV-Launcher?label=release"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84">
  <img alt="APK size" src="https://img.shields.io/badge/APK-0.7%20MB-blue">
  <a href="LICENSE"><img alt="GPL-3.0 License" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
</p>

<p align="center">
  <img src="docs/home.jpg" alt="Koolest home screen: menu on the left, a billboard for the focused video, and a YouTube row" width="100%">
</p>

Koolest replaces the stock home screen with what you actually use: your apps, your consoles and what's on in them. There are no ads, no sponsored rows, no account and no tracking. It's a single small APK with no third-party libraries.

## Highlights

- **30nama TV layout.** A menu on the left edge, a large billboard for whatever is focused, and rows of landscape cards below it, set in Vazirmatn with a white focus ring.
- **Content rows.** YouTube, 30nama and Spotify each get a row of their own recommendations, plus Continue watching from Android TV's Watch Next list.
- **Consoles and inputs.** Devices that name themselves over HDMI-CEC get a tile with their own mark: PlayStation 3/4/5, Xbox, Switch, Steam Deck, Apple TV, Shield, Chromecast and more. Selecting one switches the TV to that input.
- **A taskbar menu.** Search, Home, your pinned apps and consoles, then All apps. Add, remove and reorder the pins in settings.
- **All apps, sorted your way.** Home order, A–Z, most used, recently used or recently installed.
- **Motion.** Focused cards grow and lift, rows glide into place and the background crossfades to the focused item's art.
- **Simple settings.** Six groups: Look, Home screen, Continue watching, Weather, Screensaver, and Koolest and this TV.
- **Home button support on Google TV.** The Home button opens Koolest even where the system launcher can't be replaced. Holding Home still opens the Google TV dashboard.
- **Extras:** light, dark or automatic theme with your own wallpapers (upload from your phone by QR code), a Gallery screensaver, optional weather and a PIN lock for the layout.

## Requirements

- Android TV or Google TV running **Android 8.0 (Oreo) or newer**
- A way to sideload an APK: the *Downloader* app, a USB drive with a file manager, or `adb`

## Installation

1. Download the latest APK from the [Releases](https://github.com/benymaxparsa/Koolest-TV-Launcher/releases/latest) page, or build it yourself (below).
2. Install it on your TV:
   - **USB drive:** copy the APK over and open it with a file manager.
   - **adb:** `adb install Koolest.apk`
3. Open **Koolest TV Launcher** from your apps. Its settings open automatically on first launch.

### First-run setup

In **Koolest settings → Koolest and this TV**:

| Setting | What to do |
|---|---|
| **Home button opens Koolest** | Press OK. In the Accessibility screen that opens, select **Koolest: Home button** and turn it on. |
| **Make Koolest the home screen** | Press OK and choose **Koolest TV Launcher** in the dialog. |

Allow the **TV listings** permission when asked; the content rows and Continue watching need it.

> [!NOTE]
> Android shows the same generic warning for every accessibility service. Koolest's service only listens for the Home key and for the system launcher appearing. It cannot read screen content.

> [!TIP]
> **Android 13 and newer:** Android blocks sideloaded apps from Accessibility at first. Open **Settings → Apps → Koolest TV Launcher**, choose **Allow restricted settings** from the menu, then turn the service on.

### Optional: one extra permission over adb

```sh
adb shell pm grant dev.plume android.permission.WRITE_SECURE_SETTINGS
```

This lets Koolest set the Gallery screensaver itself and turn its Home button service back on if the app is ever force-stopped. Everything else works without it.

## Using Koolest

| Action | How |
|---|---|
| Pin, remove or reorder apps in the menu | **Settings → Home screen → Apps in the menu** |
| Rearrange the Apps row | Hold **OK** on an app, or **Settings → Home screen → Rearrange apps** |
| Hide a Continue watching item | Hold **OK** on its card |
| Choose which apps feed Continue watching | **Settings → Continue watching → Which apps appear** |
| Sort All apps | Open **All apps** and pick a sort at the top |
| Change theme or wallpaper | **Settings → Look** |
| Lock the layout with a PIN | **Settings → Koolest and this TV → Lock layout with a PIN** |
| Open the Google TV dashboard | Hold the **Home** button |

### Why only one YouTube account?

YouTube publishes its home-screen rows (Recommended, Subscriptions, Trending and so on) for the account that's active in the YouTube app only, and offers no way for other apps to read another account's recommendations. Switch accounts in YouTube and Koolest's YouTube row follows.

### Custom wallpapers

In **Settings → Look**, open a theme's wallpaper and choose **Add from phone…**. Scan the QR code with your phone (on the same Wi-Fi as the TV) and upload a photo. You can also copy `.jpg`, `.png` or `.webp` files into the app's folder:

```sh
adb push my-wallpaper.jpg /sdcard/Android/data/dev.plume/files/wallpapers/
```

## Privacy

Koolest has no analytics, no ads and no account. It only connects to the internet to:
- load artwork for the cards on the home screen
- check this repository's GitHub releases for a new version, once a day, and download it when you choose to update
- download the Gallery paintings from this repository, **only if you choose that screensaver**
- fetch the weather, **only if you turn it on**, via [ipwho.is](https://ipwho.is) (approximate location from your IP) and [Open-Meteo](https://open-meteo.com)

**Add from phone…** runs a small upload page on the TV itself, reachable only on your home network and only while that screen is open.

## Building from source

```sh
git clone https://github.com/benymaxparsa/Koolest-TV-Launcher.git
cd Koolest-TV-Launcher
./gradlew testDebugUnitTest assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Without a `keystore.properties` file, release builds are signed with your local debug key, which is fine for installing your own build.

The package name is still `dev.plume`, so an existing install keeps its settings when it updates.

## Credits

- Based on [Plume Launcher](https://github.com/aradhyas8/Plume_Launcher) by aradhyas8 (GPL-3.0).
- Design language from the 30nama TV app.
- **Font:** [Vazirmatn](https://github.com/rastikerdar/vazirmatn) by Saber Rastikerdar (SIL Open Font License 1.1).
- **Logos:** [Simple Icons](https://simpleicons.org) (CC0) and [Material Symbols](https://fonts.google.com/icons) (Apache 2.0). All trademarks belong to their respective owners.
- **Screensaver paintings** (public domain, via Wikimedia Commons; files in [`screensaver/`](screensaver)): Frederic Edwin Church, Ivan Shishkin, Jasper Francis Cropsey and Martin Johnson Heade.
- **Wallpaper:** "Canada" by Thomas Curryer, via [Unsplash](https://unsplash.com/@tawc20).

## License

Copyright (C) 2026 aradhyas8 (Plume Launcher)
Copyright (C) 2026 Parsa Kamalipour (Koolest TV Launcher)

Koolest TV Launcher is free software, released under the [GNU General Public License v3.0](LICENSE). Any distributed version, modified or not, must also be released under the GPL-3.0 with its full source code.
