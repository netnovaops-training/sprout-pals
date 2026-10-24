package org.jasonagibson.sproutpals.art

import org.jasonagibson.sproutpals.engine.Kind
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Stage

/*
 * All the pixel art, drawn as text. One character = one pixel; '.' = see-through.
 * Characters are 32 wide, SNES style: big heads, hand-placed light and shadow.
 *
 * UPPER-CASE letters are a color, the matching lower-case letter is that color's shadow:
 *   B/b skin or fur     H/h hair or fur markings    W/w white (ruff, eyes, teeth)
 *   C/c shirt or hoodie P/p hoodie pocket           L/l trousers    F/f shoes
 *   Y/y straw hat       O hat band                  D/d beard       X/x scarf
 *   K glasses or sunglasses frames (cats: whiskers)  E eyes   M mouth   N nose   R blush
 *   A ears   U muzzle   G cheeks   T tail   S walking cane
 *
 * A grid is shared by every character of the same kind (person / dog / cat); the palette
 * gives each one its own colors. People have their own heads on shared bodies.
 */

typealias Grid = List<String>

/** Every pixel doubled, for small props drawn on the big screen. */
fun Grid.x2(): Grid = flatMap { row -> val r = row.map { "$it$it" }.joinToString(""); listOf(r, r) }

object Palettes {
    private val base = mapOf('E' to 0xFF1B1B1B, 'N' to 0xFF1B1B1B, 'R' to 0xFFE89A8A, 'S' to 0xFF7A4A24)

