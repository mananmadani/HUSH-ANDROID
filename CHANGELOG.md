# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.6]

### Added
- "Your playlists" row on Home with Liked songs and every playlist you made, with cover collages.
- Two home-screen widgets: Now playing (4x2, cover, title, previous / play / next) and Mini (2x2).
  Widgets show only what is playing on your phone and use your accent colour.

## [1.0.5]

### Fixed
- Adding songs to a playlist: every song row now has an "add to playlist" button, and the picker
  lists your playlists (or lets you create one) and confirms with a message. Works in Search,
  Library lists, mood lists and the player.

## [1.0.4]

### Fixed
- Text was dark and hard to read on the dark background; it is now light everywhere.
- Cover art was blurry because YouTube sends tiny thumbnails; Hush now asks for the large version.

### Added
- "Developed by Manan Madani" at the bottom of Home and in Settings.

## [1.0.3]

### Added
- New "Dusk" design: floating navigation, glass mini player, accent colour choice, bundled Inter font.
- Home: mood chips, a featured card and a ranked list for your country.
- Search: live suggestions (optional), recent searches and mood tiles.
- Library: cover collages for playlists, backup and restore to a file.
- Player: colours taken from the cover art, swipe down to close, queue with jump and remove, sleep timer, share.
- Settings screen.

### Changed
- Pull-to-refresh is a refresh button, as before.

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
