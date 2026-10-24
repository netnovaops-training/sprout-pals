package org.jasonagibson.sproutpals.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jasonagibson.sproutpals.app.Chiptune
import org.jasonagibson.sproutpals.app.Game
import org.jasonagibson.sproutpals.app.Panel
import org.jasonagibson.sproutpals.engine.Achievements
import org.jasonagibson.sproutpals.engine.FruitCatch
import org.jasonagibson.sproutpals.engine.Garden
import org.jasonagibson.sproutpals.engine.PlotState
import org.jasonagibson.sproutpals.engine.HigherLower
import org.jasonagibson.sproutpals.engine.JumpRope
import org.jasonagibson.sproutpals.engine.MiniGame
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Shop
import org.jasonagibson.sproutpals.engine.ShopTab
import org.jasonagibson.sproutpals.engine.TicTacToe
import org.jasonagibson.sproutpals.engine.PlantHarvest
import org.jasonagibson.sproutpals.engine.Visitors
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Stage

private val SHELL = Color(0xFF2E5E3A)
private val SHELL_EDGE = Color(0xFF1E4128)
private val CREAM = Color(0xFFF3E9C6)
private val INK = Color(0xFF1E2A1C)

private val lcdText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 20.sp, color = INK, textAlign = TextAlign.Center, lineHeight = 26.sp)

// The cream console frame from Jason's reference picture: pixel hearts and food bowls at the
// corners, chunky red / yellow / blue A-B-C buttons along the bottom.
private val FRAME = Color(0xFFEDE6D3)
private val FRAME_EDGE = Color(0xFF8A8F99)
private val FRAME_INNER = Color(0xFF5E6470)
private val BTN_A = Color(0xFFE5484D)
private val BTN_B = Color(0xFFF5C542)
private val BTN_C = Color(0xFF4A86E8)
private val BTN_SHOP = Color(0xFFF08FB5)
private val rainbowTitle = listOf(0xFFE0344B, 0xFFF2A3C0, 0xFFF2B33D, 0xFF5BC236, 0xFF3C7DD9, 0xFF8E6FE0)

