package com.vidgravity.app

import androidx.compose.ui.graphics.Color

/** Centralized VidGravity multicolor design tokens. Foundations stay Soft Slate; accents are semantic. */
internal object VidGravityPalette {
    val mutedTeal = Color(0xFF38B8B2)
    val creativeViolet = Color(0xFF7D5CFF)
    val softBlue = Color(0xFF6698E8)
    val mint = Color(0xFF65BFA2)
    val warmAmber = Color(0xFFE7A66A)
    val softRose = Color(0xFFDE8792)
    val coral = Color(0xFFD98D78)
    val periwinkle = Color(0xFF8B9BC9)
    val lavender = Color(0xFFA78BFA)
    val orchid = Color(0xFFC084FC)
    val softFuchsia = Color(0xFFE879F9)
    val blushPink = Color(0xFFF0A6CA)
    val dustyRose = Color(0xFFE6A4A1)
    val goldenHoney = Color(0xFFE9B949)
    val champagneGold = Color(0xFFC9A66B)
    val sage = Color(0xFFA3B86C)
    val seafoam = Color(0xFF78B89A)
    val oceanCyan = Color(0xFF5EB7D1)
    val cornflower = Color(0xFF86A8E7)
    val silverBlue = Color(0xFF9AA6B2)

    // Semantic foregrounds for the core brand accents; use slate/white only where contrast permits.
    val darkText = Color(0xFF172126)
    val lightText = Color(0xFFF7FAFA)
    val darkFoundation = Color(0xFF171F23)
    val darkSurface = Color(0xFF202B30)
    val darkElevated = Color(0xFF29373D)
    val darkPrimaryText = Color(0xFFE6EEF0)
    val darkSecondaryText = Color(0xFFAFBEC3)
    val darkBorder = Color(0xFF38484E)
    val lightFoundation = Color(0xFFE8EEED)
    val lightSurface = Color(0xFFF1F5F4)
    val lightElevated = Color(0xFFF7F9F8)
    val lightPrimaryText = Color(0xFF26363B)
    val lightSecondaryText = Color(0xFF58696D)
    val lightBorder = Color(0xFFCCDAD7)
}

internal enum class VgAccentPreset(
    val title: String,
    val secondary: Color,
    val tertiary: Color,
    val semantic: Color
) {
    OceanBreeze("Ocean Breeze", VidGravityPalette.oceanCyan, VidGravityPalette.softBlue, VidGravityPalette.mint),
    AuroraViolet("Aurora Violet", VidGravityPalette.lavender, VidGravityPalette.periwinkle, VidGravityPalette.orchid),
    SunsetStudio("Sunset Studio", VidGravityPalette.warmAmber, VidGravityPalette.softRose, VidGravityPalette.creativeViolet),
    SageCalm("Sage Calm", VidGravityPalette.seafoam, VidGravityPalette.sage, VidGravityPalette.mutedTeal),
    MidnightLuxe("Midnight Luxe", VidGravityPalette.champagneGold, VidGravityPalette.creativeViolet, VidGravityPalette.silverBlue),
    VidGravity("VidGravity", VidGravityPalette.mutedTeal, VidGravityPalette.creativeViolet, VidGravityPalette.mint);

    companion object {
        fun fromStored(value: String?): VgAccentPreset =
            entries.firstOrNull { it.name == value } ?: VidGravity
    }
}
