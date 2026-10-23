package org.jasonagibson.sproutpals.engine

/**
 * What's worth a phone notification right now, and which ones are new. Each alert has a key;
 * it's sent once, and only sent again after it has cleared (e.g. sick -> cured -> sick again).
 * Pure Kotlin so it can be tested; the Android side (Reminders) just posts the text.
 */
data class Alert(val key: String, val text: String)

object Alerts {
    /** Everything that needs the player right now. */
    fun current(save: Save, now: Long, birthdayEve: Boolean): List<Alert> {
        val pet = save.pet
        val name = pet.species.title
        val out = mutableListOf<Alert>()
        if (pet.ended != null) return listOf(Alert("ended:${pet.bornAt}", "$name has returned to the garden."))
        if (pet.stage == Stage.EGG) return listOf(Alert("egg:${pet.bornAt}", "An egg is waiting! Shake your phone to open it."))
        if (pet.sleeping && pet.lightsOn) out += Alert("lights", "$name is trying to sleep. Lights off?")
        if (pet.sick) out += Alert("sick", "$name is sick. Medicine time!")
        if (!pet.sleeping && pet.hunger == 0) out += Alert("hungry", "$name is hungry!")
        if (!pet.sleeping && pet.poops > 0) out += Alert("poop", "$name made a mess. Time to clean.")
        if (!pet.sleeping && pet.happy == 0) out += Alert("lonely", "$name is lonely. Come play!")
        if (birthdayEve) out += Alert("birthday:${pet.stage}", "🎉 $name grows up tomorrow! Party hat time.")
        val plots = Garden.plots(save)
        if (plots.any { Garden.state(it, now) == PlotState.RIPE }) out += Alert("ripe", "🍓 Something's ripe in the garden. Harvest time!")
        if (!pet.sleeping && plots.any { Garden.state(it, now) == PlotState.DRY }) out += Alert("dry", "💧 The garden is thirsty. Crops stop growing until watered.")
        Weather.now(save, now)?.let { k -> out += Alert("weather:${save.weather?.start}", "${k.icon} ${k.title.lowercase().replaceFirstChar { it.uppercase() }}: ${k.line}.") }
        return out
    }

    /** The alerts to send now, and the new list of keys that have been sent. */
    fun fresh(save: Save, now: Long, birthdayEve: Boolean): Pair<List<Alert>, List<String>> {
        val cur = current(save, now, birthdayEve)
        val new = cur.filter { it.key !in save.notified }
        return new to cur.map { it.key }   // keys that cleared drop out, so they can fire again later
    }
}