/**
 * The whole app is one "device": a cream console frame around the garden screen.
 * Top: happiness hearts, the food bowl (fills with hunger), the shop and the music note.
 * Bottom: A (next), B (choose), C (back), and the sound switch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PalScreen(
    game: Game,
    debug: Boolean,
    onUpdate: () -> Unit,
    onShareCode: () -> Unit = {},
    onAddFriend: (String) -> Unit = {},
    notificationsOff: Boolean = false,
    onEnableNotifications: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(FRAME).padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // ----- top bar -----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val hearts = if (game.pet.stage == Stage.EGG) 0 else game.pet.happy
            Row(Modifier.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    // still pressed after 15 s: withTimeoutOrNull gives up waiting for the release
                    if (withTimeoutOrNull(15_000) { tryAwaitRelease() } == null) game.heartHeld()
                })
            }) {
                repeat(Rules.MAX_HEARTS) { i ->
                    PixelIcon(if (i < hearts) PixelArt.HEART else PixelArt.HEART_EMPTY, PixelArt.PAL, 26.dp)
                    Spacer(Modifier.size(2.dp))
                }
            }
            Spacer(Modifier.size(6.dp))
            val food = if (game.pet.stage == Stage.EGG) 0 else game.pet.hunger
            PixelIcon(PixelArt.bowl(food), PixelArt.PAL_BLUE_BOWL, 34.dp)
            Spacer(Modifier.weight(1f))
            PixelIcon(PixelArt.bowl(4), PixelArt.PAL_PINK_BOWL, 34.dp, Modifier.clickable {
                game.onSound(Chiptune.Sfx.SHOP); game.pressShop()
            })
            Spacer(Modifier.size(10.dp))
            PixelIcon(PixelArt.NOTE, if (game.save.musicOn) PixelArt.PAL_NOTE_ON else PixelArt.PAL_OFF, 30.dp, Modifier.clickable { game.toggleMusic() })
        }

        // ----- title, rainbow blocks like the reference -----
        Row(Modifier.combinedClickable(onClick = {}, onLongClick = { if (debug) game.timeWarp() })) {
            "SPROUT PALS".forEachIndexed { i, ch ->
                BasicText(ch.toString(), style = lcdText.copy(fontSize = 30.sp, color = Color(rainbowTitle[i % rainbowTitle.size])))
            }
        }

        // ----- garden code: yours to share, and a box to type a friend's -----
        if (notificationsOff) {
            // without notifications nobody hears the pal call; one tap fixes it
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFD84A))
                    .border(2.dp, INK, RoundedCornerShape(8.dp)).clickable { onEnableNotifications() }.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("🔔 TURN ON REMINDERS - TAP HERE", style = lcdText.copy(fontSize = 13.sp, lineHeight = 16.sp))
            }
        }
        GardenCodeBar(game, onShareCode, onAddFriend)

        // ----- the garden screen in its grey bezel -----
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(FRAME_EDGE)
                .padding(5.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(FRAME_INNER)
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(LCD_W / LCD_H.toFloat()).clip(RoundedCornerShape(6.dp))) {
                // Reading game.frame here makes the canvas redraw on every animation tick.
                @Suppress("UNUSED_VARIABLE") val tick = game.frame
                Canvas(Modifier.fillMaxSize()) { drawLcd(game) }
                PanelOverlay(game)
                game.toast?.let { ToastBanner(it) }
                game.caption?.let { CaptionStrip(it) }
            }
        }

        // ----- bottom bar: sound switch and the A / B / C buttons -----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(PixelArt.SPEAKER, if (game.save.soundOn) PixelArt.PAL_SPEAKER_ON else PixelArt.PAL_OFF, 30.dp, Modifier.clickable { game.toggleSound() })
            Spacer(Modifier.weight(1f))
            PillButton("A", BTN_A, onLong = { if (debug) game.debugWalk() }) { game.onSound(Chiptune.Sfx.A); game.pressA() }
            Spacer(Modifier.size(10.dp))
            PillButton("B", BTN_B, onLong = { if (debug) game.debugWeather() }) {
                game.onSound(Chiptune.Sfx.B)
                // Debug builds: B stands in for a shake, since an emulator can't be shaken.
                if (debug && game.pet.stage == Stage.EGG && game.panel == Panel.None) game.onShake()
                else if (game.pressB()) onUpdate()
            }
            Spacer(Modifier.size(10.dp))
            PillButton("C", BTN_C, onLong = { if (debug) game.debugBirthday() }) { game.onSound(Chiptune.Sfx.C); game.pressC() }
            Spacer(Modifier.weight(1f))
            PillButton("SHOP", BTN_SHOP, small = true, onLong = { game.secretPoints(debug) }) { game.onSound(Chiptune.Sfx.SHOP); game.pressShop() }
        }
        BasicText(
            "A next   B choose   C back",
            style = lcdText.copy(color = FRAME_INNER, fontSize = 13.sp),
        )
    }
}

/** A chunky console button: colored pill with a dark outline and a darker bottom lip for depth. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PillButton(label: String, color: Color, small: Boolean = false, onLong: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.width(if (small) 74.dp else 66.dp).height(54.dp)
            .clip(shape).background(INK).padding(3.dp)
            .clip(RoundedCornerShape(9.dp)).background(color.copy(red = color.red * 0.72f, green = color.green * 0.72f, blue = color.blue * 0.72f))
            .padding(bottom = 5.dp)
            .clip(RoundedCornerShape(9.dp)).background(color)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = lcdText.copy(fontSize = if (small) 15.sp else 24.sp))
    }
}

/** Draws a small text-grid icon (see [PixelArt]) with square pixels. */
@Composable
private fun PixelIcon(g: List<String>, pal: Map<Char, Long>, size: Dp, modifier: Modifier = Modifier) {
    val cols = g.maxOf { it.length }
    val rows = g.size
    Canvas(modifier.width(size).height(size * rows / cols)) {
        val p = this.size.width / cols
        g.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val argb = pal[ch] ?: return@forEachIndexed
                drawRect(Color(argb), Offset(c * p, r * p), Size(p + 0.6f, p + 0.6f))
            }
        }
    }
}

