package com.kevinboutwell.thirdstop.core

/**
 * A film stock preset. [nativeIso] is the box speed; [reciprocity] the
 * correction model; [note] an optional caveat surfaced in the UI.
 */
data class FilmStock(
    val id: String,
    val name: String,
    val maker: String,
    val nativeIso: Int,
    val reciprocity: ReciprocityModel,
    val note: String? = null,
) {
    val displayName: String get() = "$maker $name"
}

/**
 * Reciprocity constants are approximations of published datasheet guidance
 * (Ilford technical information sheets give power factors; Kodak and Fujifilm
 * publish adjustment tables that are encoded here as absolute-time anchors).
 * They are deliberately conservative round-offs — verify against the current
 * manufacturer datasheet for critical work.
 */
object FilmStocks {

    val NONE = FilmStock(
        id = "none", name = "No film / digital", maker = "",
        nativeIso = 100, reciprocity = ReciprocityModel.None,
    )

    private fun schedule(vararg points: Pair<Double, Double>) =
        ReciprocityModel.Schedule(points.map { ReciprocityModel.Schedule.Point(it.first, it.second) })

    // Ilford power factors from Ilford's reciprocity technical sheet.
    val ILFORD = listOf(
        FilmStock("ilford-panf", "Pan F+ 50", "Ilford", 50, ReciprocityModel.Power(1.33)),
        FilmStock("ilford-fp4", "FP4+ 125", "Ilford", 125, ReciprocityModel.Power(1.26)),
        FilmStock("ilford-hp5", "HP5+ 400", "Ilford", 400, ReciprocityModel.Power(1.31)),
        FilmStock("ilford-delta100", "Delta 100", "Ilford", 100, ReciprocityModel.Power(1.26)),
        FilmStock("ilford-delta400", "Delta 400", "Ilford", 400, ReciprocityModel.Power(1.41)),
        FilmStock("ilford-delta3200", "Delta 3200", "Ilford", 3200, ReciprocityModel.Power(1.33)),
        FilmStock("ilford-xp2", "XP2 Super 400", "Ilford", 400, ReciprocityModel.Power(1.31)),
    )

    // Kodak B&W: datasheet adjustment tables encoded as (metered s -> corrected s).
    val KODAK_BW = listOf(
        FilmStock(
            "kodak-trix", "Tri-X 400", "Kodak", 400,
            schedule(0.1 to 0.1, 1.0 to 2.0, 10.0 to 50.0, 100.0 to 1200.0),
            note = "Kodak also suggests reduced development for long exposures.",
        ),
        FilmStock(
            "kodak-tmax100", "T-Max 100", "Kodak", 100,
            schedule(1.0 to 1.0, 10.0 to 15.0, 100.0 to 200.0),
        ),
        FilmStock(
            "kodak-tmax400", "T-Max 400", "Kodak", 400,
            schedule(1.0 to 1.0, 10.0 to 15.0, 100.0 to 300.0),
        ),
    )

    // Kodak color negative.
    private const val KODAK_COLOR_NOTE = "Kodak does not characterize exposures beyond 10s; results may shift."
    val KODAK_COLOR = listOf(
        FilmStock("kodak-portra160", "Portra 160", "Kodak", 160, schedule(1.0 to 1.0, 10.0 to 13.0), KODAK_COLOR_NOTE),
        FilmStock("kodak-portra400", "Portra 400", "Kodak", 400, schedule(1.0 to 1.0, 10.0 to 13.0), KODAK_COLOR_NOTE),
        FilmStock("kodak-portra800", "Portra 800", "Kodak", 800, schedule(1.0 to 1.0, 10.0 to 13.0), KODAK_COLOR_NOTE),
        FilmStock("kodak-ektar", "Ektar 100", "Kodak", 100, schedule(1.0 to 1.0, 10.0 to 13.0), KODAK_COLOR_NOTE),
        FilmStock("kodak-gold200", "Gold 200", "Kodak", 200, schedule(0.5 to 0.5, 1.0 to 2.0, 10.0 to 40.0), "Consumer film; long-exposure color shift likely."),
    )

    // Fujifilm slide film.
    val FUJI = listOf(
        FilmStock(
            "fuji-velvia50", "Velvia 50", "Fujifilm", 50,
            schedule(1.0 to 1.0, 4.0 to 5.0, 8.0 to 13.0),
            note = "Fujifilm advises against exposures beyond ~1 min (color shift).",
        ),
        FilmStock(
            "fuji-velvia100", "Velvia 100", "Fujifilm", 100,
            schedule(64.0 to 64.0, 120.0 to 180.0),
            note = "No correction needed up to ~1 min.",
        ),
        FilmStock(
            "fuji-provia100f", "Provia 100F", "Fujifilm", 100,
            ReciprocityModel.None,
            note = "No correction needed up to 128s per Fujifilm.",
        ),
    )

    val ALL: List<FilmStock> = listOf(NONE) + ILFORD + KODAK_BW + KODAK_COLOR + FUJI

    fun byId(id: String?): FilmStock = ALL.firstOrNull { it.id == id } ?: NONE
}
