# Contributing to Hush

Thanks for helping! Hush is intentionally small, so the bar for new features is high and the bar for
fixes is low.

## Scope

**Welcome:** bug fixes, extractor-breakage fixes, performance, accessibility, translations, tests,
and polish of existing screens.

**Not accepted:** accounts or sign-in, analytics or crash-reporting SDKs, ads, social features,
lyrics, and anything that sends user data off the device. If you are unsure, open an issue first.

## Getting set up

1. Fork the repository and clone your fork.
2. Open it in Android Studio (JDK 17, Android SDK 34), or build with Gradle 8.7+:
   `gradle assembleDebug`.
3. Create a branch: `git checkout -b fix/short-description`.

## Making a change

- Follow the existing structure (`net`, `player`, `data`, `ui`) and keep files focused.
- Use idiomatic Kotlin; follow `.editorconfig` (4 spaces, LF line endings).
- Keep UI state in `HushVm` and the composables stateless where practical.
- Do not add dependencies lightly. Each one needs a clear reason in the PR description.
- Test on a real device or emulator: search, play, background, and the notification controls.

## Commit messages

Short imperative subject line, for example `Fix queue not extending at end of playlist`.
Reference issues with `Fixes #12` in the body.

## Pull requests

- Keep PRs small and about one thing.
- Fill in the PR template and make sure the **CI** check passes.
- Screenshots or a short screen recording help for UI changes.

## When YouTube breaks things

Most outages are fixed by bumping NewPipeExtractor in `app/build.gradle.kts` to the latest release.
If a bump alone does not fix it, include the exception and the extractor version in your issue.

By contributing you agree that your contributions are licensed under the project's
[GPL-3.0](LICENSE) license. Please also follow the [Code of Conduct](CODE_OF_CONDUCT.md).
