package org.jasonagibson.sproutpals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jasonagibson.sproutpals.app.Game

private val INK = Color(0xFF1E2A1C)
private val PAPER = Color(0xFFFFF8E4)
private val LEAF = Color(0xFF3E8F26)
private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = INK)

/**
 * Under the title: "MY GARDEN  ABC123  [SHARE]  [+ ADD]  [♥ VISIT]".
 * + ADD swaps the row for a box to type a friend's code (keyboard opens, capitals on).
 * ♥ VISIT opens the friends panel: send your pal over, or a heart.
 */
@Composable
fun GardenCodeBar(game: Game, onShare: () -> Unit, onAdd: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val code = game.save.gardenCode

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PAPER)
                .border(2.dp, INK, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (!adding) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column {
                        BasicText("🌱 MY GARDEN", style = mono.copy(fontSize = 10.sp, color = LEAF))
                        BasicText(code?.chunked(3)?.joinToString(" ") ?: "······", style = mono.copy(fontSize = 18.sp, letterSpacing = 1.sp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (code != null) Chip("SHARE", Color(0xFFF5C542)) { onShare() }
                    Chip("+ ADD", Color(0xFF8FD46A)) { adding = true; typed = "" }
                    Chip("♥ VISIT", Color(0xFFF2A3C0)) { game.openFriends() }
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText("FRIEND'S CODE", style = mono.copy(fontSize = 12.sp, color = LEAF))
                    Box(
                        Modifier.width(120.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)
                            .border(2.dp, LEAF, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        BasicTextField(
                            value = typed,
                            // keep what a code can contain; forgiving of spaces and dashes, max 6 letters/digits
                            onValueChange = { v -> typed = v.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                            singleLine = true,
                            textStyle = mono.copy(fontSize = 20.sp, letterSpacing = 2.sp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (typed.length == 6) { onAdd(typed); adding = false }
                            }),
                            modifier = Modifier.focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Chip("ADD", if (typed.length == 6) Color(0xFF8FD46A) else Color(0xFFD8D8D0)) {
                        if (typed.length == 6) { onAdd(typed); adding = false }
                    }
                    Chip("✕", Color(0xFFF08FB5)) { adding = false }
                }
            }
        }
        game.friendsNote?.let {
            BasicText(it, style = mono.copy(fontSize = 12.sp, color = Color(0xFF5E6470)), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Chip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(INK).padding(2.dp)
            .clip(RoundedCornerShape(6.dp)).background(color).clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = mono.copy(fontSize = 12.sp), softWrap = false)
    }
}

@Suppress("unused")
@Composable
private fun Gap() = Spacer(Modifier.size(4.dp))
