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
- **Region and language.** Hush reads your phone's country and language and uses them in its requests
  to YouTube so results feel local. Nothing else about your device is sent.
- **Network requests.** To search and play music, the app contacts YouTube and Google video hosts
  directly from your device, so those services can see your IP address and the requests you make,
  as they would for any client. Cover art is loaded from the image hosts those services provide.
- **Permissions.** `INTERNET` to stream, `FOREGROUND_SERVICE` and `WAKE_LOCK` for background
  playback, and `POST_NOTIFICATIONS` for the playback notification.

If this ever changes, it will be stated here and in the changelog.