    // Looks from Jason's reference picture and photos (10-23). Each character owns its colors.
    private val bySpecies: Map<Species, Map<Char, Long>> = mapOf(
        // Jun: big curly hair, sunglasses, wide grin, pink hoodie, jeans
        Species.JUN to mapOf(
            'B' to 0xFFB07A55, 'b' to 0xFF8C5E3F, 'H' to 0xFF3E2716, 'h' to 0xFF2A190D, 'K' to 0xFF111111,
            'W' to 0xFFFFFFFF, 'M' to 0xFF7A2A2A, 'C' to 0xFFF2A3C0, 'c' to 0xFFD67FA0, 'P' to 0xFFF7B6CC,
            'p' to 0xFFD67FA0, 'L' to 0xFF4A6FA5, 'l' to 0xFF36527C, 'F' to 0xFF7A4A24, 'f' to 0xFF5A3518,
        ),
        // Kona: red bob, glasses, plaid scarf, white top
        Species.KONA to mapOf(
            'B' to 0xFFF2D0B5, 'b' to 0xFFD9B094, 'H' to 0xFFB0402A, 'h' to 0xFF8A2E1E, 'K' to 0xFF2B2B2B,
            'W' to 0xFFFFFFFF, 'M' to 0xFFC0504A, 'X' to 0xFFC8483C, 'x' to 0xFF5B3A6E, 'C' to 0xFFF4F4F0,
            'c' to 0xFFD8D8D0, 'P' to 0xFFFFFFFF, 'p' to 0xFFD8D8D0, 'L' to 0xFF3B3B4A, 'l' to 0xFF2A2A36,
            'F' to 0xFF7A4A24, 'f' to 0xFF5A3518,
        ),
        // Karta: swept-up dark reddish-brown hair, maroon tee, tattoo sleeves, dark jeans
        Species.KARTA to mapOf(
            'B' to 0xFFE8B58E, 'b' to 0xFFC8936C, 'H' to 0xFF5A2A20, 'h' to 0xFF3E1C16, 'K' to 0xFF1B1B1B,
            'W' to 0xFFFFFFFF, 'M' to 0xFF9A4A4A, 'C' to 0xFF6E1E2E, 'c' to 0xFF521624, 'P' to 0xFF7E2A3A,
            'p' to 0xFF521624, 'L' to 0xFF2C3550, 'l' to 0xFF1E2438, 'F' to 0xFFEDEDED, 'f' to 0xFFBFBFBF,
        ),
        // Nathan: light-brown hair and beard, black tee with a pocket, dark trousers
        Species.NATHAN to mapOf(
            'B' to 0xFFE6A97E, 'b' to 0xFFC4875C, 'H' to 0xFF8A5E3A, 'h' to 0xFF6A4428, 'K' to 0xFF1B1B1B,
            'W' to 0xFFFFFFFF, 'M' to 0xFF8A4A3A, 'C' to 0xFF1E1E22, 'c' to 0xFF101014, 'P' to 0xFF2A2A30,
            'p' to 0xFF101014, 'L' to 0xFF2E2E3A, 'l' to 0xFF1E1E28, 'F' to 0xFF5A3518, 'f' to 0xFF3E2410,
        ),
        // Jason (rare): straw hat, glasses, beard, blue hoodie, white sneakers
        Species.JASON to mapOf(
            'B' to 0xFFC68E63, 'b' to 0xFFA0704C, 'H' to 0xFF2B2018, 'h' to 0xFF1A130C, 'Y' to 0xFFD9B26A,
            'y' to 0xFFB08F4E, 'O' to 0xFF7A4A24, 'K' to 0xFF1B1B1B, 'W' to 0xFFEAF4FF, 'D' to 0xFF3A2A1E,
            'd' to 0xFF261A12, 'M' to 0xFF7A2A2A, 'C' to 0xFF2F6BC4, 'c' to 0xFF224F96, 'P' to 0xFF4A82D6,
            'p' to 0xFF224F96, 'L' to 0xFF2C3E66, 'l' to 0xFF1F2D4A, 'F' to 0xFFEDEDED, 'f' to 0xFFBFBFBF,
        ),
        // Rusty: sable collie - red-brown coat, white blaze, muzzle, ruff and legs
        Species.RUSTY to mapOf(
            'B' to 0xFFB5652A, 'b' to 0xFF8A4A1C, 'W' to 0xFFF4F1E8, 'w' to 0xFFD6D0BE, 'A' to 0xFF7A3E14,
            'T' to 0xFFB5652A, 'M' to 0xFFE07080, 'U' to 0xFFF4F1E8, 'G' to 0xFFB5652A,
        ),
        // Kara: rough collie from Jason's photos - charcoal-grey coat, big white ruff and legs, tan muzzle
        Species.KARA to mapOf(
            'B' to 0xFF54565F, 'b' to 0xFF3C3D45, 'W' to 0xFFF4F1E8, 'w' to 0xFFD6D0BE, 'A' to 0xFF32333A,
            'T' to 0xFF54565F, 'M' to 0xFFE07080, 'U' to 0xFFC98A4B, 'G' to 0xFFC98A4B,
        ),
        // DJ: black & white tuxedo cat, green eyes
        Species.DJ to mapOf(
            'B' to 0xFF1E1E22, 'b' to 0xFF0E0E10, 'H' to 0xFF1E1E22, 'W' to 0xFFF4F1E8, 'w' to 0xFFD6D0BE,
            'A' to 0xFF3A3A42, 'E' to 0xFF9BD14A, 'N' to 0xFFE89AA8, 'M' to 0xFFE07080, 'K' to 0xFFF4F1E8,
            'T' to 0xFF1E1E22,
        ),
        // Maui: brown tabby - dark stripes, cream chest, amber eyes
        Species.MAUI to mapOf(
            'B' to 0xFF8B5E3C, 'b' to 0xFF6B4529, 'H' to 0xFF4E3220, 'W' to 0xFFE8D5B5, 'w' to 0xFFC9B695,
            'A' to 0xFF4E3220, 'E' to 0xFFE8B923, 'N' to 0xFFE89AA8, 'M' to 0xFFE07080, 'K' to 0xFF1B1B1B,
            'T' to 0xFF4E3220,
        ),
    )

    /** Old age: grey hair and beard for people, a grey muzzle for animals. */
    private val oldHuman = mapOf('H' to 0xFFC9C9C9, 'h' to 0xFFA6A6A6, 'D' to 0xFFC9C9C9, 'd' to 0xFFA6A6A6)
    private val oldAnimal = mapOf('U' to 0xFFBDBDBD)

    fun of(species: Species, stage: Stage): Map<Char, Long> {
        var p = base + bySpecies.getValue(species)
        if (stage == Stage.OLD) p = p + if (species.kind == Kind.HUMAN) oldHuman else oldAnimal
        return p
    }
}

object Sprites {

