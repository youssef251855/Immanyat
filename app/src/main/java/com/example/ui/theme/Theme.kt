package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimary,
    secondary = EmeraldSecondary,
    tertiary = GoldAccent,
    background = DarkBackground,
    surface = SurfaceDarkGlass,
    onPrimary = LightWhite,
    onSecondary = LightWhite,
    onBackground = TextColorPrimary,
    onSurface = TextColorPrimary,
  )

private val LightColorScheme =
  darkColorScheme( // We provide Emerald slate as default even for light mode to maintain spiritual aesthetic as instructed
    primary = EmeraldPrimary,
    secondary = EmeraldSecondary,
    tertiary = GoldAccent,
    background = DarkBackground,
    surface = SurfaceDarkGlass,
    onPrimary = LightWhite,
    onSecondary = LightWhite,
    onBackground = TextColorPrimary,
    onSurface = TextColorPrimary,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // default to true for spiritual Dark mode
  dynamicColor: Boolean = false, // enforce brand emerald identity
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
