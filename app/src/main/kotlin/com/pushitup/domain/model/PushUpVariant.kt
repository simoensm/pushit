package com.pushitup.domain.model

/**
 * Push-up variant the user selects before a session.
 * The [starMultiplier] is applied to all stars earned during that session.
 * The detection model is not affected — only scoring changes.
 */
enum class PushUpVariant(
    val labelFr         : String,
    val description     : String,
    val starMultiplier  : Float,
    val difficultyStars : Int,          // 1–5, shown as filled dots in the picker
) {
    NORMAL(
        labelFr        = "Normales",
        description    = "Position classique",
        starMultiplier = 1.0f,
        difficultyStars = 1,
    ),
    MILITAIRE(
        labelFr        = "Militaires",
        description    = "Mains sous les épaules, coudes serrés",
        starMultiplier = 1.2f,
        difficultyStars = 2,
    ),
    ECARTEEES(
        labelFr        = "Écartées",
        description    = "Mains très larges, pectoraux dominants",
        starMultiplier = 1.2f,
        difficultyStars = 2,
    ),
    DIAMS(
        labelFr        = "Diamant",
        description    = "Mains en triangle sous la poitrine",
        starMultiplier = 1.5f,
        difficultyStars = 4,
    ),
    PIQUEES(
        labelFr        = "Piquées",
        description    = "Bassin plus élevé, épaules dominantes",
        starMultiplier = 1.4f,
        difficultyStars = 3,
    ),
    UNE_MAIN(
        labelFr        = "Une main",
        description    = "Défi ultime — force et équilibre",
        starMultiplier = 2.0f,
        difficultyStars = 5,
    ),
}
