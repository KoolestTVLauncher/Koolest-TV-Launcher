# Contributing to Koolest

Thanks for wanting to help. Koolest is a small project with one maintainer, so this page explains what fits, what doesn't, and how to send a change that can be merged quickly.

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md). Security problems go through [private reporting](SECURITY.md), not a public issue. Questions are welcome in [Discussions](../../discussions).

## What Koolest is

Koolest is a fast, ad-free home screen for Android TV and Google TV with a glass, TV-first look. Every change is weighed against these principles:

- **Small and light.** A 0.7 MB APK, no third-party libraries (AndroidX included), no extra background services.
- **What you use, not what we push.** Rows show your apps, your consoles and what apps report as partway watched. Nothing is sponsored or recommended.
- **Yours, not ours.** No ads, no analytics, no account.
- **Quiet on the network.** Koolest only goes online for the things listed under [Privacy](README.md#privacy). Anything new that connects to the internet must be optional and off until the user turns it on.

## What's out of scope

These will be declined, however well they're built:

- Recommendation, trending or "discover" content, and anything else that fills the home screen with things the user didn't ask for
- Ads, analytics, crash reporting services, or accounts
- New third-party libraries or SDKs
- Services, workers or alarms that run while Koolest isn't on screen, beyond the Home button revival already described in the README
- Online metadata services that need an API key, such as TMDB

Not sure if an idea fits? Open a feature request first and ask. That's much cheaper than finding out after the code is written.

## Before you start

- **Open an issue first, for every pull request.** Use the [bug report or feature request form](../../issues/new/choose), then say in the issue that you'd like to work on it and wait for a reply. Pull requests with no linked issue may be closed, however good the code is.
  - Bug fixes: the issue can be the bug report itself; a maintainer will confirm it so you know the fix is wanted.
  - Features: the issue is where we agree it fits Koolest before any code is written.
  - Typos and wording fixes in the docs don't need an issue.
- **Looking for something to pick up?** See the [good first issue](../../issues?q=is%3Aopen+label%3A%22good+first+issue%22) and [help wanted](../../issues?q=is%3Aopen+label%3A%22help+wanted%22) labels.

## Pull requests

- **Link the issue** with `Closes #123` in the description.
- **One change per pull request.** A bug fix and a new setting are two pull requests.
- **Keep the diff small.** Match the style of the code around your change. Don't reformat or reorganise code you aren't changing.
- **Test on a real TV** (or an Android TV emulator if you have no TV) and say which device and Android version you used. Run `./gradlew testSideloadDebugUnitTest testPlayDebugUnitTest`; the same tests run on every pull request as the **CI** check, which has to pass before merging.
- **Mention size changes.** If your change adds resources or code, give the APK size before and after.
- **Add a line to [CHANGELOG.md](CHANGELOG.md)** under *Unreleased* for anything a user would notice.
- **No new permissions** without discussing them in an issue first.

By sending a pull request you agree that your contribution is licensed under the project's [GPL-3.0 licence](LICENSE).

## Translations

Not open yet. Almost all of Koolest's text lives in the Kotlin code, and `strings.xml` holds a single string, so there is nothing to translate by file. If you'd like to help move the text out first, open an issue.

## Reporting bugs

Use the [bug report form](../../issues/new/choose). The TV model, Android version and Koolest version matter most, because many problems only happen on one manufacturer's TVs. A photo or video of the screen helps a lot.

## Building

See [Building from source](README.md#building-from-source) in the README.