    val EGG: Grid = listOf(
        "............WWWWWWWW............",
        "..........WWWWWWWWWWWW..........",
        ".........WWWLLWWWWWWWWW.........",
        "........WWWLLWWWWGGWWWWW........",
        ".......WWWWLWWWWWGGGWWWWW.......",
        ".......WWGGWWWWWWWGWWWWWW.......",
        "......WWWGGGWWWWWWWWWWGGWW......",
        "......WWWWGWWWWWWWWWWGGGWW......",
        "......WWWWWWWWWGGWWWWWGWWW......",
        "......WWWWWWWWGGGWWWWWWWWW......",
        "......WWGGWWWWWGWWWWWWWWWw......",
        "......WWGGGWWWWWWWWWGGWWWw......",
        "......WWWGWWWWWWWWWGGGWWww......",
        ".......WWWWWWWWWWWWWGWWww.......",
        ".......WWWWWWGGWWWWWWWwww.......",
        "........WWWWWGGGWWWWWwww........",
        ".........WWWWWGWWWWwwww.........",
        "..........wwwwwwwwwwww..........",
        "............wwwwwwww............",
    )
    val EGG_PALETTE = mapOf('W' to 0xFFF6F1DC, 'w' to 0xFFD9D2B8, 'L' to 0xFFFFFFFF, 'G' to 0xFF5BC236)

    // ---------- people ----------

    private val HUMAN_BABY: Grid = listOf(
        "..............HHH...............",
        ".............HHHH...............",
        "..........BBBBBBBBBBB...........",
        "........BBBBBBBBBBBBBBB.........",
        ".......BBBBBBBBBBBBBBBBB........",
        "......BBBBBBBBBBBBBBBBBBB.......",
        "......BBBEEEBBBBBBBEEEBBB.......",
        "......BBBEWEBBBBBBBEWEBBB.......",
        "......BBBEEEBBBBBBBEEEBBB.......",
        "......RRBBBBBBBbbBBBBBBRR.......",
        "......RRBBBBBBBBBBBBBBBRR.......",
        ".......BBBBBBMMMMMBBBBBB........",
        ".......bBBBBBBBBBBBBBBBb........",
        "........bbbbbbbbbbbbbbb.........",
        "..........CCCCCCCCCCC...........",
        ".........CCCCCCCCCCCCC..........",
        "........BCCCCCCCCCCCCCB.........",
        "........BCCCCCCCCCCCCCB.........",
        ".........CCCCCCCCCCCCC..........",
        ".........cccccccccccc...........",
        "..........BBB....BBB............",
        "..........FFF....FFF............",
    )

    /** Jun: big curly hair, sunglasses, wide grin. */
    private val HEAD_JUN: Grid = listOf(
        "..........HHHHHHHHHHHH..........",
        ".......HHHHHHHHHHHHHHHHHH.......",
        ".....HHHHHHHHHHHHHHHHHHHHHH.....",
        "....HHHhHHHHHHHHHHHHHHHhHHH.....",
        "...HHHHhhBBBBBBBBBBBBhhHHHH.....",
        "...HHHhhBBBBBBBBBBBBBBhhHHH.....",
        "..HHHhBBBBBBBBBBBBBBBBBBhHHH....",
        "..HHHhBKKKKKKBBBBKKKKKKBhHHH....",
        "..HHhBBKKWKKKBBBBKKWKKKBBhHH....",
        "..HHhBBKKKKKKBBBBKKKKKKBBhHH....",
        "..HHhBBBKKKKBBBBBBKKKKBBBhHH....",
        "...HhBBBBBBBBBbbBBBBBBBBBhH.....",
        "...HhBBBBBBBBBBBBBBBBBBBBhH.....",
        "....hBBBMMMMMMMMMMMMBBBBh.......",
        "....hBBBMWWWWWWWWWWMBBBBh.......",
        ".....BBBBMMMMMMMMMMBBBBb........",
        ".....bBBBBBBBBBBBBBBBBbb........",
        "......bbBBBBBBBBBBBBbb..........",
        "........bbbbbbbbbbbb............",
        "...........BBBBBBBB.............",
    )

