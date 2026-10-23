package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.app.Effect
import org.jasonagibson.sproutpals.app.Game
import org.jasonagibson.sproutpals.app.Move
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.art.Grid
import org.jasonagibson.sproutpals.app.Panel
import org.jasonagibson.sproutpals.engine.Kind
import org.jasonagibson.sproutpals.engine.Weather
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.art.Icons
import org.jasonagibson.sproutpals.art.Sprites
import org.jasonagibson.sproutpals.art.x2
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Stage
import java.time.Instant
import java.time.ZoneId

/*
 * The screen: a 96 x 104 grid of square pixels (SNES-ish detail, still chunky).
 *   rows 0-19     top icon bar (4 icons)
 *   rows 20-83    the garden, where the pet lives
 *   rows 84-103   bottom icon bar (3 icons + the attention light)
 */
const val LCD_W = 96
const val LCD_H = 104
private const val AREA_TOP = 20
private const val AREA_H = 64
private const val GROUND = AREA_TOP + 56
/** Where the pet stands when petX = 0 (sprites are 32 wide). */
private const val PET_X0 = 32
/**
 * How tall the pet stands on screen, by age (screen units; the garden is 64 tall).
 * Cats and dogs are a bit lower to the ground than people, as in the reference picture.
 * Sizing by target height keeps a baby cat big enough to see.
 */
/** Three wavy green stink lines rising from (x, y) and fading as they go. */
private fun DrawScope.stink(x: Int, y: Int, f: Int, px: Float) {
    val wave = listOf(0, 1, 1, 0, -1, -1)
    for (line in 0 until 3) {
        val lx = x + line * 5
        val rise = (f + line * 4) % 12
        for (seg in 0 until 6) {
            val sy = y - rise - seg
            if (sy < AREA_TOP) continue
            val alpha = (0xCC - seg * 0x1C).coerceAtLeast(0x30).toLong()
            block(lx + wave[(seg + f / 2 + line) % wave.size], sy, 1, 1, (alpha shl 24) or 0x7FBF3F, px)
        }
    }
}

private fun poseFor(game: Game, lift: Int): Int {
    val pet = game.pet
    val k = pet.species.kind
    val f = game.frame
    val P = CharacterArt.Pose
    val fullOfCare = pet.hunger == Rules.MAX_HEARTS && pet.happy == Rules.MAX_HEARTS && pet.poops == 0 && !pet.sick
    return when {
        pet.sleeping -> P.STAND
        pet.sick && game.effect != Effect.PILL -> P.SICK
        game.effect == Effect.HAPPY -> if (lift >= 4) P.JUMP else P.cheer(k)
        game.effect == Effect.NO -> if (k == Kind.CAT) 3 else P.STAND
        game.effect != Effect.NONE -> P.STAND                     // eating, medicine, cleaning
        game.walkMode -> P.walk(k, f)
        game.move == Move.HOP -> if (lift >= 4) P.JUMP else if (lift == 0) P.LAND else P.walk(k, f)
        game.move == Move.RUN && game.moving -> P.run(k, f)
        game.moving -> P.walk(k, f)
        game.move == Move.DANCE || game.move == Move.BALL -> P.wave(k, f)
        game.move == Move.GARDEN -> if (k == Kind.HUMAN) (if ((f / 4) % 2 == 0) 1 else 0) else P.STAND
        // all hearts full, nothing to clean: a happy clapping fit every few seconds
        fullOfCare && (f / 16) % 3 == 0 -> P.clap(k, f)
        // standing about: every few seconds a little wave (people) or a meow (cats)
        (f / 8) % 6 == 5 -> P.wave(k, f)
        else -> P.STAND
    }
}

private fun petScale(species: Species, stage: Stage): Float {
    val target = when (stage) { Stage.BABY -> 26f; Stage.KID -> 36f; else -> 46f } *
        if (species.kind == Kind.HUMAN) 1f else 0.82f
    return target / CharacterArt.of(species, stage).height
}

private val BAR = Color(0xFFE6E8CF)
private val BAR_INK = Color(0xFF2E3A2A)

