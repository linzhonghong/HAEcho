package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SophisticatedDarkColorScheme = darkColorScheme(
  primary = Color(0xFFD0BCFF),        // M3 Lavender Gold neon accent
  onPrimary = Color(0xFF381E72),
  primaryContainer = Color(0xFF4F378B),
  onPrimaryContainer = Color(0xFFEADDFF),
  secondary = Color(0xFFCCC2DC),
  onSecondary = Color(0xFF332D41),
  background = Color(0xFF1C1B1F),     // Elegant rich dark background body
  onBackground = Color(0xFFE6E1E5),   // Crisp light content
  surface = Color(0xFF2B2930),        // Premium elevated surface card
  onSurface = Color(0xFFE6E1E5),
  surfaceVariant = Color(0xFF38353F), // Mid ground card level
  onSurfaceVariant = Color(0xFFCAC4D0),
  outline = Color(0xFF49454F),        // Fine slate dividers & borders
  error = Color(0xFFF2B8B5),
  onError = Color(0xFF601410),
  errorContainer = Color(0xFF8C1D18),
  onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for Sophisticated Dark mode
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve exact brand color design
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = SophisticatedDarkColorScheme,
    typography = Typography,
    content = content
  )
}

