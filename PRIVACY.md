# Privacy

Hush is built to collect nothing.

- **No account.** There is no sign-in and no Google login.
- **No analytics, ads or crash reporting.** The app contains no tracking SDKs and talks to no
  servers operated by the Hush project (there are none).
- **Local storage only.** Liked songs, playlists and your last 50 played songs ("Recently played")
  live in a database on your device. Uninstalling the app deletes them. Nothing is synced or
  uploaded. You can clear the play history any time from Library.
- **On-device recommendations.** The "Because you liked ..." rows are built from your liked songs
  on the phone. The only thing sent out is an ordinary request to YouTube for songs related to one
  song title, the same as playing it.
- **Recent searches and settings.** Your last 8 searches and your settings (accent colour, suggestions
  switch) are stored only on the device. Clear searches any time in Search or Settings.
- **Search suggestions.** While you type, the partial text is sent to YouTube to get suggestions.
  This is the only time unfinished searches leave your phone. Turn it off in Settings and nothing is
  sent until you press search.
- **Listening stats.** Hush counts your plays and listening time on the device so it can show your
  top songs and artists. They are never uploaded and are not included in backups. Reset them any time.
- **Daily Mix.** The mix is built on the phone. Like the "Because you liked ..." rows, it asks YouTube for
  songs related to a few of your songs.
- **Update check.** Off by default. If you switch it on (or tap "Check for updates now"), Hush asks
  GitHub for the latest release of this project. Nothing about you is sent, but GitHub can see your IP
  address, as with any website.
- **Playlist import.** If you import a playlist, the link you paste is opened on YouTube or Spotify
  (Spotify's public embed page, no login), and the song names are searched on YouTube Music. Nothing
  is sent unless you start an import.
- **Widgets.** Home-screen widgets show the song that is playing. They get it from the app on your
  phone and contact nothing.
- **Backup file.** "Back up library" writes your likes and playlists to a file you choose. Hush never
  uploads it; where you store it is up to you.
- **Region and language.** Hush reads your phone's country and language and uses them in its requests
  to YouTube so results feel local. Nothing else about your device is sent.
- **Network requests.** To search and play music, the app contacts YouTube and Google video hosts
  directly from your device, so those services can see your IP address and the requests you make,
  as they would for any client. Cover art is loaded from the image hosts those services provide.
- **Permissions.** `INTERNET` to stream, `FOREGROUND_SERVICE` and `WAKE_LOCK` for background
  playback, and `POST_NOTIFICATIONS` for the playback notification.

If this ever changes, it will be stated here and in the changelog.