    /** Jason: straw hat with a band, glasses, full beard. */
    private val HEAD_JASON: Grid = listOf(
        "............YYYYYYYY............",
        "..........YYYYYYYYYYYY..........",
        ".........YYYYYYYYYYYYYY.........",
        ".........YYYYYYYYYYYYYY.........",
        ".........OOOOOOOOOOOOOO.........",
        "...YYYYYYYYYYYYYYYYYYYYYYYYYY...",
        "..yyyyyyyyyyyyyyyyyyyyyyyyyyyy..",
        "......hHBBBBBBBBBBBBBBBBHh......",
        ".....hBBBBBBBBBBBBBBBBBBBBh.....",
        ".....BBKKKKKKBBBBBBKKKKKKBB.....",
        ".....BBKWWWEKBBBBBBKWWWEKBB.....",
        ".....BBKWWWWKKKKKKKKWWWWKBB.....",
        ".....BBKKKKKKBBBBBBKKKKKKBB.....",
        ".....BBBBBBBBBbbBBBBBBBBBBB.....",
        ".....DBBBBBBBBBBBBBBBBBBBBD.....",
        ".....DDBBBMMMMMMMMMMBBBBDD......",
        ".....DDDBBMWWWWWWWWMBBDDD.......",
        "......DDDDDMMMMMMMMDDDDD........",
        ".......dDDDDDDDDDDDDDDd.........",
        "........ddddddddddddd...........",
        "...........BBBBBBBB.............",
    )

    /** Kona: red bob, glasses, plaid scarf. */
    private val HEAD_KONA: Grid = listOf(
        "..........HHHHHHHHHHHH..........",
        "........HHHHHHHHHHHHHHHH........",
        ".......HHHHHHHHHHHHHHHHHH.......",
        "......HHHHHHHHHHHHHHHHHHHH......",
        ".....HHHHHHhhhhhhhhhhHHHHHH.....",
        ".....HHHHhBBBBBBBBBBBBhHHHH.....",
        ".....HHHhBBBBBBBBBBBBBBhHHH.....",
        "....HHHhBBBBBBBBBBBBBBBBhHHH....",
        "....HHHhBKKKKKBBBBBBKKKKKBhHHH..",
        "....HHHhBKWWEKBBBBBBKWWEKBhHHH..",
        "....HHHhBKWWWKKKKKKKKWWWKBhHHH..",
        "....HHHhBKKKKKBBBBBBKKKKKBhHHH..",
        "....HHHhBBBBBBBBbbBBBBBBBBhHHH..",
        "....HHhBBBBBBBBBBBBBBBBBBBhHHH..",
        "....HHhBBBBBBMMMMMMBBBBBBBhHHH..",
        "....HHhBBBBBBBMMMMBBBBBBBBhHHH..",
        "....HHhhBBBBBBBBBBBBBBBBBhhHHH..",
        "....HHHHhbBBBBBBBBBBBBbhHHHH....",
        ".....HHHHhhbbbbbbbbbbbbhhHHHH...",
        "......HHHH..XXXXXXXXXX..HHHH....",
        ".........XXXXxxXXXXxxXXXX.......",
    )

    private val BODY_KID: Grid = listOf(
        "..........CCCCCCCCCCCC..........",
        ".......CCCCCCCCCCCCCCCCCC.......",
        ".....CCCCCCCCCCCCCCCCCCCCCC.....",
        "....CCCcCCCCCPPPPPPCCCCCcCCC....",
        "....BBBcCCCCCPppppPCCCCCcBBB....",
        "....bBBcCCCCCCCCCCCCCCCCcBBb....",
        ".....bb.cccccccccccccccc.bb.....",
        "........LLLLLLLLLLLLLLLL........",
        "........LLLLLLLLLLLLLLLL........",
        "........LLLLLL....LLLLLL........",
        "........lLLLLl....lLLLLl........",
        ".......FFFFFFF....FFFFFFF.......",
        ".......FFFFFFF....FFFFFFF.......",
        ".......fffffff....fffffff.......",
    )