/** The corner icons from the reference picture, as text grids. 'K' = dark outline. */
private object PixelArt {
    val HEART = listOf(
        ".KK.KK.",
        "KRRKRRK",
        "KRWRRRK",
        "KRRRRRK",
        ".KRRRK.",
        "..KRK..",
        "...K...",
    )
    val HEART_EMPTY = listOf(
        ".KK.KK.",
        "K..K..K",
        "K.....K",
        "K.....K",
        ".K...K.",
        "..K.K..",
        "...K...",
    )
    /** A food bowl; [level] 0-4 fills it with kibble, so it doubles as the hunger meter. */
    fun bowl(level: Int): List<String> {
        val empty = "............"
        val full = listOf("....FFFF....", "..FFFFFFFF..", ".FFFFFFFFFF.")
        // 0 = empty, 1-2 = a layer per heart, 3-4 = heaped
        val layers = when (level) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 3 }
        val top = List(3 - layers) { empty } + full.takeLast(layers)
        return top + listOf(
            "KKKKKKKKKKKK",
            "KBWBBBBBBBBK",
            ".KBBBBBBBBK.",
            "..KKKKKKKK..",
        )
    }
    val NOTE = listOf(
        "...KKKKKKK",
        "...KNNNNNK",
        "...KNKKKNK",
        "...KNK.KNK",
        "...KNK.KNK",
        ".KKKNKKKNK",
        "KNNNNKNNNK",
        "KNNNKKNNNK",
        ".KKK..KKK.",
    )
    val SPEAKER = listOf(
        "....K.....",
        "...KK...W.",
        "KKKSK..W..",
        "KSSSK.W..W",
        "KSSSK.W..W",
        "KKKSK..W..",
        "...KK...W.",
        "....K.....",
    )
    val PAL = mapOf('K' to 0xFF2B2B33, 'R' to 0xFFE0344B, 'W' to 0xFFFFFFFF)
    val PAL_BLUE_BOWL = mapOf('K' to 0xFF2B2B33, 'B' to 0xFF3C9BE0, 'W' to 0xFFBFE4FF, 'F' to 0xFF8A5A2B)
    val PAL_PINK_BOWL = mapOf('K' to 0xFF2B2B33, 'B' to 0xFFF08FB5, 'W' to 0xFFFFD6E6, 'F' to 0xFFC0703A)
    val PAL_NOTE_ON = mapOf('K' to 0xFF2B2B33, 'N' to 0xFF8E6FE0)
    val PAL_SPEAKER_ON = mapOf('K' to 0xFF2B2B33, 'S' to 0xFF3C9BE0, 'W' to 0xFF3C9BE0)
    val PAL_OFF = mapOf('K' to 0xFF9A9AA2, 'N' to 0xFFD0D0D6, 'S' to 0xFFD0D0D6, 'W' to 0x00000000)
}

