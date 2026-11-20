package app.hush.ui

import android.content.Context
import android.widget.Toast
import app.hush.Taste
import app.hush.data.SongEntity

/** "Don't play this": Hush stops suggesting and queueing the song. Undo it in Settings. */
fun hideSong(context: Context, song: SongEntity) {
    Taste.blockSong(song.url)
    Toast.makeText(context, "Hush won't play this song again", Toast.LENGTH_SHORT).show()
}

/** "Don't play this artist": the same, for every song by the artist. */
fun hideArtist(context: Context, artist: String) {
    Taste.blockArtist(artist)
    Toast.makeText(context, "Hush won't play $artist again", Toast.LENGTH_SHORT).show()
}
