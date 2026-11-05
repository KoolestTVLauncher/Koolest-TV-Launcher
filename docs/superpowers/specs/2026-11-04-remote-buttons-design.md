# Remote buttons: design

Date: 2026-11-04. Follows issue #13 (Menu button opens the app menu, PR #15).

## Why

Android TV remotes differ by brand. Koolest today only reacts to the D-pad, OK, Back, Home, numbers (PIN pad) and, since #15, Menu. Every other button does nothing in Koolest, and many remotes have no Menu key at all (the maintainer's TCL remote has Settings and a TCL quick-menu key instead). The maintainer wants Koolest to work sensibly with any remote out of the box, and to let people choose what extra buttons do.

## Goals

1. **No per-remote or per-brand tables.** There are thousands of remotes. Koolest learns buttons from the remote in front of it instead of shipping a list of key codes.
2. **Sensible defaults with no setup**, based only on Android's own meaning of a key (Menu is Android's standard "menu" key), never on which remote it is.
3. **A settings page to customize buttons**: Settings → This TV → **Remote buttons**.
   - Lists the buttons this remote has actually sent to Koolest, each with its current action.
   - **Add a button**: Koolest asks you to press a button and learns it.
3. **Actions a button can do**: open an app; Koolest actions; TV actions; let the TV handle it; do nothing.
4. **Per button, "Only in Koolest" or "Everywhere"**, so a button can work inside other apps (for example inside YouTube).
5. Both builds (GitHub sideload and Play) get the feature.

## Non-goals

- Remapping Home, Power, Volume or Mute. Home keeps today's behaviour (press: Koolest; hold: Google TV dashboard).
- Long-press actions, double press, macros, per-app profiles.
- Gamepads.
- Buttons the TV keeps for itself before any app sees them. Koolest cannot get these and must say so honestly instead of pretending.

## Anti-goals (failure even if it "works")

- An unassigned button behaves differently from today in other apps. Outside Koolest, Koolest only touches buttons someone explicitly set to Everywhere.
- The layout lock can be bypassed (for example, a button that opens Koolest settings without the PIN).
- A third-party library, a new permission or a new service. Use the existing `HomeOverride` accessibility service.
- A hardcoded list of remotes, brands or vendor key codes to maintain.
- A cluttered settings page listing buttons the remote doesn't have.

## What we know about remotes (research, 2026-11-04)

- Remote keys arrive as Android `KeyEvent`s. Android maps HID usage or scan codes to key codes through the device's `.kl` key layout file.
- Some keys never reach apps: Home and Power are handled by the system. Vendors also catch their own keys. On the maintainer's TCL (Google TV, Bluetooth remote `BT_RC813_MG`, layout `Vendor_0417_Product_0304.kl`), most extra buttons send HID consumer usages that the standard layout doesn't know (`KEY_UNKNOWN`). TCL's `TclKeyLayoutMap` / `shortcutket.tkl` turns them into **private key codes above Android's range** (seen: 4056 for usage `0x0c0052`, 4137 for `0x0c003a`), and `WindowManager.handleKeyBeforeQueueing` handles them. Whether these private codes reach apps or the accessibility key filter is unconfirmed, which is why learning by pressing matters.
- Standard keys seen on that remote: Channel up/down, Back, Mute, D-pad. Usage `0x0c0049` is mapped to `TV_INPUT` by the layout file.
- Accessibility services with key filtering (how Button Mapper and tvQuickActions work) get keys before the focused app, so Everywhere is possible through `HomeOverride.onKeyEvent`, which already handles Home.
- Sources: Android Central, Android Police (Chromecast button remapping), tvQuickActions, TCL support pages, Home Assistant community thread on Google TV keycodes.

## Behaviour

### Defaults (no setup, Only in Koolest)