/** Words shown over the garden: menus, status pages, messages. Big monospace to match the pixels. */
@Composable
private fun PanelOverlay(game: Game) {
    val text: String = when (val p = game.panel) {
        Panel.None -> {
            game.mini?.let { GameHintStrip(it); return }
            if (game.pet.stage == Stage.EGG && game.frame % 8 < 6) ShakeBanner()
            if (game.walkMode) GoingPlacesBanner(game)
            if (game.away) AwayBanner(game)
            if (game.selected >= 0) IconHint(game.selected)
            return
        }
        is Panel.Message -> p.text
        is Panel.Feed -> {
            val names = listOf("MEAL", "SNACK") + if (game.save.pantry.isEmpty()) emptyList() else listOf("HARVEST (${game.save.pantry.size})")
            names.mapIndexed { i, n -> (if (i == p.choice) "> " else "  ") + n }.joinToString("\n")
        }
        is Panel.Status -> statusPage(game, p.page) +
            if (game.pet.stage == Stage.EGG) "\n\nC: CLOSE"
            else "\n\n${p.page + 1}/${Game.STATUS_PAGES}  " + when {
                p.page >= Game.PAGE_TROPHIES -> if (p.page + 1 >= Game.STATUS_PAGES) "C: CLOSE" else "A: NEXT  C: CLOSE"
                else -> "B: NEXT  C: CLOSE"
            }
        is Panel.Trophies -> { TrophyCard(game, p); return }
        is Panel.Book -> { BookCard(game, p); return }
        is Panel.PlayMenu -> "LET'S PLAY!\n\n" + MiniGame.NAMES.mapIndexed { i, n -> (if (i == p.choice) "> " else "  ") + n }.joinToString("\n") +
            "\n\nA: NEXT  B: PICK"
        is Panel.HowTo -> HOW_TO[p.game] + "\n\nB: START  C: BACK"
        is Panel.GameOver -> p.text + "\n\nB: PLAY AGAIN\nC: DONE"
        is Panel.Shop -> { ShopCard(game, p); return }
        is Panel.Garden -> gardenText(game, p)
        is Panel.Friends -> friendsText(game, p)
        Panel.Ended -> {
            val pet = game.pet
            val family = pet.ended == org.jasonagibson.sproutpals.engine.EndReason.NATURAL
            "${pet.species.title.uppercase()}\nRETURNED TO\nTHE GARDEN\n\n${pet.ended?.line ?: ""}\n\n" +
                if (family) "AND LEFT A FAMILY EGG!\n\nB = GENERATION ${pet.generation + 1}" else "B = NEW EGG"
        }
        is Panel.Update -> when {
            p.error != null -> "UPDATE FAILED\n${p.error}\n\nC = CLOSE"
            p.progress != null -> "DOWNLOADING\n${p.progress}%"
            else -> "NEW VERSION\n${p.info.versionName}\n\nB = UPDATE\nC = LATER"
        }
    }
    Box(Modifier.fillMaxSize().padding(vertical = 44.dp, horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(CREAM.copy(alpha = 0.93f)).border(3.dp, INK, RoundedCornerShape(6.dp)).padding(12.dp)) {
            BasicText(text, style = lcdText.copy(fontSize = if (text.length > 60) 15.sp else 19.sp, lineHeight = if (text.length > 60) 19.sp else 25.sp))
        }
    }
}

private fun hearts(n: Int) = "♥".repeat(n) + "♡".repeat(Rules.MAX_HEARTS - n)

/** The "how to play" card shown before each game starts. Same order as MiniGame.NAMES. */
private val HOW_TO = listOf(
    "FRUIT CATCH\n\nA ◀  move  ▶ C\nCatch the fruit,\ndodge the rocks!\n3 rocks = out",
    "JUMP ROPE\n\nPress B to jump\njust as the rope\nreaches the feet.\nIt speeds up!",
    "TIC-TAC-TOE\n\nYou are X.\nA / C: move\nB: place\nThree in a row wins",
    "HIGHER/LOWER\n\nWill the next\nnumber be...\nA: HIGHER\nC: LOWER\n5 rounds",
    "PLANT & HARVEST\n\nA ◀  move  ▶ C\nB: pick when the\nmeter hits gold!\nRipe = 1  Gold = 2\nDon't let them rot",
)