    private val BODY_ADULT: Grid = listOf(
        "..........CCCCCCCCCCCC..........",
        ".......CCCCCCCCCCCCCCCCCC.......",
        ".....CCCCCCCCCCCCCCCCCCCCCC.....",
        "....CCCCCCCCCCCCCCCCCCCCCCCC....",
        "...CCCcCCCCCCCCCCCCCCCCcCCC.....",
        "...CCCcCCCCCCPPPPPPCCCCCCcCCC...",
        "...CCCcCCCCCCPppppPCCCCCCcCCC...",
        "...BBBcCCCCCCPPPPPPCCCCCCcBBB...",
        "...bBBcCCCCCCCCCCCCCCCCCCcBBb...",
        "....bb.CCCCCCCCCCCCCCCCCC.bb....",
        ".......cccccccccccccccccc.......",
        ".......LLLLLLLLLLLLLLLLLL.......",
        ".......LLLLLLLLLLLLLLLLLL.......",
        ".......LLLLLLLlllLLLLLLLL.......",
        ".......LLLLLLL...LLLLLLLL.......",
        ".......LLLLLLL...LLLLLLLL.......",
        ".......lLLLLLl...lLLLLLLl.......",
        "......FFFFFFFF...FFFFFFFFF......",
        "......FFFFFFFF...FFFFFFFFF......",
        "......ffffffff...fffffffff......",
    )

    /** Old age leans on a walking cane held in the right hand. */
    private val BODY_OLD: Grid = BODY_ADULT.mapIndexed { i, row ->
        when {
            i == 6 -> row.substring(0, 29) + "SS" + row.substring(31)
            i >= 7 -> row.substring(0, 30) + "S" + row.substring(31)
            else -> row
        }
    }

    private fun head(s: Species): Grid = when (s) {
        Species.JASON -> HEAD_JASON
        Species.KONA -> HEAD_KONA
        else -> HEAD_JUN
    }

    // ---------- dogs: a rough collie in three-quarter view, head left, tail up right ----------

    private val DOG: Grid = listOf(
        "....AAA...........AAA...........",
        "...AAAAA.........AAAAA..........",
        "...AAAAAA.......AAAAAA..........",
        "...AABBBBBBBBBBBBBBBAA..........",
        "...AABBBBBBWWWBBBBBBAA..........",
        "....BBBBBBBWWWBBBBBBB...........",
        "....BBBEEBBWWWBBEEBBB.........TT",
        "....BBBEEBBWWWBBEEBBB........TTT",
        "....BBBBBBGWWWGBBBBBB.......TTTT",
        ".....BBBBGGUUUGGBBBB.......TTTTT",
        ".....BBBGGUUUUUGGBBBBBBBBBBTTTTT",
        "......BBGUUNNUUGBBBBBBBBBBBTTTT.",
        "......BWWUUUUUUWWBBBBBBBBBBBTTT.",
        ".....WWWWWUMMUWWWWBBBBBBBBBBbTT.",
        "....WWWWWWWWWWWWWWWBBBBBBBBBbbT.",
        "....WWWWWWWWWWWWWWWWBBBBBBBBbb..",
        "....WWWWWWWWWWWWWWWWBBBBBBBbbb..",
        ".....WWWWWWWWWWWWWWWBBBBBBBbbb..",
        ".....wWWWWWWWWWWWWWWBBBBBBbbbb..",
        "......wwWWWWWWWWWWWbBBBBBbbbb...",
        ".......wwwWWWWWWWwwbbbbbbbbbb...",
        "........WWW.WWWW...bbbb..bbbb...",
        "........WWW.WWWW...WWWW..WWWW...",
        "........WWW.WWWW...WWWW..WWWW...",
        "........WWW.WWWW...WWWW..WWWW...",
        "........www.wwww...wwww..wwww...",
    )

    /** A puppy is all head: the collie's face on a little white chest. */
    private val DOG_PUP: Grid = DOG.take(14).map { it.substring(0, 21) + "..........." } + listOf(
        "....WWWWWWWWWWWWWWW.............",
        ".....WWWWWWWWWWWWW..............",
        "......WWW.....WWW...............",
        "......www.....www...............",
    )

    /** Shorter legs for the kid. */
    private val DOG_KID: Grid = DOG.take(23) + DOG.takeLast(1)

    // ---------- cats: sitting, three-quarter view, tail curled up right ----------