- Android's standard **Menu** key (`KEYCODE_MENU`) opens the focused item's menu, as shipped in #15. This is Android's convention for every remote, not a per-remote rule.
- Every other button is left to the TV (today's behaviour) until someone assigns it.
- Defaults never apply outside Koolest.

### Actions

- **Open an app**: chosen from the installed apps list (same picker style as Settings → Menu apps → Add an app).
- **Koolest**: Item menu, All apps, Home (Koolest home screen, first row), Search, Koolest settings (asks for the PIN when locked, like everywhere else), Start screensaver.
- **TV**: Inputs (the TV's input picker), a specific input (an HDMI tile Koolest already knows), Android settings.
- **Let the TV handle it**: Koolest ignores the button; it does whatever it did before.
- **Do nothing**: Koolest swallows the button.

Item menu only makes sense inside Koolest, so for that action the Everywhere switch is not offered.

### Everywhere

- Each assigned button has an Only in Koolest / Everywhere setting (default: Only in Koolest).
- Everywhere works through the existing **Koolest: Home button** accessibility service: `onKeyEvent` consumes both down and up, and runs the action on key up.
- If that service is off, the button still works inside Koolest, and its row says: "Turn on Koolest: Home button to use it everywhere" (focusing that opens the existing Home button setup).
- Koolest actions run from outside Koolest bring Koolest to the front first (for example, All apps from inside YouTube opens Koolest's All apps).

### Settings page

Settings → This TV → **Remote buttons**:

1. **Add a button** at the top.
2. One row per **button this remote has sent**, newest first, with its current action as the subtitle. Koolest remembers every non-core key it receives, in the app or through the service, so a button shows up here after it has been pressed once. Nothing is pre-listed.
   - Name: Android's own name for the key code, made readable (`KeyEvent.keyCodeToString`: `KEYCODE_PROG_RED` becomes "Prog red", `KEYCODE_CHANNEL_UP` becomes "Channel up"). Codes Android has no name for, like TCL's private ones, are shown as "Button 4056". The person can rename any button ("Netflix").
   - Choosing a row opens: the action list, the Only in Koolest / Everywhere choice when relevant, **Rename**, and **Forget this button**.
3. **Add a button**: shows "Press the button you want to set (10 s)". The next key Koolest receives, in the app or through the service, is captured. If it's excluded (D-pad, OK, Back, Home, Power, Volume, Mute), Koolest says that button can't be changed. If nothing arrives in 10 s: "Koolest didn't get that button. Your TV probably keeps it for itself." If the button is already listed, Koolest opens that button's row.

The only fixed list is the small set of **core keys Koolest never remaps**: D-pad, OK/Enter, Back, Home, Power, Volume, Mute. It is a safety rule, not a remote list.

### Lock and PIN

- The Remote buttons page sits inside Settings, so it is behind the layout lock already.
- While the PIN pad is open, keys go to the PIN pad and remaps don't run.
- Actions that change the home screen still ask for the PIN when locked, through the existing `unlock {}`.
- Opening an app with a button is allowed when locked, like opening an app from the row is.

## Constraints

- Kotlin, no third-party libraries, minSdk 26, both flavors. Strings in English, French and Persian. Persian follows the house style: fluent everyday Iranian Persian, no Arabic orthography.
- Keep the existing structure: settings pages through `showPage`/`page`, menus through `holdMenu`, prefs in the existing `SharedPreferences`.
- Key identity is the key code. For `KEYCODE_UNKNOWN` (0), use the scan code with a marker, so unmapped buttons can still be told apart where Android gives a distinct scan code.
- The TV-side test bed is the maintainer's TCL (Google TV 14), plus the emulator AVDs (`tv34`, `atv31`, `gtv36`, `o26`) using `adb shell input keyevent`.

## Decided by the maintainer

- Settings location: This TV → Remote buttons.
- No hardcoded remotes or key lists: buttons are learned from what the remote sends.
- Per-button Only in Koolest / Everywhere.
- Sensible defaults out of the box.
- An assigned button overrides whatever the TV did with it.
- Home is not remappable.
- Service off: works in Koolest, with a hint.
- Both builds.

## Builder's calls (settle while building; flagged so the maintainer can check)

- Remembering seen buttons: only after the Remote buttons page has been opened once, or always (in memory is fine; saved when the page opens).
- The Item menu action isn't offered Everywhere.
- Storage format of the mapping in prefs, plus a small unit test for parsing and saving.
- The 10-second learn timeout.
- Whether Search uses the existing search entry point.
- README controls table and website copy updates.
