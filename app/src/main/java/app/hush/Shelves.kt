package app.hush

data class ShelfSpec(val title: String, val query: String)

/**
 * Which Home rows to show. The first row is always "Trending in <your country>",
 * followed by a few rows chosen for the phone's region, then the global ones.
 */
object Shelves {

    private val GLOBAL = listOf(
        ShelfSpec("Top hits", "top hits"),
        ShelfSpec("New releases", "new songs"),
        ShelfSpec("Chill", "chill songs"),
        ShelfSpec("Workout", "workout songs")
    )

    fun forRegion(code: String, country: String): List<ShelfSpec> {
        val local = listOf(ShelfSpec("Trending in $country", "top songs $country"))

        val regional = when (code) {
            "IN" -> listOf(
                ShelfSpec("Bollywood hits", "bollywood hits"),
                ShelfSpec("Punjabi hits", "punjabi hits"),
                ShelfSpec("Tamil hits", "tamil hits"),
                ShelfSpec("Telugu hits", "telugu hits")
            )
            "US" -> listOf(
                ShelfSpec("Hip-hop & rap", "hip hop rap hits"),
                ShelfSpec("Country", "country hits"),
                ShelfSpec("Pop", "pop hits")
            )
            "GB" -> listOf(
                ShelfSpec("UK hits", "uk hits"),
                ShelfSpec("Indie & rock", "indie rock hits")
            )
            "BR" -> listOf(
                ShelfSpec("Sertanejo", "sertanejo hits"),
                ShelfSpec("Funk brasileiro", "funk brasileiro hits"),
                ShelfSpec("Pagode", "pagode hits")
            )
            "KR" -> listOf(ShelfSpec("K-pop", "kpop hits"))
            "JP" -> listOf(ShelfSpec("J-pop", "jpop hits"))
            "MX", "ES", "AR", "CO", "CL", "PE" -> listOf(
                ShelfSpec("Latin hits", "latin hits"),
                ShelfSpec("Reggaeton", "reggaeton hits")
            )
            else -> emptyList()
        }

        return local + regional + GLOBAL
    }
}
