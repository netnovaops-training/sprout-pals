package org.jasonagibson.sproutpals.engine

/**
 * The shop: garden visitors bought with points (from steps, care and games). Once bought they
 * live in the garden for good - every pet, every life - and each can be shown or hidden.
 * The "???" item has no visible effect: it quietly raises the rare-Jason odds (see PetEngine).
 */
data class ShopItem(val id: String, val name: String, val price: Int, val blurb: String)

enum class ShopResult { BOUGHT, SHOWN, HIDDEN, TOO_POOR }

object Shop {
    /** Listed from the top, each one costing more than the one before (Jason, 10-23). */
    val ITEMS = listOf(
        ShopItem("ladybug", "LADYBUG", 5, "Crawls along\nthe grass"),
        ShopItem("butterfly", "BUTTERFLY", 8, "Flutters around\nyour pal"),
        ShopItem("bee", "BUMBLEBEE", 10, "Buzzes from\nflower to flower"),
        ShopItem("fireflies", "FIREFLIES", 12, "Glow at dusk\nand at night"),
        ShopItem("bluebird", "BLUEBIRD", 15, "Hops and pecks\non the fence"),
        ShopItem("bunny", "BUNNY", 20, "Hops across\nthe lawn"),
        ShopItem("squirrel", "SQUIRREL", 25, "Scampers along\nthe fence"),
        ShopItem("hummingbird", "HUMMINGBIRD", 30, "Hovers beside\nyour pal"),
        ShopItem("deer", "DEER", 40, "Grazes at the\nback of the garden"),
        ShopItem(Rules.MYSTERY_ITEM, "???", 50, "????"),
    )

    fun owns(save: Save, id: String) = id in save.unlocked
    fun showing(save: Save, id: String) = owns(save, id) && id !in save.hidden

    /** B on a shop item: buy it if you can afford it; if you already own it, show or hide it. */
    fun press(save: Save, item: ShopItem): Pair<Save, ShopResult> = when {
        owns(save, item.id) ->
            if (item.id in save.hidden) save.copy(hidden = save.hidden - item.id) to ShopResult.SHOWN
            else save.copy(hidden = save.hidden + item.id) to ShopResult.HIDDEN
        save.points < item.price -> save to ShopResult.TOO_POOR
        else -> save.copy(points = save.points - item.price, unlocked = save.unlocked + item.id) to ShopResult.BOUGHT
    }
}
