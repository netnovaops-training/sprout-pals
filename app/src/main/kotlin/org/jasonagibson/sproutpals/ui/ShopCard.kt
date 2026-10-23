package org.jasonagibson.sproutpals.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import org.jasonagibson.sproutpals.app.Game
import org.jasonagibson.sproutpals.app.Panel
import org.jasonagibson.sproutpals.engine.Shop
import org.jasonagibson.sproutpals.engine.ShopTab
import org.jasonagibson.sproutpals.engine.Slot
import org.jasonagibson.sproutpals.engine.Visitors

/*
 * The shop card, in clear sections:
 *   tabs + points  |  picture, name, price or on/off  |  what it does (green)  |  the catch (red)  |  buttons
 */
private val INK = Color(0xFF1E2A1C)
private val CREAM = Color(0xFFF3E9C6)
private val GOOD = Color(0xFF2F7D32)
private val BAD = Color(0xFFB3261E)
private val GOLD = Color(0xFFE8B923)
private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = INK)

@Composable
private fun Chip(text: String, bg: Color, fg: Color = INK, size: Int = 13) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(bg).border(2.dp, INK, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        BasicText(text, style = mono.copy(fontSize = size.sp, color = fg))
    }
}

@Composable
private fun Section(title: String, color: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.10f)).border(1.5.dp, color, RoundedCornerShape(5.dp)).padding(6.dp)) {
        BasicText(title, style = mono.copy(fontSize = 11.sp, color = color))
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun PerkRow(icon: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { BasicText(icon, style = mono.copy(fontSize = 15.sp, color = color)) }
        Spacer(Modifier.width(4.dp))
        BasicText(text, style = mono.copy(fontSize = 12.sp, lineHeight = 15.sp))
    }
}

@Composable
fun ShopCard(game: Game, p: Panel.Shop) {
    val save = game.save
    val items = Shop.items(p.tab, save)
    val item = items[p.choice.coerceIn(0, items.size - 1)]
    val owned = Shop.owns(save, item.id) && !item.repeatable
    val on = Shop.showing(save, item.id)

    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(6.dp)).background(CREAM.copy(alpha = 0.96f)).border(3.dp, INK, RoundedCornerShape(6.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── tabs and points
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (t in ShopTab.entries) {
                    Chip(t.title, if (t == p.tab) INK else CREAM, if (t == p.tab) CREAM else INK, 11)
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                Chip("★ ${save.points}", GOLD, size = 13)
            }

            // ── the item: picture, name, price or switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(64.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFBFE3A8)).border(2.dp, INK, RoundedCornerShape(5.dp))) {
                    drawItemPreview(item.id, game.frame, size.width / 24f)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    BasicText("${p.choice + 1}/${items.size}", style = mono.copy(fontSize = 10.sp, color = INK.copy(alpha = 0.6f)))
                    BasicText(item.name, style = mono.copy(fontSize = 16.sp))
                    BasicText(item.blurb.replace('\n', ' '), style = mono.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(3.dp))
                    when {
                        !owned -> Chip("PRICE ★ ${item.price}" + if (item.repeatable && save.mysteryCount > 0) "  ×${save.mysteryCount}" else "", GOLD)
                        on -> Chip(if (item.slot != null) "☑ WEARING" else "☑ IN THE GARDEN", GOOD, CREAM)
                        else -> Chip(when { item.slot != null -> "☐ IN THE CLOSET"; item.tab == ShopTab.DECOR -> "☐ IN THE SHED"; else -> "☐ AT HOME" }, CREAM)
                    }
                }
            }

            // ── what it does, and the catch
            val perks = Visitors.PERKS[item.id]
            if (perks != null) {
                Section("WHAT IT DOES", GOOD) { perks.filter { it.good }.forEach { PerkRow(it.icon, it.text, GOOD) } }
                if (perks.any { !it.good }) Section("THE CATCH", BAD) { perks.filter { !it.good }.forEach { PerkRow(it.icon, it.text, BAD) } }
                if (on && item.tab == ShopTab.CREATURES) BasicText("Goes home after ${Shop.OUT_HOURS}h - switch it on again", style = mono.copy(fontSize = 10.sp, color = INK.copy(alpha = 0.7f)))
            } else if (item.slot != null) {
                Section("WHAT IT DOES", GOOD) {
                    PerkRow("👕", when (item.slot) { Slot.HAT -> "Worn on the head"; Slot.SHIRT -> "Worn as a shirt (people)"; Slot.PANTS -> "Worn as pants (people)" }, GOOD)
                    PerkRow("✨", "Just for looks - one of each kind", GOOD)
                }
            } else {
                Section("WHAT IT DOES", GOOD) { PerkRow("?", "????", GOOD) }
            }

            if (p.note.isNotBlank()) BasicText(p.note.replace('\n', ' '), style = mono.copy(fontSize = 13.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())

            // ── buttons
            val b = when {
                !owned -> "B BUY"
                on -> when { item.slot != null -> "B TAKE OFF"; item.tab == ShopTab.DECOR -> "B PUT AWAY"; else -> "B SEND HOME" }
                else -> when { item.slot != null -> "B WEAR"; item.tab == ShopTab.DECOR -> "B PUT OUT"; else -> "B BRING OUT" }
            }
            BasicText("A NEXT · $b · SHOP PAGE · C CLOSE", style = mono.copy(fontSize = 10.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        }
    }
}