/** Draws one text-art grid. [x],[y] are in screen pixels; [flip] mirrors it left-right. */
fun DrawScope.grid(g: Grid, pal: Map<Char, Long>, x: Int, y: Int, px: Float, flip: Boolean = false) {
    val w = g.maxOf { it.length }
    g.forEachIndexed { r, row ->
        row.forEachIndexed { c, ch ->
            val argb = pal[ch] ?: return@forEachIndexed
            val col = if (flip) w - 1 - c else c
            // +0.6 overlap hides the hairline gaps between neighbouring squares.
            drawRect(Color(argb), Offset((x + col) * px, (y + r) * px), Size(px + 0.6f, px + 0.6f))
        }
    }
}

internal fun DrawScope.block(x: Int, y: Int, w: Int, h: Int, c: Color, px: Float) =
    drawRect(c, Offset(x * px, y * px), Size(w * px + 0.6f, h * px + 0.6f))

internal fun DrawScope.block(x: Int, y: Int, w: Int, h: Int, c: Long, px: Float) = block(x, y, w, h, Color(c), px)

/** Flat color bands for the sky, hills and lawn, picked from the phone clock. */
private enum class Sky(val top: Long, val mid: Long, val low: Long, val hill: Long, val grass: Long, val grassDark: Long) {
    DAWN(0xFFF2A28E, 0xFFF7C4A0, 0xFFFBE0B4, 0xFF8FB873, 0xFF79B85A, 0xFF6AA64E),
    DAY(0xFF6FC0EE, 0xFF93D3F5, 0xFFBDE7F7, 0xFF7FBF5C, 0xFF5BC236, 0xFF4FB02E),
    SUNSET(0xFFE2673F, 0xFFEE8F5E, 0xFFF3B98A, 0xFF5E8F45, 0xFF4E8F3A, 0xFF427D32),
    NIGHT(0xFF10193A, 0xFF1A2A52, 0xFF24345C, 0xFF24482E, 0xFF2C5236, 0xFF24462E),
}

private fun skyAt(now: Long): Sky = when (Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour) {
    in 5..7 -> Sky.DAWN
    in 8..16 -> Sky.DAY
    in 17..19 -> Sky.SUNSET
    else -> Sky.NIGHT
}

