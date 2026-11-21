package com.sandeep.imghunt.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ImgColors {
    val VoidBg = Color(0xFF0F0F12)
    val SurfaceCard = Color(0xFF17171C)
    val SurfaceElevated = Color(0xFF202028)
    val AccentViolet = Color(0xFF7C5CFF)
    val AccentSoft = Color(0xFF9C84FF)
    val OnAccent = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFF3F3F7)
    val TextMuted = Color(0xFF9A9AA8)
    val BorderSubtle = Color(0xFF2B2B36)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
}

private val DarkScheme = darkColorScheme(
    primary = ImgColors.AccentViolet,
    onPrimary = ImgColors.OnAccent,
    secondary = ImgColors.AccentSoft,
    background = ImgColors.VoidBg,
    onBackground = ImgColors.TextPrimary,
    surface = ImgColors.VoidBg,
    onSurface = ImgColors.TextPrimary,
    surfaceVariant = ImgColors.SurfaceCard,
    onSurfaceVariant = ImgColors.TextMuted,
    outline = ImgColors.BorderSubtle
)

@Composable
fun ImgHuntTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
