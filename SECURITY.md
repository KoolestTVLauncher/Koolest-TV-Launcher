# Security policy

Koolest is a launcher. It can read your installed apps and the TV's home screen listings, and with the optional setup steps it holds an Accessibility service, `WRITE_SECURE_SETTINGS` and notification access. Security reports are taken seriously.

## Supported versions

Only the [latest release](https://github.com/KoolestTVLauncher/Koolest-TV-Launcher/releases/latest) receives fixes.

## Reporting a vulnerability

Please **don't open a public issue**. Use GitHub's private reporting instead:
[Report a vulnerability](https://github.com/KoolestTVLauncher/Koolest-TV-Launcher/security/advisories/new).

Include what you found, the Koolest version, the TV model and Android version, and steps to reproduce. You'll get a reply within a few days; this is a one-person project, so please be patient. Once a fix is released you'll be credited in the release notes unless you prefer otherwise.

## In scope

- An update that installs without coming from this repository's releases, or without a valid signature
- The **Add from phone…** upload page being reachable from outside the home network, or after its screen is closed
- The adb setup steps (**Keep Home working**, **Only launcher**) accepting or running anything beyond what the README describes
- Koolest sending data anywhere not listed under [Privacy](README.md#privacy)
- The Accessibility service or notification listener reading more than the Home key, the launcher appearing, and the playing track
- The PIN lock being bypassed from the remote without the PIN

## Out of scope

- Findings that need a rooted TV or physical access to an unlocked adb connection
- Problems in third-party services Koolest can optionally contact (Open-Meteo, ipwho.is, Lorem Picsum); report those to their owners
- Reports that an unmaintained fork or an APK from another source is unsafe