fun DrawScope.drawLcd(game: Game) {
    val px = size.width / LCD_W
    val pet = game.pet
    val f = game.frame
    val walking = game.walkMode
    // scrolling scenery while walking with you: far things move slower than near things
    val farScroll = if (walking) f / 4 else 0
    val nearScroll = if (walking) f else 0

    val sky = if (pet.sleeping) Sky.NIGHT else skyAt(game.now())
    val dark = pet.sleeping && !pet.lightsOn
    if (dark) {
        block(0, AREA_TOP, LCD_W, AREA_H, 0xFF05070D, px)
    } else {
        // sky
        block(0, AREA_TOP, LCD_W, 14, sky.top, px)
        block(0, AREA_TOP + 14, LCD_W, 14, sky.mid, px)
        block(0, AREA_TOP + 28, LCD_W, 14, sky.low, px)
        when (sky) {
            Sky.DAY -> {
                block(78, AREA_TOP + 4, 10, 10, 0xFFFFD84A, px)
                block(80, AREA_TOP + 2, 6, 14, 0xFFFFD84A, px); block(76, AREA_TOP + 6, 14, 6, 0xFFFFD84A, px)
            }
            Sky.SUNSET, Sky.DAWN -> block(74, AREA_TOP + 24, 14, 8, 0xFFFFE08A, px)
            Sky.NIGHT -> {
                for ((sx, sy) in listOf(8 to 4, 26 to 10, 44 to 2, 60 to 8, 82 to 4, 16 to 18, 70 to 16, 36 to 22))
                    if ((f / 4 + sx) % 5 != 0) block(sx, AREA_TOP + sy, 2, 2, 0xFFFFF3B0, px)
                block(78, AREA_TOP + 4, 8, 8, 0xFFF2F0DC, px); block(76, AREA_TOP + 6, 12, 4, 0xFFF2F0DC, px)
            }
        }
        // clouds drift on their own, faster when walking
        val drift = f / 12 + farScroll
        for ((cx, cy, cw) in listOf(Triple(10, 6, 18), Triple(52, 12, 14), Triple(86, 3, 12))) {
            val x = Math.floorMod(cx - drift, LCD_W + cw) - cw
            block(x, AREA_TOP + cy, cw, 3, 0xFFFAFCFF, px); block(x + 3, AREA_TOP + cy - 2, cw - 6, 2, 0xFFFAFCFF, px)
        }
        // rolling hills
        for (i in 0 until 5) {
            val hx = Math.floorMod(i * 28 - farScroll, LCD_W + 28) - 14
            block(hx, AREA_TOP + 34, 28, 8, sky.hill, px); block(hx + 6, AREA_TOP + 31, 16, 3, sky.hill, px)
        }
        // lawn with mowing stripes, then the dirt edge
        block(0, AREA_TOP + 42, LCD_W, AREA_H - 42, sky.grass, px)
        for (y in AREA_TOP + 44 until AREA_TOP + AREA_H step 8) block(0, y, LCD_W, 2, sky.grassDark, px)
        block(0, GROUND + 4, LCD_W, 4, 0xFF7A4A24, px)
        // fence posts and bushes behind the lawn
        for (i in 0 until 5) {
            val fx = Math.floorMod(i * 22 + 4 - nearScroll / 3, LCD_W + 8) - 4
            block(fx, AREA_TOP + 38, 3, 10, 0xFFA8743F, px); block(fx - 4, AREA_TOP + 41, 11, 2, 0xFF8A5A2B, px)
        }
        for (i in 0 until 3) {
            val bx = Math.floorMod(i * 40 + 12 - nearScroll / 2, LCD_W + 16) - 8
            block(bx, AREA_TOP + 40, 16, 6, 0xFF2F8A3A, px); block(bx + 3, AREA_TOP + 37, 10, 3, 0xFF3FA04A, px)
        }
        // flowers and grass tufts in front
        val sway = if (f % 16 < 8) 0 else 1
        val flowerColors = listOf(0xFFE0344BL, 0xFFFFD84AL, 0xFF8E6FE0L, 0xFF3C7DD9L, 0xFFF2A3C0L)
        // six flowers always, plus the ones the bees have grown
        for (i in 0 until 6 + game.save.flowers) {
            val fx = Math.floorMod(i * 17 + 3 + (i / 6) * 8 - nearScroll, LCD_W + 6) - 3
            val fy = GROUND - 2 + (i % 3) * 2
            block(fx + 1, fy, 1, 3, 0xFF2F8A3A, px)
            block(fx + sway, fy - 2, 3, 2, flowerColors[i % flowerColors.size], px)
        }
        for (i in 0 until 8) {
            val gx = Math.floorMod(i * 13 + 8 - nearScroll, LCD_W + 4) - 2
            block(gx, GROUND + 1, 1, 2, sky.grassDark, px); block(gx + 2, GROUND, 1, 3, sky.grassDark, px)
        }
    }

    // ----- a mini-game takes over the garden while it runs -----
    val playing = game.mini
    // decor, then the ground and fence visitors, all behind the pet (not while walking: the scene scrolls)
    if (playing == null && !dark && !walking) drawDecor(game.save, f, GROUND, px, night = sky == Sky.NIGHT || sky == Sky.SUNSET)
    if (playing == null && !dark) drawBackCritters(game.save, f, AREA_TOP, GROUND, px)
    if (playing != null) drawMiniGame(playing, pet, f, AREA_TOP, GROUND, px)

    // ----- the garden bed: three plots of real crops (see GardenDraw) -----
    if (!dark && !walking && playing == null) {
        drawGardenBed(game.save, game.now(), f, GROUND, px, (game.panel as? Panel.Garden)?.plot)
    }

    // ----- the pet (or the sprout it left behind) -----
    if (playing != null) {
        Unit   // the game drew the pet
    } else if (pet.ended != null) {
        grid(Sprites.SPROUT.x2(), Sprites.SPROUT_PALETTE, 40, GROUND - 13, px)
    } else if (!dark) {
        var x = PET_X0 + game.petX
        var lift = 0
        // the character's box on screen; the egg is a 32x19 grid, characters are pictures
        val box: CharBox
        if (pet.stage == Stage.EGG) {
            // Idle: rock a few times, rest, rock again. Walking or shaking: it can't sit still.
            x = PET_X0
            val e = f / 2
            when {
                game.effect == Effect.SHAKE -> x += if (f % 2 == 0) -4 else 4
                game.walkingNow -> { x += listOf(-2, -1, 0, 1, 2, 1, 0, -1)[f % 8]; if (f % 8 < 2) lift = 2 }
                e % 16 < 6 -> x += listOf(0, -1, -2, -1, 0, 1, 2, 1, 0, -1, -2, -1)[f % 12]
            }
            val egg = Sprites.EGG
            val y = GROUND - egg.size + 1 - lift
            shaded(egg, Sprites.EGG_PALETTE, x, y, px)
            // a zig-zag crack grows with each shake
            val crack = listOf(12 to 7, 13 to 8, 14 to 9, 15 to 8, 16 to 9, 17 to 10, 18 to 9, 19 to 10, 20 to 11)
            crack.take(pet.cracks * 3).forEach { (cx, cy) -> block(x + cx, y + cy, 1, 1, 0xFF3A2A1E, px) }
            box = CharBox(x.toFloat(), y.toFloat(), 32f, egg.size.toFloat())
        } else {
            when {
                game.effect == Effect.HAPPY -> lift = listOf(0, 3, 6, 7, 6, 3, 0, 0)[f % 8]
                game.effect == Effect.NO -> x += listOf(-2, -1, 0, 1, 2, 1, 0, -1)[f % 8]
                pet.sleeping -> Unit
                else -> lift = game.petLift
            }
            // walking: a little side-to-side sway on alternate steps stands in for leg frames
            val sway = if (walking) listOf(0f, 0.5f, 0f, -0.5f)[(f / 2) % 4] else 0f
            box = character(
                pet.species, pet.stage,
                centerX = x + 16f + sway, ground = (GROUND + 1 - lift).toFloat(),
                unitPx = px, unitsPerPixel = petScale(pet.species, pet.stage), faceLeft = game.facingLeft,
                pose = poseFor(game, lift),
                layers = { img -> if (pet.species.kind == Kind.HUMAN) clothesLayers(img, pet.stage, game.save) else emptyList() },
            )
        }
        val bx = box.left.toInt()
        val by = box.top.toInt()
        val bw = box.width.toInt()

        // gardening: a trowel in hand, digging in time with the bobbing
        if (game.move == Move.GARDEN && game.petX == game.gardenTo) {
            grid(Sprites.TROWEL.x2(), Sprites.TROWEL_PALETTE, bx + bw - 2, GROUND - 10 + listOf(0, 0, 1, 3, 3, 2, 1, 0)[f % 8], px)
            if (f % 8 == 4) block(BED_X + 2 + (f / 4) % 8, GROUND - 4, 2, 1, 0xFF5C3A1A, px)
        }
        // playing ball: it bounces between the pet and the left edge
        if (game.move == Move.BALL) {
            val t = f % 32
            val bx = if (t < 16) 6 + t * 2 else 6 + (32 - t) * 2
            val by = GROUND - 4 - listOf(0, 4, 7, 9, 11, 12, 12, 11, 9, 7, 4, 0, 0, 2, 3, 2)[t % 16]
            block(bx, by, 4, 4, 0xFFE0344B, px); block(bx + 1, by + 1, 2, 2, 0xFFFF8A9A, px)
        }

        // shop visitors that fly: in front of the pet, around its head
        drawFrontCritters(game.save, f, AREA_TOP, GROUND, px, box.headX.toInt(), box.headTop.toInt(), night = sky == Sky.NIGHT || sky == Sky.SUNSET)

        // the worn hat sits on the head (the birthday hat takes its place on birthday eve)
        if (!(game.birthdaySoon && !pet.sleeping) && pet.stage != Stage.EGG) drawHat(game.save, box, px)

        if (game.birthdaySoon && !pet.sleeping) {
            // party hat on top of the head, and a little confetti
            grid(Sprites.PARTY_HAT.x2(), Sprites.PARTY_HAT_PALETTE, box.headX.toInt() - 7, box.headTop.toInt() - 10, px)
            val colors = listOf(0xFFE0344BL, 0xFFFFD84AL, 0xFF3C7DD9L, 0xFF5BC236L, 0xFFF2A3C0L)
            for (i in 0 until 8) {
                val cx = (i * 23 + 5) % LCD_W
                val cy = AREA_TOP + Math.floorMod(f + i * 9, 50)
                block(cx, cy, 2, 2, colors[i % colors.size], px)
            }
        }
        if (walking && f % 8 < 4) block(bx - 2, GROUND - 1, 4, 2, 0xAAE6D3B0, px)   // dust behind the feet
        // smelly: wavy green stink lines drifting up off the pet
        if (PetEngine.smelly(pet) && !pet.sleeping) stink(bx + bw / 2 - 6, by + 4, f, px)

        if (pet.species.rare && pet.stage != Stage.EGG && f % 8 < 4) grid(Sprites.STAR.x2(), Sprites.STAR_PALETTE, bx + bw, by - 2, px)
        if (pet.sick && !pet.sleeping) grid(Sprites.SKULL.x2(), Sprites.SKULL_PALETTE, bx - 12, by, px)
        if (pet.sleeping) {
            val zy = (by - 4 - ((f / 2) % 3) * 2).coerceAtLeast(AREA_TOP + 2)
            block(bx + bw, zy, 6, 2, Color.White, px); block(bx + bw + 2, zy + 2, 2, 2, Color.White, px); block(bx + bw, zy + 4, 6, 2, Color.White, px)
        }

        // food / pill appear next to the pet, shrinking as it eats
        val side = if (game.facingLeft) bx - 18 else bx + bw + 2
        when (game.effect) {
            Effect.MEAL -> grid(Sprites.MEAL.drop((f % 12) / 4).x2(), Sprites.MEAL_PALETTE, side, GROUND - 9, px)
            Effect.SNACK -> grid(Sprites.SNACK.drop((f % 12) / 4).x2(), Sprites.SNACK_PALETTE, side, GROUND - 9, px)
            Effect.PILL -> grid(Sprites.PILL.x2(), Sprites.PILL_PALETTE, side, GROUND - 12, px)
            else -> Unit
        }
    }

    // poop piles along the right edge; the clean effect sweeps a wave across them
    if (!dark) {
        repeat(pet.poops) { i -> grid(Sprites.POOP.x2(), Sprites.POOP_PALETTE, 78 - (i % 2) * 16, GROUND - 9 - (i / 2) * 12, px) }
        if (PetEngine.smelly(pet)) stink(78, GROUND - 12 - ((pet.poops - 1) / 2) * 12, f + 3, px)
        if (game.effect == Effect.CLEAN) {
            val wx = (f % 16) * 6
            block(wx, AREA_TOP + 4, 4, AREA_H - 8, 0xCCFFFFFF, px)
        }
    }

    // ----- weather over the whole garden -----
    if (!dark && game.mini == null) Weather.now(game.save, game.now())?.let { drawWeather(it, f, AREA_TOP, AREA_H, px) }

    // ----- icon bars -----
    block(0, 0, LCD_W, AREA_TOP, BAR, px)
    block(0, AREA_TOP + AREA_H, LCD_W, LCD_H - AREA_TOP - AREA_H, BAR, px)
    val attention = PetEngine.needsAttention(pet)
    // thin dark rules between the bars and the garden
    block(0, AREA_TOP - 1, LCD_W, 1, BAR_INK, px)
    block(0, AREA_TOP + AREA_H, LCD_W, 1, BAR_INK, px)
    Icons.ALL.forEachIndexed { i, icon ->
        val ix = 4 + (i % 4) * 24
        val iy = if (i < 4) 1 else AREA_TOP + AREA_H + 2
        val isAttention = i == 7
        if (i == game.selected) {
            // selected: a bright yellow tile with a dark frame
            block(ix - 2, iy - 1, 20, 18, BAR_INK, px)
            block(ix - 1, iy, 18, 16, 0xFFFFD84A, px)
        }
        when {
            !isAttention -> shaded(icon, Icons.PALETTE, ix, iy, px)
            attention && (f / 4) % 2 == 0 -> shaded(icon, Icons.PALETTE, ix, iy, px)
            else -> grid(icon, Icons.PALETTE.mapValues { 0x40000000L }, ix, iy, px)   // faint when all's well
        }
    }
}