    private val CAT: Grid = listOf(
        "....AA................AA........",
        "...AAAA..............AAAA.......",
        "...ABBAA............AABBA.......",
        "...ABBBBBBBBBBBBBBBBBBBBA.......",
        "...BBBBBHHHHHHHHHHHHBBBBB.......",
        "...BBBBBBBHHHHHHHHBBBBBBB.......",
        "...BBBEEEBBBBBBBBBBEEEBBB.......",
        "...BBBEWEBBBBBBBBBBEWEBBB.......",
        "...BBBEEEBBBBBBBBBBEEEBBB.......",
        "K..BBBBBBBBWWNNWWBBBBBBBB.....K.",
        "KKKBBBBBBBWWWWWWWWBBBBBBBKKK....",
        "...bBBBBBBWWWMMWWWBBBBBBb.......",
        "....bBBBBBBWWWWWWBBBBBBb........",
        ".....bBBBBBWWWWWWBBBBBb.........",
        "......BBBBWWWWWWWWBBBBBBBB....TT",
        ".....BBBBWWWWWWWWWWBBBBBBBBB.TTT",
        ".....BBBBWWWWWWWWWWBBBBBBBBBTTTT",
        ".....BBBBWWWWWWWWWWBBBBBBBBBTTT.",
        ".....BBBBWWWWWWWWWWBBBBBBBbbTT..",
        ".....bBBBWWWWWWWWWBBBBBBBbbb....",
        "......bbbWWWWWWWWbbbbbbbbbb.....",
        ".......WWW...WWW...bbb..bbb.....",
        ".......WWW...WWW...WWW..WWW.....",
        ".......www...www...www..www.....",
    )

    private val CAT_KITTEN: Grid = CAT.take(14) + listOf(
        "......WWWWWWWWWWWWW.............",
        ".......WWW.....WWW..............",
        ".......www.....www..............",
    )

    private val CAT_KID: Grid = CAT.take(22) + CAT.takeLast(1)

    fun body(species: Species, stage: Stage): Grid = when (species.kind) {
        Kind.HUMAN -> when (stage) {
            Stage.EGG -> EGG
            Stage.BABY -> HUMAN_BABY
            Stage.KID -> head(species) + BODY_KID
            Stage.ADULT -> head(species) + BODY_ADULT
            Stage.OLD -> head(species) + BODY_OLD
        }
        Kind.DOG -> when (stage) {
            Stage.EGG -> EGG
            Stage.BABY -> DOG_PUP
            Stage.KID -> DOG_KID
            Stage.ADULT, Stage.OLD -> DOG
        }
        Kind.CAT -> when (stage) {
            Stage.EGG -> EGG
            Stage.BABY -> CAT_KITTEN
            Stage.KID -> CAT_KID
            Stage.ADULT, Stage.OLD -> CAT
        }
    }

    fun palette(species: Species, stage: Stage) =
        if (stage == Stage.EGG) EGG_PALETTE else Palettes.of(species, stage)

    // ---------- props (small; drawn doubled with x2()) ----------
    val POOP: Grid = listOf(
        "...P....",
        "..PP....",
        "..PPP...",
        ".PPPPP..",
        "PPPPPPP.",
    )
    val POOP_PALETTE = mapOf('P' to 0xFF7A4A24)

    val SKULL: Grid = listOf(
        ".WWWW.",
        "WKWWKW",
        "WWWWWW",
        ".WKKW.",
    )
    val SKULL_PALETTE = mapOf('W' to 0xFFFFFFFF, 'K' to 0xFF1B1B1B)

    val MEAL: Grid = listOf(
        "..WWWW..",
        ".WWWWWW.",
        "BBBBBBBB",
        ".BBBBBB.",
        "..BBBB..",
    )
    val MEAL_PALETTE = mapOf('W' to 0xFFFFFFFF, 'B' to 0xFF3C7DD9)

    val SNACK: Grid = listOf(
        "..PPPP..",
        ".PWPPWP.",
        "PPPPPPPP",
        "YYYYYYYY",
        "YYYYYYYY",
    )
    val SNACK_PALETTE = mapOf('P' to 0xFFF27AB0, 'W' to 0xFFFFFFFF, 'Y' to 0xFFF2D06B)

