package org.jasonagibson.sproutpals.engine

/**
 * The shop, in two pages: CREATURES (garden visitors) and OUTFITS (worn by your pal).
 * Bought with points from steps, care and games; once bought, they're yours for every pet and
 * every life. Creatures: every one you own is out at once, each with its own on/off box.
 * Outfits: one hat, one shirt and one pair of pants at a time. Prices climb down each page.
 */
enum class ShopTab(val title: String) { CREATURES("CREATURES"), OUTFITS("OUTFITS"), DECOR("DECOR") }

/** Where an outfit goes. Shirts and pants are worn by people only. */
enum class Slot { HAT, SHIRT, PANTS }

data class ShopItem(
    val id: String,
    val name: String,
    val price: Int,
    val blurb: String,
    val tab: ShopTab,
    val slot: Slot? = null,
    /** Can be bought more than once. */
    val repeatable: Boolean = false,
)

enum class ShopResult { BOUGHT, SHOWN, HIDDEN, TOO_POOR, MAXED }

object Shop {
    private fun creature(id: String, name: String, price: Int, blurb: String) = ShopItem(id, name, price, blurb, ShopTab.CREATURES)
    private fun outfit(id: String, name: String, price: Int, blurb: String, slot: Slot) = ShopItem(id, name, price, blurb, ShopTab.OUTFITS, slot)

    val ITEMS = listOf(
        // each costs more than the last, in step with how much it does for you (Jason, 10-23)
        creature("ladybug", "LADYBUG", 10, "Crawls along\nthe grass"),
        creature("butterfly", "BUTTERFLY", 20, "Flutters around\nyour pal"),
        creature("bee", "BUMBLEBEE", 35, "Buzzes from\nflower to flower"),
        creature("fireflies", "FIREFLIES", 50, "Glow at dusk\nand at night"),
        creature("bluebird", "BLUEBIRD", 75, "Hops and pecks\non the fence"),
        creature("bunny", "BUNNY", 100, "Hops across\nthe lawn"),
        creature("squirrel", "SQUIRREL", 140, "Scampers along\nthe fence"),
        creature("hummingbird", "HUMMINGBIRD", 190, "Hovers beside\nyour pal"),
        creature("deer", "DEER", 250, "Grazes at the\nback of the garden"),
        ShopItem(Rules.MYSTERY_ITEM, "???", 300, "????", ShopTab.CREATURES, repeatable = true),

        outfit("flower_crown", "FLOWER CROWN", 10, "Fresh from\nthe garden", Slot.HAT),
        outfit("green_tee", "GREEN TEE", 12, "Garden green\n(people only)", Slot.SHIRT),
        outfit("bow", "BIG BOW", 15, "A bright\npink bow", Slot.HAT),
        outfit("jeans", "BLUE JEANS", 18, "Good for digging\n(people only)", Slot.PANTS),
        outfit("beanie", "BEANIE", 20, "Cozy for\ncold mornings", Slot.HAT),
        outfit("flannel", "RED FLANNEL", 22, "Checked and cozy\n(people only)", Slot.SHIRT),
        outfit("sun_hat", "SUN HAT", 25, "For long days\nin the sun", Slot.HAT),
        outfit("raincoat", "RAINCOAT", 28, "Puddle-proof\n(people only)", Slot.SHIRT),
        outfit("cowboy_hat", "COWBOY HAT", 30, "Yee-haw!", Slot.HAT),
        outfit("work_pants", "WORK PANTS", 32, "Tough and brown\n(people only)", Slot.PANTS),
        outfit("sweater", "STRIPED SWEATER", 35, "Stripes!\n(people only)", Slot.SHIRT),
        outfit("crown", "GOLD CROWN", 45, "Garden royalty", Slot.HAT),

        decor("flower_pots", "FLOWER POTS", 15, "Bright pots\nby the path"),
        decor("gnome", "GARDEN GNOME", 30, "A lucky little\nfellow"),
        decor("birdbath", "BIRDBATH", 45, "Birds love\na splash"),
        decor("rain_barrel", "RAIN BARREL", 60, "Saves the rain\nfor the beds"),
        decor("lantern", "LANTERN", 80, "A warm glow\nat night"),
        decor("bench", "BENCH", 100, "A cozy spot\nto sit"),
        decor("pond", "POND", 130, "Cool, calm\nwater"),
        decor("well", "WISHING WELL", 170, "Make a wish..."),
        decor("scarecrow", "SCARECROW", 220, "Keeps an eye\non the crops"),
    )

    private fun decor(id: String, name: String, price: Int, blurb: String) = ShopItem(id, name, price, blurb, ShopTab.DECOR)

    /** How many times the repeatable item can be bought; after that it leaves the shop. */
    const val MAX_REPEAT = 3

    /** What's on a page right now: a repeatable item that has been bought out is gone. */
    fun items(tab: ShopTab, save: Save) = ITEMS.filter { it.tab == tab && !(it.repeatable && save.mysteryCount >= MAX_REPEAT) }

    fun byId(id: String) = ITEMS.firstOrNull { it.id == id }
    fun owns(save: Save, id: String) = id in save.unlocked

    /** Out in the garden (creatures) or being worn (outfits). */
    fun showing(save: Save, id: String) = owns(save, id) &&
        if (byId(id)?.slot != null) id in save.wearing else id !in save.hidden

    /** The worn item for a slot, if any. */
    fun worn(save: Save, slot: Slot) = save.wearing.firstOrNull { byId(it)?.slot == slot }

    /** How long a creature stays out before it goes home and needs switching on again. */
    const val OUT_HOURS = 24

    /** B on an item: buy it (it comes straight out), or switch one you own on or off. */
    fun press(save: Save, item: ShopItem, now: Long = 0L): Pair<Save, ShopResult> {
        if (item.repeatable) {
            if (save.mysteryCount >= MAX_REPEAT) return save to ShopResult.MAXED
            if (save.points < item.price) return save to ShopResult.TOO_POOR
            return save.copy(points = save.points - item.price, mysteryCount = save.mysteryCount + 1) to ShopResult.BOUGHT
        }
        if (owns(save, item.id)) {
            return if (showing(save, item.id)) setOn(save, item, false, now) to ShopResult.HIDDEN
            else setOn(save, item, true, now) to ShopResult.SHOWN
        }
        if (save.points < item.price) return save to ShopResult.TOO_POOR
        val bought = save.copy(points = save.points - item.price, unlocked = save.unlocked + item.id)
        return setOn(bought, item, true, now) to ShopResult.BOUGHT
    }

    private fun setOn(save: Save, item: ShopItem, on: Boolean, now: Long): Save {
        val slot = item.slot ?: return if (on) save.copy(hidden = save.hidden - item.id, outUntil = save.outUntil + (item.id to now + OUT_HOURS * 3_600_000L))
            else save.copy(hidden = save.hidden + item.id, outUntil = save.outUntil - item.id)
        val others = save.wearing.filter { byId(it)?.slot != slot }
        return save.copy(wearing = if (on) others + item.id else others)
    }

    fun rarePercent(save: Save) = (Rules.RARE_PERCENT + Rules.RARE_STEP * save.mysteryCount).coerceAtMost(Rules.RARE_PERCENT_MAX)
}
