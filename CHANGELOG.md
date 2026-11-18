# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.16]

### Added
- **Backups now hold everything.** Besides liked songs and playlists, a backup includes recently played songs, what Hush learned about your taste (skips, "Don't play this" and listening scores), your listening stats and your settings (theme, accent colour and the switches). Moving to a new phone keeps your recommendations.
- **Backups are locked with a password.** Each backup is encrypted with AES-256 (GCM), and the key comes from your password with PBKDF2-SHA256. Hush can't recover a forgotten password.

### Changed
- Backup files are now saved as `Hush-backup.hush`. Older plain `.json` backups can still be restored.
- Restoring adds the backup's songs and playlists to the library. Taste, stats and settings in the backup replace the ones on the phone, which suits a new phone.

## [1.0.15]

### Fixed
- Songs that stayed stuck at 0:00 and would not play. YouTube changed how it hands out audio links, so Hush's music engine (NewPipe Extractor) is updated from 0.26.5 to 0.27.1.
- Update alerts arrive sooner. Hush now looks for a new version every time you bring the app to the front (at most every 10 minutes), not only when it starts from scratch, and the background check runs about every 3 hours instead of 6.

## [1.0.14]

### Added
- **Recommendations based on what you really listen to.** Hush gives each song and artist a taste score on your phone: playing a song through or replaying it raises it, skipping it early lowers it, and old listening fades over time.
- A **Made for you** row on Home, built from songs related to the ones you play most, with artists you listen to most first.
- A time-of-day row on Home ("Your morning picks", "Your afternoon picks", "Your evening picks" or "Your late-night picks") with the songs you often play at that time of day.

### Changed
- The Daily Mix and the endless queue now use your listening, not only your likes.
- "Forget skip history" in Settings is now "Forget what Hush learned" and clears both skips and listening scores.

## [1.0.13]

### Fixed
- Hush now tells you about a new version on its own. It checks about every six hours in the background, even when the app is closed, and shows a notification when an update is out. Before, it only looked once a day and only while the app was open.
- The in-app update banner now checks every time you open Hush (at most every 30 minutes).

### Changed
- Turn this off any time with "Check for updates automatically" in Settings > Updates.

## [1.0.12]

### Added
- "Don't play this" and "Don't play this artist" in the song menu. Hush stops suggesting and queueing them. Undo it in Settings > Recommendations.
- Hush learns from skips, on your phone only: an artist you keep skipping in the first seconds is left out of suggestions. Your own playlists and searches are never changed. "Forget skip history" in Settings clears it.

### Changed
- Smarter endless queue: when your queue runs out, Hush now looks at your last few songs instead of only the last one, blends the suggestions, and avoids playing the same artist back to back.
- Daily Mix and the "Because you liked" rows also leave out hidden songs and artists you keep skipping.

## [1.0.11]

### Fixed
- Shuffle now starts on a random song. Before, playing a list with Shuffle always began with the first song, and only the rest was shuffled.

### Removed
- Smooth song changes and DJ mode (added in 1.0.10), to keep Hush small and focused. The new Library design stays.

## [1.0.10]

### Added
- Smooth song changes, Apple Music style: the next song starts while the current one is still ending, and the two blend, so there is no gap. It uses a second player for a few seconds. Switch it off, or pick a blend length of 2 to 12 seconds, in Settings > Playback. Skipping a song also fades the new one in softly.
- DJ mode (beta): adds a tempo ride to the blend, so the ending song speeds up a little and the next one settles back to normal. It is new and may sound better on some songs than others. Switch it on in Settings > Playback.

### Changed
- Library redesigned to match Settings: grouped cards, playlists as clean rows with their cover, and tinted icons for Listening stats, Import, Back up and Restore.

## [1.0.9]

### Added
- Haptic feedback across the app (switch it off in Settings).
- Tap Daily Mix to see its songs, with Play and Shuffle. The round button still plays it straight away.

### Changed
- Cleaner Settings: grouped cards, shorter text, a slimmer layout, and nothing hidden behind the mini player.
- Listening stats redesigned: songs and artists in cards, a bar for each top artist, and a clearer Reset stats button that asks before clearing.
- Update check is now on by default (daily, GitHub only). Switch it off in Settings.

## [1.0.8]

### Added
- Daily Mix on Home: a mix for the day built on your phone from your likes and what you played.
- Listening stats (Library > Listening stats): time listened, top songs and top artists, this month or all time. Stored only on your phone.
- Update check: switch it on in Settings, or tap "Check for updates now". Hush asks GitHub for the newest release and shows a banner on Home when there is one.
- Search inside Liked songs and inside any playlist.

## [1.0.7]

### Added
- Artist pages (top songs and albums) and album pages. Tap an artist in the player, or choose "Go to artist" on any song.
- Search now has Songs, Artists, Albums and Playlists tabs.
- Import a public YouTube, YouTube Music or Spotify playlist from a link (Library > Import a playlist).
- "Save to my playlists" on any album or online playlist.
- Light theme, and three more accent colours (Rose, Gold, Teal). Both are in Settings.
- The Hush logo next to the app name on Home and in Settings.

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