    val PILL: Grid = listOf(
        ".RRWW.",
        "RRRWWW",
        ".RRWW.",
    )
    val PILL_PALETTE = mapOf('R' to 0xFFE04444, 'W' to 0xFFFFFFFF)

    val HEART: Grid = listOf(
        ".RR.RR.",
        "RRRRRRR",
        "RRRRRRR",
        ".RRRRR.",
        "..RRR..",
        "...R...",
    )
    val HEART_PALETTE = mapOf('R' to 0xFFE0344B, 'K' to 0xFF3A3A3A)

    /** Grows where a pet returned to the garden. */
    val SPROUT: Grid = listOf(
        ".GG...GG.",
        "GGGG.GGGG",
        ".GGG.GGG.",
        "...GGG...",
        "....G....",
        "....G....",
        "..DDDDD..",
        ".DDDDDDD.",
    )
    val SPROUT_PALETTE = mapOf('G' to 0xFF5BC236, 'D' to 0xFF7A4A24)

    /** Worn the day before each birthday. */
    val PARTY_HAT: Grid = listOf(
        "...W...",
        "...R...",
        "..RYR..",
        "..YRY..",
        ".RYRYR.",
        "YRYRYRY",
    )
    val PARTY_HAT_PALETTE = mapOf('W' to 0xFFFFFFFF, 'R' to 0xFFE0344B, 'Y' to 0xFFFFD84A)

    val STAR: Grid = listOf(
        "..Y..",
        ".YYY.",
        "YYYYY",
        ".YYY.",
        ".Y.Y.",
    )
    val STAR_PALETTE = mapOf('Y' to 0xFFFFD84A)

    /** A raised garden bed with a seedling, for the gardening idle animation (4 growth frames). */
    val BED: Grid = listOf(
        "DDDDDDDDDDDDDD",
        "DdddddddddddDD",
        "DdddddddddddDD",
        "DDDDDDDDDDDDDD",
    )
    val BED_PALETTE = mapOf('D' to 0xFF8A5A2B, 'd' to 0xFF5C3A1A)
    val SEEDLING: List<Grid> = listOf(
        listOf("..", ".."),
        listOf(".G", ".G"),
        listOf("GG", ".G", ".G"),
        listOf("G.G", "GGG", ".G.", ".G."),
    )
    val SEEDLING_PALETTE = mapOf('G' to 0xFF5BC236)
    val TROWEL: Grid = listOf(
        "..H",
        ".H.",
        "M..",
        "M..",
    )
    val TROWEL_PALETTE = mapOf('H' to 0xFF7A4A24, 'M' to 0xFFBFC4CC)
}

/**
 * Menu icons, 16x16, in the same style as the characters: solid shapes in two or three tones
 * (lower-case = shadow, L = highlight), drawn with the shaded outline. Order matches [MenuItem].
 */
object Icons {
    private val BLANK = "................"

