package com.offlinepay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GlacierBlue = Color(0xFF5FCDFF)
val WealthBeige = Color(0xFFFFFBE7)
val TrustIndigo = Color(0xFF081A31)
val YieldBlue = Color(0xFF0E86EE)
val CarbonCredit = Color(0xFF17181D)
val SoftBlue = Color(0xFFE7F5FF)
val MutedNavy = Color(0xFF506987)
val LineBlue = Color(0xFFD7E5EF)
val PaleNavy = Color(0xFF122746)

private val OfflinePayColors = lightColorScheme(
    primary = YieldBlue,
    onPrimary = Color.White,
    secondary = GlacierBlue,
    background = WealthBeige,
    onBackground = TrustIndigo,
    surface = Color(0xFFFFFEFA),
    onSurface = TrustIndigo,
    outline = LineBlue
)

@Composable
fun OfflinePayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OfflinePayColors,
        content = content
    )
}
