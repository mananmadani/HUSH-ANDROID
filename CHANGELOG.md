# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.2]

### Added
- Home now uses the phone's country and language, with a "Trending in your country" row.
- Region rows (for example Bollywood, Punjabi, Tamil and Telugu in India).
- "Because you liked ..." rows built on-device from your liked songs.
- "Recently played" row, stored only on the device, with a "Clear listening history" action in Library.
- Home rows now appear as soon as each one loads, and there is a refresh button on Home.

### Changed
- Database upgraded to version 2. Existing liked songs and playlists are kept.

## [1.0.1]

### Added
- First public release, signed with a permanent key so future updates install over it.
- Home shelves and song search over YouTube Music, with no account or ads.
- Full player with queue, shuffle, repeat, seeking and an endless related-songs queue.
- Background playback with notification and lock-screen controls.
- Liked songs and local playlists stored on the device.
- GitHub Actions CI and tag-based release workflow.
