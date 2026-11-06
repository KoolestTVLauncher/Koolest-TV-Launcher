# Remote Buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Koolest learns any remote's buttons from what it sends and lets people assign each one an action, either only in Koolest or everywhere.

**Architecture:** A small pure model (`RemoteKeys.kt`) holds the saved mappings and key naming. `MainActivity.dispatchKeyEvent` records buttons it sees, captures one while learning, and runs mappings inside Koolest. `HomeOverride.onKeyEvent` (the existing accessibility service) runs Everywhere mappings, and hands Koolest actions to `MainActivity` through an intent extra. The settings pages use the existing `showPage`/`page` helpers.

**Tech Stack:** Kotlin, Android SDK only (minSdk 26, targetSdk 36), JUnit 4 for unit tests.

**Spec:** `docs/superpowers/specs/2026-11-04-remote-buttons-design.md` (issue #16)

## Global Constraints

- No third-party libraries, no new permissions, no new services. Use `HomeOverride`.
- No hardcoded remotes, brands or vendor key codes. The only fixed list is the core keys that are never remapped: D-pad up/down/left/right/center, Enter, Numpad Enter, Back, Home, Power, Volume up/down, Mute (`KEYCODE_VOLUME_MUTE`, `KEYCODE_MUTE`).
- Default with no mapping: `KEYCODE_MENU` opens the item menu (already in #15). Every other key does what it does today.
- Outside Koolest, only buttons explicitly set to Everywhere are touched.
- Layout lock: Remote buttons page is inside Settings (already locked). Actions that change the home screen go through `unlock {}`. Opening an app is allowed while locked.
- Strings in `values`, `values-fr`, `values-fa`. Persian is fluent everyday Iranian Persian, with no ـهٔ, no tanvin and no ئ/أ.
- Both flavors. Never `git commit -a`; add files by name (`gradlew.bat` noise).
- Branch `feat/16-remote-buttons`, stacked on `feat/13-menu-button` until PR #15 merges. Commits mention `(#16)`.

## Review Focus

1. **Holding a mapped button** (key repeat): the action runs once on key up, and the repeats are swallowed. Test: `RemoteKeysTest.firesOnlyOnCleanUp`.
2. **Unnamed buttons sending `KEYCODE_UNKNOWN`** (0) must not all collapse into one button. Use the scan code. If the scan code is also generic (240, Linux `KEY_UNKNOWN`), they can't be told apart, and the learn page says so. Test: `RemoteKeysTest.unknownKeysUseScanCode`.
3. **A mapped app gets uninstalled**: the button falls back to the TV's behaviour, and the row shows "Not installed". Test: `RemoteKeysTest.decodeKeepsMissingApps` (the model keeps the entry; the activity checks `apps`).
4. **The PIN pad is open**: keys go to the PIN pad, and mappings and learning don't run (`currentFocus === pinBox`, or the PIN page is showing).
5. **A corrupt or old saved value** decodes to an empty or partial list, never a crash. Test: `RemoteKeysTest.decodeIgnoresGarbage`.

---

### Task 1: The mapping model

**Files:**
- Create: `app/src/main/java/dev/koolest/RemoteKeys.kt`
- Test: `app/src/test/java/dev/koolest/RemoteKeysTest.kt`

**Interfaces:**
- Produces:
  - `data class KeyMap(val id: Int, val action: String, val arg: String = "", val everywhere: Boolean = false, val name: String = "")`
  - Action ids (`const val` in `object RemoteKeys`): `APP="app"` (arg = package, HDMI tiles included as they are in `apps`), `MENU="menu"`, `ALL_APPS="all_apps"`, `HOME="home"`, `SEARCH="search"`, `SETTINGS="settings"`, `SCREENSAVER="screensaver"`, `ANDROID_SETTINGS="android_settings"`, `TV="tv"` (let the TV handle it), `NONE="none"` (swallow).
  - `RemoteKeys.encode(maps: List<KeyMap>): String` and `RemoteKeys.decode(s: String?): List<KeyMap>`: one map per line, fields separated by a tab, with name and arg escaped so tabs and newlines can't break it.
  - `RemoteKeys.id(keyCode: Int, scanCode: Int): Int`: `keyCode` when it isn't 0. Otherwise `UNKNOWN_BASE + scanCode`, with `UNKNOWN_BASE = 1_000_000`.
  - `RemoteKeys.isCore(keyCode: Int): Boolean`
  - `RemoteKeys.readable(raw: String): String?`: `"KEYCODE_PROG_RED"` becomes `"Prog red"`. Returns null for a bare number (`KeyEvent.keyCodeToString` returns digits for codes Android has no name for, like TCL's 4056).
  - `RemoteKeys.fires(action: Int, repeatCount: Int, canceled: Boolean): Boolean`: true only for `ACTION_UP` that isn't canceled. Down and repeat events are consumed but don't fire.

- [ ] **Step 1: Write the failing tests** in `RemoteKeysTest`:
  - `roundTrips`: encode then decode of `[KeyMap(183,"app","com.spotify.tv.android",true,"Red"), KeyMap(1000240,"none")]` equals the input. A name with a tab and a newline survives.
  - `decodeIgnoresGarbage`: `decode(null)`, `decode("")` and `decode("x\ty\n183\tapp")` give an empty list or only the valid lines, with no exception.
  - `decodeKeepsMissingApps`: an `app` mapping keeps its package whatever is installed.
  - `unknownKeysUseScanCode`: `id(0, 240) == 1_000_240`, `id(183, 999) == 183`.
  - `coreKeysAreProtected`: `isCore` is true for 19, 20, 21, 22, 23, 66, 160, 4, 3, 26, 24, 25, 164, 91, and false for 82 (Menu), 183 and 4056.
  - `readableNames`: `readable("KEYCODE_PROG_RED") == "Prog red"`, `readable("KEYCODE_CHANNEL_UP") == "Channel up"`, `readable("4056") == null`.
  - `firesOnlyOnCleanUp`: `fires(ACTION_UP=1, 0, false)` is true; `fires(0, 0, false)`, `fires(0, 3, false)` and `fires(1, 0, true)` are false. Use the literal ints so the test stays a plain JVM test.

- [ ] **Step 2: Run them and see them fail**
  Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew testSideloadDebugUnitTest --tests dev.koolest.RemoteKeysTest`
  Expected: compile failure, because `RemoteKeys` is not defined.

- [ ] **Step 3: Implement `RemoteKeys.kt`** with the interfaces above. Don't use any Android classes, so the tests run on the JVM.

- [ ] **Step 4: Run the tests and see them pass** (same command): `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit** `RemoteKeys.kt` and `RemoteKeysTest.kt`: `feat: remote button mapping model (#16)`

### Task 2: Buttons inside Koolest

**Files:**
- Modify: `app/src/main/java/dev/koolest/MainActivity.kt`, in `dispatchKeyEvent` (around line 1936, before the Menu block from #15), plus new private helpers next to `onHold`.

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `private fun keyMaps(): List<KeyMap>` and `private fun saveKeyMaps(m: List<KeyMap>)`, using pref `"remote_keys"`.
  - `private fun seenKeys(): List<Int>`, using pref `"remote_seen"`: a comma-separated list of ids, newest first, capped at 30.
  - `internal fun noteKey(id: Int)`: adds the id to the front of the seen list if it's new.
  - `private var learning: ((Int) -> Unit)? = null`: Task 4 sets it. The next non-core key id goes to it instead of being handled.
  - `internal fun runKeyAction(m: KeyMap)`: runs an action. APP uses the same launch path as a row tile (falls back to the TV if the package isn't in `apps`). MENU calls `onHold(currentFocus)`. ALL_APPS calls `openAllApps()`. HOME closes overlays and focuses `firstRowEntry()`. SEARCH calls `search()`. SETTINGS calls `openLauncherSettings()` (already behind the lock). SCREENSAVER calls `testScreensaver()`. ANDROID_SETTINGS calls `openSettings(Settings.ACTION_SETTINGS)`.

- [ ] **Step 1: Add the handling.** In `dispatchKeyEvent`, first, for a non-core key that doesn't come from the PIN pad: compute the id with `RemoteKeys.id(e.keyCode, e.scanCode)`; on `ACTION_DOWN` with repeat 0, call `noteKey`. If `learning` is set, pass the id on key up and consume the event. If there's a mapping with action `TV` (or no mapping), fall through to today's code. `NONE` consumes the key. Any other action consumes down, repeat and up, and runs `runKeyAction` when `RemoteKeys.fires(...)`. If a mapping exists, the Menu block from #15 doesn't run.
- [ ] **Step 2: Build and run unit tests**: `./gradlew testSideloadDebugUnitTest testPlayDebugUnitTest assembleSideloadDebug` should give `BUILD SUCCESSFUL`.
- [ ] **Step 3: Emulator check (tv34).** Write a mapping with `run-as dev.koolest` into `shared_prefs/home.xml` (`remote_keys` = `183\tall_apps\t\tfalse\t`), then `adb shell input keyevent KEYCODE_PROG_RED`. Expected: All apps opens. `adb shell input keyevent KEYCODE_MENU` with no mapping still opens the item menu.
- [ ] **Step 4: Commit** `MainActivity.kt`: `feat: run remote button mappings inside Koolest (#16)`

### Task 3: Everywhere, through the Home button service

**Files:**
- Modify: `app/src/main/java/dev/koolest/HomeOverride.kt`, in `onKeyEvent` (currently Home only).
- Modify: `MainActivity.kt`: handle the intent extra `"remote_action"` (the encoded single `KeyMap`) in `onCreate` and `onNewIntent`, and call `runKeyAction`.

**Interfaces:**
- Consumes: Task 1, and Task 2's `runKeyAction` and `noteKey` through the intent.
- Produces: `HomeOverride.learning: ((Int) -> Unit)?` (companion object), which the learn page sets so a button can be learned while the service consumes keys.

- [ ] **Step 1: Implement.** Home stays exactly as it is. For any other non-core key: if `learning` is set, pass the id on clean key up and return true. Otherwise read `RemoteKeys.decode(prefs "remote_keys")`. If there's no Everywhere mapping for the id, return false (the key goes on as before). `NONE` returns true. `APP` runs on clean key up with the package's leanback launch intent (else its launch intent) and `FLAG_ACTIVITY_NEW_TASK`. `ANDROID_SETTINGS` starts the settings intent. Koolest actions start `MainActivity` with `remote_action` and `FLAG_ACTIVITY_NEW_TASK`. Return true for down, repeat and up. `MENU` is never Everywhere (the settings page doesn't offer it). If one is found anyway, return false.
- [ ] **Step 2: Build**, same command as Task 2.
- [ ] **Step 3: Emulator check.** Turn on the service (Settings → This TV → Home button). Map `KEYCODE_PROG_GREEN` (184) to Spotify, Everywhere. Open YouTube, then `adb shell input keyevent 184`. Expected: Spotify opens. With the service off, the same key inside Koolest still opens Spotify, and inside YouTube nothing changes.
- [ ] **Step 4: Commit** `HomeOverride.kt` and `MainActivity.kt`: `feat: remote buttons work everywhere through the Home button service (#16)`

### Task 4: Settings → This TV → Remote buttons, learn, strings

**Files:**
- Modify: `MainActivity.kt`: add the row to `renderSettings`'s list at index `REMOTE_ROW = 33`, add it to the This TV group after `LOCK_ROW` in both branches of `SETTINGS_GROUPS`, and give it an icon in `SETTING_ICONS` (reuse `R.drawable.ic_s_tv`). Add these pages: `remoteButtons(focus: Int = 0)`, `remoteKeyPage(id: Int)`, `pickKeyAction(id: Int)`, `pickKeyApp(id: Int)`, `learnKey()`, `renameKey(id: Int)` (reuse the city search box's `EditText` pattern from `pickCity`).
- Modify: `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`.

**Interfaces:**
- Consumes: Tasks 1–3 (`keyMaps`, `saveKeyMaps`, `seenKeys`, `learning`, `HomeOverride.learning`, `homeServiceOn()`).

- [ ] **Step 1: Pages.**
  - `remoteButtons`: first row is "Add a button". Then one row per id in `seenKeys()`, merged with mapped ids that aren't in the seen list, newest first. The title is the mapping's name, else `RemoteKeys.readable(KeyEvent.keyCodeToString(id))` for ids below `UNKNOWN_BASE`, else "Button <id>". The value is the action's label, or "TV" (`remote_tv`) when unmapped. If the mapping is Everywhere and the service is off, show the hint `remote_needs_service` as the value.
  - `remoteKeyPage(id)`: What it does (opens `pickKeyAction`), Where (Only in Koolest / Everywhere; hidden for MENU, TV and NONE), Rename, Forget this button (removes it from mappings and the seen list).
  - `pickKeyAction`: the actions in this order: TV, an app… (opens `pickKeyApp`, same list style as `addRailApp`), MENU, ALL_APPS, HOME, SEARCH, SETTINGS, SCREENSAVER, ANDROID_SETTINGS, NONE.
  - `learnKey`: page text "Press the button you want to set". Set `learning` and `HomeOverride.learning` and start a 10 s timeout. On an id: clear both, `noteKey(id)`, then open `remoteKeyPage(id)`. On a core key: toast `remote_core`. On timeout: show the page text `remote_not_received`. If the id is `UNKNOWN_BASE + 240`, show `remote_generic` on its page.
- [ ] **Step 2: Strings** (EN, then FR, then FA): `remote_buttons` "Remote buttons" / "Boutons de la télécommande" / "دکمه‌های ریموت"; `remote_add` "Add a button" / "Ajouter un bouton" / "افزودن دکمه"; `remote_press` "Press the button you want to set" / "Appuyez sur le bouton à régler" / "دکمه‌ای را که می‌خواهید تنظیم کنید فشار دهید"; `remote_not_received` "Koolest didn't get that button. Your TV probably keeps it for itself." / "Koolest n’a pas reçu ce bouton. Votre téléviseur le garde sans doute pour lui." / "Koolest این دکمه را دریافت نکرد. احتمالا تلویزیون خودش از آن استفاده می‌کند."; `remote_core` "This button can't be changed" / "Ce bouton ne peut pas être modifié" / "این دکمه قابل تغییر نیست"; `remote_generic` "This remote sends the same signal for several buttons" / "Cette télécommande envoie le même signal pour plusieurs boutons" / "این ریموت برای چند دکمه یک سیگنال می‌فرستد"; `remote_tv` "TV decides" / "Le téléviseur décide" / "به عهده‌ی تلویزیون"; `remote_none` "Nothing" / "Rien" / "هیچ"; `remote_an_app` "Open an app…" / "Ouvrir une appli…" / "باز کردن برنامه…"; `remote_home` "Koolest home" / "Accueil Koolest" / "خانه‌ی Koolest"; `remote_android_settings` "Android settings" / "Paramètres Android" / "تنظیمات اندروید"; `remote_where` "Where" / "Où" / "کجا"; `remote_only_koolest` "Only in Koolest" / "Seulement dans Koolest" / "فقط در Koolest"; `remote_everywhere` "Everywhere" / "Partout" / "همه‌جا"; `remote_needs_service` "Turn on Koolest: Home button to use it everywhere" / "Activez Koolest : bouton Accueil pour l’utiliser partout" / "برای استفاده در همه‌جا، Koolest: Home button را روشن کنید"; `remote_rename` "Rename" / "Renommer" / "تغییر نام"; `remote_forget` "Forget this button" / "Oublier ce bouton" / "فراموش کردن این دکمه"; `remote_button_n` "Button %1$d" / "Bouton %1$d" / "دکمه‌ی %1$d"; `not_installed` "Not installed" / "Non installée" / "نصب نیست". Reuse existing `open`, `all_apps`/`menu` labels, `search`, `settings_title`, `screensaver` where they exist (check `strings.xml` first).
- [ ] **Step 3: Build, unit tests, and `PersianTextTest`** (it checks the Persian house rules): `./gradlew testSideloadDebugUnitTest testPlayDebugUnitTest assembleSideloadDebug` should give `BUILD SUCCESSFUL`.
- [ ] **Step 4: Emulator check (tv34), in English and Persian.** Go to Settings → This TV → Remote buttons → Add a button, then run `adb shell input keyevent KEYCODE_PROG_YELLOW`. Its page should open as "Prog yellow". Set it to All apps, press it, and All apps should open. Forget it. Add a button with no press: after 10 s the "didn't get that button" text should show. Press `KEYCODE_DPAD_UP` while learning: "can't be changed".
- [ ] **Step 5: Commit** `MainActivity.kt` and the three `strings.xml`: `feat: Remote buttons settings with learn by pressing (#16)`

### Task 5: Docs, real TV, PR

**Files:**
- Modify: `README.md` (controls table: a "Set what remote buttons do" row pointing to **Settings → This TV → Remote buttons**), `CHANGELOG.md` (an Unreleased entry).
- Commit the spec and this plan.

- [ ] **Step 1: Edit README and CHANGELOG.**
- [ ] **Step 2: Install the signed sideload release** on the TCL (`adb -s 192.168.2.29:5555 install -r`), and ask the maintainer to try Add a button with the Settings key, the TCL key and the app buttons. Report which ones Koolest received.
- [ ] **Step 3: Commit** the docs, spec and plan: `docs: remote buttons in README and changelog (#16)`
- [ ] **Step 4: Push and open the PR** against `feat/13-menu-button` (or `main` once #15 has merged), with `Closes #16`. Run the `pr-reviewer` agent, fix any blocking findings, and check CI.
