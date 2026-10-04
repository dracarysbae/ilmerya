package com.bloxtrix.hexdrop.ui.theme

import androidx.compose.ui.graphics.Color

/** Medium anthracite housing with opaque, natural mineral pieces. */
object HexColors {
    val Background = Color(0xFF3D4449)
    val Surface = Color(0xFF424A50)
    val SurfaceHigh = Color(0xFF4A5359)
    val Board = Color(0xFF30383D)
    val Highlight = Color(0xFF839097)
    val Shadow = Color(0xFF161C20)
    val Border = Color(0xFF525E65)
    val BorderBright = Color(0xFF76868C)
    val GridLine = Color(0xFF414D54)
    val EmptyCell = Color(0xFF293137)
    val ColHover = Color(0xFF344449)
    val TextPrimary = Color(0xFFF1F0E9)
    val TextDim = Color(0xFFBAC4C4)
    val Accent = Color(0xFF94CDBD)
    val AccentAlt = Color(0xFFBCE4D7)
    val OnAccent = Color(0xFF172D2B)
    /** Personal-best figures. */
    val Gold = Color(0xFFD98C2B)
    /** Full columns, rejected results and a nearly expired Flow timer. */
    val Danger = Color(0xFFE47A8E)
    val Tier2 = Color(0xFF13714E)      // malachite
    val Tier4 = Color(0xFFAC4329)      // red jasper
    val Tier8 = Color(0xFF36A3AE)      // turquoise (replaces the former yellow stone)
    val Tier16 = Color(0xFF7F3CB5)     // charoite
    val Tier32 = Color(0xFF245FB4)     // lapis lazuli
    val Tier64 = Color(0xFFB2486F)     // rhodonite
    val Tier128 = Color(0xFFCF5A14)    // carnelian
    val Tier256 = Color(0xFFD9D2C4)    // howlite
    val Tier512 = Color(0xFF2B2C31)    // obsidian
    val Tier1024 = Color(0xFFA8701E)   // tiger's eye
    val Tier2048 = Color(0xFF9E1B32)   // ruby zoisite
    fun forValue(value: Int): Color = when (value) {
        2 -> Tier2; 4 -> Tier4; 8 -> Tier8; 16 -> Tier16; 32 -> Tier32
        64 -> Tier64; 128 -> Tier128; 256 -> Tier256; 512 -> Tier512
        1024 -> Tier1024; else -> Tier2048
    }
    // Ivory inlays on dark minerals; charcoal inlays on light minerals.
    fun textForValue(value: Int) = when (value) {
        8, 256 -> Color(0xFF141D19)
        else -> Color(0xFFFFF8E8)
    }
}
