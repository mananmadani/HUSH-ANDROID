package app.hush

import java.util.Locale

/** The phone's own country, read locally so Home and search feel local. */
object Region {
    val countryCode: String
        get() = Locale.getDefault().country.ifEmpty { "US" }

    val languageCode: String
        get() = Locale.getDefault().language.ifEmpty { "en" }

    val countryName: String
        get() = Locale("", countryCode).getDisplayCountry(Locale.ENGLISH).ifEmpty { countryCode }
}