    private val STATUS = listOf(
        BLANK,
        "..RRRR....RRRR..",
        ".RRWWRR..RRRRRR.",
        "RRWWRRRRRRRRRRRr",
        "RRWRRRRRRRRRRRRr",
        "RRRRRRRRRRRRRRRr",
        "RRRRRRRRRRRRRRrr",
        ".RRRRRRRRRRRRrr.",
        "..RRRRRRRRRRrr..",
        "...RRRRRRRRrr...",
        "....RRRRRRrr....",
        ".....RRRRrr.....",
        "......RRrr......",
        ".......rr.......",
        BLANK, BLANK,
    )
    /** A bowl of kibble, steaming. */
    private val FEED = listOf(
        BLANK,
        ".....w...w......",
        "....w...w.......",
        ".....w...w......",
        BLANK,
        "...FFFFFFFFFF...",
        "..FfFFFfFFFfFF..",
        ".FFFFFFFFFFFFFF.",
        "BBBBBBBBBBBBBBBB",
        "BLLBBBBBBBBBBBBb",
        ".BLBBBBBBBBBBBb.",
        "..BBBBBBBBBBBb..",
        "...bbbbbbbbbb...",
        BLANK, BLANK, BLANK,
    )
    private val LIGHT = listOf(
        BLANK,
        ".....YYYYYY.....",
        "....YYWWYYYY....",
        "...YYWWYYYYYY...",
        "...YYWYYYYYYy...",
        "...YYYYYYYYYy...",
        "...YYYYYYYYyy...",
        "....YYYYYYyy....",
        ".....YYYYyy.....",
        "......MMMM......",
        "......mmmm......",
        "......MMMM......",
        ".......mm.......",
        BLANK, BLANK, BLANK,
    )
    /** A beach ball. */
    private val PLAY = listOf(
        BLANK, BLANK,
        "....RRRRWWWW....",
        "...RRRRRWWWWB...",
        "..RRRRRRWWWBBB..",
        "..RWRRRRWWWBBB..",
        ".RRRRRRYYWWBBBB.",
        ".RRRRRYYYYWBBBB.",
        ".rRRRRYYYYBBBBb.",
        ".rrRRRRYYBBBBbb.",
        "..rrRRRGGGBBbb..",
        "..rrrRRGGGBbbb..",
        "...rrrrGGGbbb...",
        "....rrrrggbb....",
        BLANK, BLANK,
    )
    /** A medicine bottle with a red cross. */
    private val MEDICINE = listOf(
        BLANK, BLANK,
        "......CCCC......",
        "......cccc......",
        ".....WWWWWW.....",
        "....WWWWWWWW....",
        "....WLWWWWWW....",
        "....WLWRRWWw....",
        "....WLRRRRWw....",
        "....WLRRRRWw....",
        "....WLWRRWWw....",
        "....WWWWWWWw....",
        "....WWWWWWww....",
        "....wwwwwwww....",
        BLANK, BLANK,
    )
    /** The classic bath duck, bobbing in the water. */
    private val CLEAN = listOf(
        BLANK, BLANK,
        "......YYYY......",
        ".....YYYYYY.....",
        ".....YYKYYY.....",
        ".....YYYYYYOO...",
        "......YYYYOO....",
        "..Y...YYYY......",
        "..YY.YYYYYYY....",
        "..YYYYYWWYYYY...",
        "..YYYYWWWYYYYy..",
        "...YYYYYYYYYyy..",
        "....yYYYYYYyy...",
        ".....yyyyyyy....",
        "..BBBBBBBBBBBB..",
        ".BbBBBbBBBbBBBb.",
    )
    /** A sprout in a clay pot. */
    private val GARDEN = listOf(
        BLANK, BLANK,
        "....GG....GG....",
        "...GGGG..GGGG...",
        "...GgGGG.GGgG...",
        "....GgGG.GGg....",
        "......GGGG......",
        ".......GG.......",
        ".......GG.......",
        "....OOOOOOOO....",
        "....OoOOOOOO....",
        ".....OOOOOOo....",
        ".....OOOOOOo....",
        "......oooo......",
        BLANK, BLANK,
    )
    private val ATTENTION = listOf(
        BLANK, BLANK,
        ".......RR.......",
        "......RRRR......",
        "......RRRR......",
        "......RRRR......",
        "......RRRr......",
        ".......RR.......",
        ".......RR.......",
        ".......rr.......",
        BLANK,
        ".......RR.......",
        "......RRRR......",
        ".......rr.......",
        BLANK, BLANK,
    )

    val ALL = listOf(STATUS, FEED, LIGHT, PLAY, MEDICINE, CLEAN, GARDEN, ATTENTION)
    val PALETTE = mapOf(
        'R' to 0xFFE0344B, 'r' to 0xFFA01E30, 'W' to 0xFFFFFFFF, 'w' to 0xFFDCE6F2, 'L' to 0xFFBFE4FF,
        'F' to 0xFF8A5A2B, 'f' to 0xFF5C3A1A, 'B' to 0xFF3C9BE0, 'b' to 0xFF2A6FB0,
        'Y' to 0xFFFFD84A, 'y' to 0xFFC9A93A, 'M' to 0xFFBFC4CC, 'm' to 0xFF8A8F99,
        'G' to 0xFF5BC236, 'g' to 0xFF3E8F26, 'O' to 0xFFD07A3A, 'o' to 0xFF9A5226,
        'C' to 0xFF3C7DD9, 'c' to 0xFF2A5AA8, 'K' to 0xFF1B1B1B,
    )
}