/** While a game runs: its buttons, always in view at the top of the garden. */
@Composable
private fun GameHintStrip(g: MiniGame) {
    val text = when (g) {
        is FruitCatch -> "A ◀ MOVE ▶ C   ♥${"♥".repeat((g.lives - 1).coerceAtLeast(0))}  ⏱${g.timeLeft / 8}"
        is JumpRope -> "B: JUMP!   JUMPS ${g.score}"
        is TicTacToe -> "A/C: MOVE  B: PLACE"
        is HigherLower -> "A: HIGHER  C: LOWER   ${g.round}/${g.rounds}"
        is PlantHarvest -> (if (g.callout.isNotBlank()) "${g.callout}   " else "A◀ ▶C  B: PICK   ") + "★${g.score}  ⏱${g.timeLeft / 8}"
    }
    Box(Modifier.fillMaxSize().padding(top = 50.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(INK.copy(alpha = 0.86f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            BasicText(text, style = lcdText.copy(color = Color(0xFFFFD84A), fontSize = 13.sp, lineHeight = 16.sp))
        }
    }
}

/** The Garden panel: the chosen plot (or the seed list when planting). */
private fun gardenText(game: Game, p: Panel.Garden): String {
    val now = game.now()
    val head = "GARDEN   PLOT ${p.plot + 1}/${Garden.PLOTS}\n\n"
    if (p.picking) {
        val list = Garden.seeds(game.save).mapIndexed { i, c ->
            (if (i == p.seed) "> " else "  ") + "${c.name.padEnd(10)} ⏱${hours(c.growMinutes).padEnd(3)} ★${Garden.harvestPoints(game.save, c, now)}"
        }.joinToString("\n")
        return head + "PICK A SEED\n$list\n\n⏱ = WET HOURS TO GROW\n★ = STARS AT HARVEST\n\nA: NEXT  B: PLANT\nC: BACK"
    }
    val plot = Garden.plots(game.save)[p.plot]
    val name = Garden.crop(plot.crop)?.name ?: ""
    val left = hours(Garden.minutesLeft(plot, now))
    val (line, action) = when (Garden.state(plot, now)) {
        PlotState.EMPTY -> "EMPTY SOIL" to "B: PLANT"
        PlotState.DRY -> "$name\nTHIRSTY! NOT GROWING\n$left OF WET SOIL TO GO" to "B: WATER"
        PlotState.GROWING -> "$name\nGROWING... $left TO GO" to "B: WATER"
        PlotState.RIPE -> "$name IS RIPE!" to "B: HARVEST"
    }
    return head + line + (if (p.note.isNotBlank()) "\n\n${p.note}" else "") + "\n\nA: NEXT PLOT  $action\nC: CLOSE"
}

private fun hours(min: Int) = if (min >= 60) "${(min + 30) / 60}H" else "${min}M"

private fun statusPage(game: Game, page: Int): String {
    val pet = game.pet
    if (pet.stage == Stage.EGG) return "AN EGG!\nSHAKE YOUR\nPHONE TO\nOPEN IT"
    val ageDays = (game.now() - (pet.hatchedAt ?: game.now())) / Rules.DAY
    return when (page) {
        0 -> "HUNGRY\n${hearts(pet.hunger)}"
        1 -> "HAPPY\n${hearts(pet.happy)}"
        2 -> "${pet.species.title.uppercase()}${if (pet.species.rare) " ★" else ""}\n${pet.stage.name}\nAGE ${ageDays}d  WT ${pet.weight}" +
            (if (pet.generation > 1) "\nGENERATION ${pet.generation}" else "") +
            (org.jasonagibson.sproutpals.engine.Abilities.describe(pet.species)?.let { "\n\n$it" } ?: "") +
            if (game.birthdaySoon) "\n\nBIRTHDAY\nTOMORROW!" else ""
        3 -> {
            val steps = game.save.stepsTotal
            val next = Route.next(steps)
            if (next == null) "WALKED ${"%.0f".format(Route.miles(steps))} MI\n\nEVERY SHOP\nVISITED!"
            else "NEXT STOP\n${next.name.uppercase()}\n${next.town.uppercase()}\n\n${"%.1f".format(Route.milesToNext(steps))} MI TO GO"
        }
        4 -> "★ STARS ${game.save.points}\nSTEPS ${game.save.stepsTotal}\n\n" + weatherLine(game) + "\n\nWALK, CARE, PLAY\nAND GARDEN\nTO EARN MORE ★"
        Game.PAGE_TROPHIES -> {
            val (got, of) = Achievements.progress(game.save)
            "🏆 TROPHIES\n\n${Achievements.percent(game.save)}% COMPLETE\n$got OF $of MEDALS\n\nB: OPEN THE LIST"
        }
        else -> "📖 MEMORY BOOK\n\n${game.save.memories.size} PAL${if (game.save.memories.size == 1) "" else "S"}\nREMEMBERED\n\nB: OPEN THE BOOK"
    }
}

/** Today's weather for the status screen. */
private fun weatherLine(game: Game): String {
    val now = game.now()
    val w = game.save.weather ?: return "CLEAR SKIES"
    return when {
        now < w.start -> "CLEAR SKIES"   // what's coming stays a surprise
        now < w.end -> "${w.kind.icon} ${w.kind.title}\n${(w.end - now) / Rules.HOUR + 1}H LEFT"
        else -> "CLEAR SKIES"
    }
}

/** Blinks under the egg until it's shaken open. */
@Composable
private fun ShakeBanner() {
    Box(Modifier.fillMaxSize().padding(bottom = 58.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(INK).padding(horizontal = 12.dp, vertical = 6.dp)) {
            BasicText("SHAKE TO OPEN!", style = lcdText.copy(color = CREAM, fontSize = 18.sp))
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) CREAM else SHELL_EDGE)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicText("$label ${if (on) "ON" else "OFF"}", style = lcdText.copy(fontSize = 13.sp, color = if (on) INK else CREAM))
    }
}

/** While walking with you: a signpost to the next garden shop on the route. */
@Composable
private fun GoingPlacesBanner(game: Game) {
    val steps = game.save.stepsTotal
    val next = Route.next(steps)
    val text = if (next == null) "WALKING → EVERYWHERE VISITED!"
    else "→ ${next.name.uppercase()}  ${"%.1f".format(Route.milesToNext(steps))} MI"
    Box(Modifier.fillMaxSize().padding(top = 44.dp, start = 10.dp), contentAlignment = Alignment.TopStart) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFA8743F)).border(2.dp, Color(0xFF5C3A1A), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            BasicText(text, style = lcdText.copy(color = Color(0xFFFFF3D6), fontSize = 11.sp, lineHeight = 13.sp))
        }
    }
}

