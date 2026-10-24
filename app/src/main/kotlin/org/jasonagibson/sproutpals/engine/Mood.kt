package org.jasonagibson.sproutpals.engine

/** What the pal's little thought bubble shows. */
enum class Mood { HAPPY, LOVE, HUNGRY, ALARM, SWEAT, SPARKLE, NONE }

object Moods {
    /** The most important feeling right now. [company] = friends visiting or a buddy along. */
    fun of(pet: PetState, weather: WeatherKind?, company: Boolean): Mood = when {
        pet.ended != null || pet.stage == Stage.EGG || pet.sleeping -> Mood.NONE
        pet.poops > 0 && PetEngine.smelly(pet) -> Mood.ALARM
        pet.sick -> Mood.SWEAT
        pet.hunger == 0 -> Mood.HUNGRY
        pet.happy == 0 -> Mood.ALARM
        weather == WeatherKind.HEAT -> Mood.SWEAT
        weather == WeatherKind.STORM -> Mood.ALARM
        company -> Mood.LOVE
        weather == WeatherKind.SNOW || weather == WeatherKind.RAINBOW -> Mood.SPARKLE
        pet.happy >= 3 && pet.hunger >= 2 -> Mood.HAPPY
        pet.species == Species.KONA && pet.happy >= 1 -> Mood.HAPPY     // Kona sings even on so-so days
        else -> Mood.NONE
    }
}
