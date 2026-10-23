package org.jasonagibson.sproutpals.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jasonagibson.sproutpals.app.Chiptune
import org.jasonagibson.sproutpals.app.Game
import org.jasonagibson.sproutpals.app.Panel
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Stage

private val SHELL = Color(0xFF2E5E3A)
private val SHELL_EDGE = Color(0xFF1E4128)
private val CREAM = Color(0xFFF3E9C6)
private val INK = Color(0xFF1E2A1C)

private val lcdText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 20.sp, color = INK, textAlign = TextAlign.Center, lineHeight = 26.sp)

/**
 * The whole app is one "device": title, LCD, four buttons. Like the 90s egg,
 * everything is done with A (next), B (choose), C (back) - plus a Shop button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PalScreen(game: Game, debug: Boolean, onUpdate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(SHELL).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        BasicText(
            "SPROUT PALS",
            style = lcdText.copy(color = CREAM, fontSize = 30.sp, letterSpacing = 3.sp),
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { if (debug) game.timeWarp() }),
        )

        // Sound switches: tap to turn the tune or the beeps on and off.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Toggle("♪ MUSIC", game.save.musicOn) { game.toggleMusic() }
            Toggle("🔊 SOUND", game.save.soundOn) { game.toggleSound() }
        }

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(SHELL_EDGE)
                .padding(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(LCD_W / LCD_H.toFloat()).border(4.dp, INK)) {
                // Reading game.frame here makes the canvas redraw on every animation tick.
                @Suppress("UNUSED_VARIABLE") val tick = game.frame
                Canvas(Modifier.fillMaxSize()) { drawLcd(game) }
                PanelOverlay(game)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundButton("A", CREAM, onLong = { if (debug) game.debugWalk() }) {  game.onSound(Chiptune.Sfx.A);game.pressA() }
            RoundButton("B", CREAM) { game.onSound(Chiptune.Sfx.B);
                // Debug builds: B stands in for a shake, since an emulator can't be shaken.
                if (debug && game.pet.stage == Stage.EGG && game.panel == Panel.None) game.onShake()
                else if (game.pressB()) onUpdate()
            }
            RoundButton("C", CREAM, onLong = { if (debug) game.debugBirthday() }) {  game.onSound(Chiptune.Sfx.C);game.pressC() }
            RoundButton("SHOP", Color(0xFFF2B33D), small = true) {  game.onSound(Chiptune.Sfx.SHOP);game.pressShop() }
        }
        BasicText(
            "A next   B choose   C back",
            style = lcdText.copy(color = CREAM.copy(alpha = 0.7f), fontSize = 13.sp),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RoundButton(label: String, color: Color, small: Boolean = false, onLong: () -> Unit = {}, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(SHELL_EDGE).padding(5.dp)
            .clip(CircleShape).background(color)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = lcdText.copy(fontSize = if (small) 15.sp else 26.sp))
    }
}

/** Words shown over the garden: menus, status pages, messages. Big monospace to match the pixels. */
@Composable
private fun PanelOverlay(game: Game) {
    val text: String = when (val p = game.panel) {
        Panel.None -> {
            if (game.pet.stage == Stage.EGG && game.frame % 8 < 6) ShakeBanner()
            if (game.walkMode) GoingPlacesBanner(game)
            return
        }
        is Panel.Message -> p.text
        is Panel.Feed -> (if (p.choice == 0) "> MEAL\n  SNACK" else "  MEAL\n> SNACK")
        is Panel.Status -> statusPage(game, p.page)
        Panel.Shop -> "SHOP\nOPENS SOON!\n\nPOINTS: ${game.save.points}"
        Panel.Garden -> "GARDEN\nCOMING SOON!\n\nPLANT, GROW,\nHARVEST"
        Panel.Ended -> {
            val pet = game.pet
            "${pet.species.title.uppercase()}\nRETURNED TO\nTHE GARDEN\n\n${pet.ended?.line ?: ""}\n\nB = NEW EGG"
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

private fun statusPage(game: Game, page: Int): String {
    val pet = game.pet
    if (pet.stage == Stage.EGG) return "AN EGG!\nSHAKE YOUR\nPHONE TO\nOPEN IT"
    val ageDays = (game.now() - (pet.hatchedAt ?: game.now())) / Rules.DAY
    return when (page) {
        0 -> "HUNGRY\n${hearts(pet.hunger)}"
        1 -> "HAPPY\n${hearts(pet.happy)}"
        2 -> "${pet.species.title.uppercase()}${if (pet.species.rare) " ★" else ""}\n${pet.stage.name}\nAGE ${ageDays}d  WT ${pet.weight}" +
            if (game.birthdaySoon) "\n\nBIRTHDAY\nTOMORROW!" else ""
        3 -> {
            val steps = game.save.stepsTotal
            val next = Route.next(steps)
            if (next == null) "WALKED ${"%.0f".format(Route.miles(steps))} MI\n\nEVERY SHOP\nVISITED!"
            else "NEXT STOP\n${next.name.uppercase()}\n${next.town.uppercase()}\n\n${"%.1f".format(Route.milesToNext(steps))} MI TO GO"
        }
        else -> "POINTS ${game.save.points}\nSTEPS ${game.save.stepsTotal}\n\nWALK & CARE\nTO EARN MORE"
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
