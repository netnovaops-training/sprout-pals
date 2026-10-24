package org.jasonagibson.sproutpals.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jasonagibson.sproutpals.app.Game
import org.jasonagibson.sproutpals.app.Panel
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.engine.Achievements
import org.jasonagibson.sproutpals.engine.EndReason
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.app.dateAt
import kotlinx.datetime.number

/*
 * The trophy list, the memory book, and the banner that slides in when something is earned.
 */
private val INK = Color(0xFF1E2A1C)
private val CREAM = Color(0xFFF3E9C6)
private val GOLD = Color(0xFFE8B923)
private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = INK)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(CREAM.copy(alpha = 0.97f))
                .border(3.dp, INK, RoundedCornerShape(6.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) { content() }
    }
}

/** Medals as small coloured discs: bronze, silver, gold; dim until earned. */
@Composable
private fun Medals(tiers: Int, earned: Int) {
    val colors = if (tiers == 1) listOf(GOLD) else listOf(Color(0xFFC07A3A), Color(0xFFB8C0C8), GOLD)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        colors.forEachIndexed { i, c ->
            Box(
                Modifier.size(13.dp).clip(RoundedCornerShape(50)).background(if (i < earned) c else Color(0x22000000))
                    .border(1.5.dp, if (i < earned) INK else Color(0x33000000), RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
fun TrophyCard(game: Game, p: Panel.Trophies) {
    val rows = Achievements.rows(game.save)
    val (got, of) = Achievements.progress(game.save)
    Card {
        val pct = Achievements.percent(game.save)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("🏆 TROPHIES", style = mono.copy(fontSize = 15.sp))
            Spacer(Modifier.weight(1f))
            BasicText("$pct% COMPLETE", style = mono.copy(fontSize = 13.sp))
        }
        // progress bar: done vs still to do
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x22000000)).border(1.5.dp, INK, RoundedCornerShape(5.dp))) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(10.dp).background(Color(0xFF8FD46A)))
        }
        BasicText("$got DONE  ·  ${of - got} TO GO", style = mono.copy(fontSize = 10.sp, color = INK.copy(alpha = 0.7f)))
        rows.drop(p.top).take(Game.TROPHY_ROWS).forEach { (f, n, earned) ->
            val next = f.tiers.firstOrNull { it > n }
            val hidden = f.secret && earned == 0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { BasicText(if (hidden) "❔" else f.icon, style = mono.copy(fontSize = 16.sp)) }
                Column(Modifier.weight(1f)) {
                    BasicText(if (hidden) "???" else f.name, style = mono.copy(fontSize = 11.sp, color = if (earned > 0) INK else INK.copy(alpha = 0.55f)))
                    BasicText(
                        when {
                            hidden -> "???"
                            f.secret && next != null && f.tiers.size > 1 -> "${f.reveal}  ·  $n / $next"
                            f.secret -> f.reveal
                            next == null -> "DONE!"
                            f.tiers.size == 1 -> "NOT YET"
                            else -> "$n / $next"
                        },
                        style = mono.copy(fontSize = 9.sp, color = INK.copy(alpha = 0.6f)),
                    )
                }
                Medals(f.tiers.size, earned)
            }
        }
        BasicText(
            "${p.top / Game.TROPHY_ROWS + 1}/${(rows.size + Game.TROPHY_ROWS - 1) / Game.TROPHY_ROWS}  ·  A: MORE  ·  C: CLOSE",
            style = mono.copy(fontSize = 10.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Oct 23" for a moment in time, in the phone's time zone. */
private fun day(ms: Long) = dateAt(ms).let { "${MONTHS[it.month.number - 1]} ${it.day}" }

@Composable
fun BookCard(game: Game, p: Panel.Book) {
    val book = game.save.memories
    Card {
        BasicText("📖 MEMORY BOOK", style = mono.copy(fontSize = 15.sp))
        if (book.isEmpty()) {
            BasicText("No pages yet.\n\nEvery pal that returns to the garden gets a page here.", style = mono.copy(fontSize = 12.sp))
            BasicText("C: CLOSE", style = mono.copy(fontSize = 10.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            return@Card
        }
        // newest first
        val m = book.reversed()[p.page.coerceIn(0, book.size - 1)]
        val days = (m.endedAt - m.bornAt) / Rules.DAY
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(84.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFBFE3A8)).border(2.dp, INK, RoundedCornerShape(5.dp))) {
                val u = size.width / 32f
                character(m.species, m.stage, centerX = 16f, ground = 30f, unitPx = u, unitsPerPixel = 26f / CharacterArt.of(m.species, m.stage).height)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                BasicText(m.species.title.uppercase() + if (m.species.rare) "  ✨ RARE" else "", style = mono.copy(fontSize = 16.sp))
                BasicText("${day(m.bornAt)} - ${day(m.endedAt)}", style = mono.copy(fontSize = 11.sp))
                BasicText("$days DAYS · REACHED ${m.stage.name}", style = mono.copy(fontSize = 11.sp))
                if (m.generation > 1) BasicText("GENERATION ${m.generation}", style = mono.copy(fontSize = 11.sp, color = Color(0xFF8E4FC9)))
                BasicText(if (m.mistakes == 0) "NO CARE MISTAKES!" else "${m.mistakes} CARE MISTAKES", style = mono.copy(fontSize = 10.sp, color = INK.copy(alpha = 0.7f)))
            }
        }
        BasicText(
            "\"" + m.species.title + " " + m.reason.line + ".\"" + if (m.reason == EndReason.NATURAL) " 🌈" else "",
            style = mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            "PAGE ${p.page + 1} / ${book.size}  ·  A: NEXT  ·  C: CLOSE",
            style = mono.copy(fontSize = 10.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Slides down from the top: the icon, what was earned. Key items get a gold frame. */
@Composable
fun ToastBanner(t: Game.Toast) {
    var shown by remember(t) { mutableStateOf(false) }
    LaunchedEffect(t) { shown = true }
    val drop by animateFloatAsState(if (shown) 0f else -80f, label = "toast")
    Box(Modifier.fillMaxSize().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer { translationY = drop * density }
                .clip(RoundedCornerShape(8.dp)).background(if (t.key) Color(0xFF2B2140) else INK)
                .border(3.dp, if (t.key) GOLD else Color(0xFF8FD46A), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(t.icon, style = mono.copy(fontSize = 24.sp))
            Spacer(Modifier.width(8.dp))
            Column {
                BasicText(t.title, style = mono.copy(fontSize = 12.sp, color = if (t.key) GOLD else Color(0xFF8FD46A)))
                BasicText(t.line, style = mono.copy(fontSize = 12.sp, color = CREAM))
            }
        }
    }
}
