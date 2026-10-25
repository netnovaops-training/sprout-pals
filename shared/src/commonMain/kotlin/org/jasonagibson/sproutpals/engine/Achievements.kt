package org.jasonagibson.sproutpals.engine

/**
 * Achievements. Things you do have three tiers - once, 5 times, 10 times (bronze, silver, gold);
 * every shop item you can own has a one-tier "got it" achievement. Each tier pays stars.
 * Counting lives in the save ([Save.counts]), so progress carries across every pet and life.
 */
/** [secret]: listed as "???" until the first tier is earned; then [name] and [reveal] show. */
data class Feat(
    val key: String, val name: String, val icon: String, val tiers: List<Int> = TIERS,
    val secret: Boolean = false, val reveal: String = "",
) {
    companion object { val TIERS = listOf(1, 5, 10) }
}

/** One tier reached: what to celebrate. [tier] 0 = bronze, 1 = silver, 2 = gold. */
data class Unlock(val feat: Feat, val tier: Int, val stars: Int) {
    val medal get() = if (feat.tiers.size == 1) "★" else listOf("🥉", "🥈", "🥇")[tier]
    val id get() = "${feat.key}#$tier"
}

object Achievements {
    /** Stars for bronze / silver / gold, and for owning a shop item. */
    val TIER_STARS = listOf(2, 5, 10)
    const val ITEM_STARS = 3

    private val DOING = listOf(
        Feat("hatch", "HATCH AN EGG", "🥚"),
        Feat("meal", "SERVE A MEAL", "🍲"),
        Feat("snack", "GIVE A SNACK", "🍪"),
        Feat("harvest_meal", "COOK FROM THE GARDEN", "🥕"),
        Feat("clean", "CLEAN UP", "🧽"),
        Feat("cure", "NURSE BACK TO HEALTH", "💊"),
        Feat("lights_off", "LIGHTS OUT AT BEDTIME", "💡"),
        Feat("play", "PLAY A GAME", "🎮"),
        Feat("game_fruit", "FRUIT CATCH", "🍎"),
        Feat("game_rope", "JUMP ROPE", "🪢"),
        Feat("game_ttt", "TIC-TAC-TOE", "⭕"),
        Feat("game_hilo", "HIGHER / LOWER", "🔢"),
        Feat("game_harvest", "PLANT & HARVEST", "🌽"),
        Feat("game_21", "21", "🃏"),
        Feat("game_slots", "SLOTS", "🎰"),
        Feat("game_crows", "SCARE THE CROWS", "🐦"),
        Feat("plant", "PLANT A SEED", "🌱"),
        Feat("water", "WATER THE GARDEN", "💧"),
        Feat("harvest", "HARVEST A CROP", "🧺"),
        Feat("buy", "SHOP TILL YOU DROP", "🛍"),
        Feat("visit", "GO VISITING", "🧳"),
        Feat("host", "HAVE A VISITOR", "🏡"),
        Feat("heart", "SEND A HEART", "♥"),
        Feat("friend", "MAKE A FRIEND", "🤝"),
        Feat("stop", "WALK TO A GARDEN SHOP", "👟"),
        Feat("birthday", "HAPPY BIRTHDAY", "🎂"),
        Feat("weather", "WEATHER THE WEATHER", "⛅"),
        Feat("full_life", "A FULL, HAPPY LIFE", "🌈"),
    )

    /** Secrets: "???" in the list until found. */
    private val SECRETS = listOf(
        Feat("secret_buddy", "A BUDDY TAGS ALONG", "✨", secret = true, reveal = "HOLD THE HEARTS 15 SECONDS - ONCE A WEEK"),
        Feat("secret_charm", "THE LUCKY CHARM", "🍀", listOf(1), secret = true, reveal = "THE ???? ITEM: RARE JASON NOW HATCHES 15% OF THE TIME"),
    )

    /** One per shop item you can own (the last creature-page item stays a secret; one-day items aren't owned). */
    private val OWNING = Shop.ITEMS.filter { !it.repeatable && !it.daily }.map { Feat("own:${it.id}", "GOT THE ${it.name}", "🎁", listOf(1)) }

    val ALL: List<Feat> = DOING + OWNING + SECRETS

    /** Share of all medals earned, 0-100. */
    fun percent(save: Save): Int = progress(save).let { (got, of) -> if (of == 0) 0 else got * 100 / of }

    fun feat(key: String) = ALL.firstOrNull { it.key == key }

    /** Counts [key] up by [by]; returns the new save (stars added) and any tiers just reached. */
    fun record(save: Save, key: String, by: Int = 1): Pair<Save, List<Unlock>> {
        val f = feat(key) ?: return save to emptyList()
        val n = (save.counts[key] ?: 0) + by
        val reached = f.tiers.indices.filter { t -> n >= f.tiers[t] && "$key#$t" !in save.achieved }
        val unlocks = reached.map { t -> Unlock(f, t, if (f.tiers.size == 1) ITEM_STARS else TIER_STARS[t]) }
        val s = save.copy(
            counts = save.counts + (key to n),
            achieved = save.achieved + unlocks.map { it.id },
            points = save.points + unlocks.sumOf { it.stars },
        )
        return s to unlocks
    }

    /** Medals earned so far and the most possible. */
    fun progress(save: Save): Pair<Int, Int> = save.achieved.count { id -> feat(id.substringBefore('#')) != null } to ALL.sumOf { it.tiers.size }

    /** For the trophy list: (feat, count so far, medals earned). */
    fun rows(save: Save) = ALL.map { f -> Triple(f, save.counts[f.key] ?: 0, f.tiers.indices.count { "${f.key}#$it" in save.achieved }) }
}