/** A caption along the bottom of the garden (e.g. the newly hatched baby's name). */
@Composable
private fun CaptionStrip(text: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 54.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(INK.copy(alpha = 0.88f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
            BasicText(text, style = lcdText.copy(color = Color(0xFFFFD84A), fontSize = 14.sp, lineHeight = 17.sp))
        }
    }
}

/** While our pal is out: a little sign saying where, and for how long. */
@Composable
private fun AwayBanner(game: Game) {
    val left = ((game.save.awayUntil - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    val text = "🧳 AT ${game.save.awayHost}'S GARDEN  ·  HOME IN ${if (left >= 60) "${left / 60}H ${left % 60}M" else "${left}M"}"
    Box(Modifier.fillMaxSize().padding(top = 44.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFA8743F)).border(2.dp, Color(0xFF5C3A1A), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            BasicText(text, style = lcdText.copy(color = Color(0xFFFFF3D6), fontSize = 11.sp, lineHeight = 13.sp))
        }
    }
}

/** The friends panel: pick a friend, then what to do. */
private fun friendsText(game: Game, p: Panel.Friends): String {
    val head = "♥ FRIENDS\n\n"
    val tail = (if (p.note.isNotBlank()) "\n\n${p.note}" else "")
    if (p.friend == null) {
        if (game.friendList.isEmpty()) return head + p.note + "\n\nC: CLOSE"
        val list = game.friendList.mapIndexed { i, f ->
            val pet = if (f.species == "EGG") "EGG" else "${f.species} ${f.stage}"
            (if (i == p.choice) "> " else "  ") + "${f.code}  $pet"
        }.joinToString("\n")
        return head + list + tail + "\n\nA: NEXT  B: PICK\nC: CLOSE"
    }
    val actions = game.friendActionNames().mapIndexed { i, a -> (if (i == p.choice) "> " else "  ") + a }.joinToString("\n")
    return head + "${p.friend}\n\n" + actions + tail + "\n\nA: NEXT  B: GO\nC: BACK"
}

/** What each menu icon does, shown as a label while it's selected (phones can't hover). */
private val HINTS = listOf(
    "STATUS · B: hunger, happiness, age",
    "FEED · B: meal or snack",
    "LIGHT · B: lights on / off",
    "PLAY · B: play together",
    "MEDICINE · B: when it's sick",
    "CLEAN · B: wash the mess away",
    "GARDEN · B: plant, water, harvest",
)

@Composable
private fun IconHint(selected: Int) {
    val text = HINTS.getOrNull(selected) ?: return
    Box(Modifier.fillMaxSize().padding(top = 50.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(INK.copy(alpha = 0.86f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            BasicText(text, style = lcdText.copy(color = Color(0xFFFFD84A), fontSize = 13.sp, lineHeight = 16.sp))
        }
    }
}
