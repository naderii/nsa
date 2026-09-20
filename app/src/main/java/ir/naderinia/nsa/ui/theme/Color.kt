package ir.naderinia.nsa.ui.theme

import androidx.compose.ui.graphics.Color

// A deliberately chosen modern palette (indigo + teal + amber) instead of
// relying on Android's wallpaper-derived "Material You" dynamic colors,
// which tend to look muted/inconsistent across devices.

// Light scheme
val LightPrimary = Color(0xFF4F46E5)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE1DFFF)
val LightOnPrimaryContainer = Color(0xFF16137A)

val LightSecondary = Color(0xFF0D9488)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCCF4EE)
val LightOnSecondaryContainer = Color(0xFF00201C)

val LightTertiary = Color(0xFFF59E0B)
val LightOnTertiary = Color(0xFF452B00)
val LightTertiaryContainer = Color(0xFFFFE4B3)
val LightOnTertiaryContainer = Color(0xFF2E1B00)

val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF7F7FB)
val LightOnBackground = Color(0xFF1B1B23)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1B1B23)
val LightSurfaceVariant = Color(0xFFEAE9F5)
val LightOnSurfaceVariant = Color(0xFF47464F)
val LightOutline = Color(0xFF787680)

// "Success/OK" semantic colors — used only for task-urgency color-coding
// (green = plenty of time), separate from the teal secondary used for
// financial credit amounts.
val LightSuccessContainer = Color(0xFFD7F2D0)
val LightOnSuccessContainer = Color(0xFF0C3A08)

// Dark scheme
val DarkPrimary = Color(0xFFBFBBFF)
val DarkOnPrimary = Color(0xFF251E8C)
val DarkPrimaryContainer = Color(0xFF3B34A3)
val DarkOnPrimaryContainer = Color(0xFFE1DFFF)

val DarkSecondary = Color(0xFF5FE9DA)
val DarkOnSecondary = Color(0xFF00382F)
val DarkSecondaryContainer = Color(0xFF005046)
val DarkOnSecondaryContainer = Color(0xFFCCF4EE)

val DarkTertiary = Color(0xFFFFC876)
val DarkOnTertiary = Color(0xFF452B00)
val DarkTertiaryContainer = Color(0xFF614000)
val DarkOnTertiaryContainer = Color(0xFFFFE4B3)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF131318)
val DarkOnBackground = Color(0xFFE4E1E9)
val DarkSurface = Color(0xFF1C1B23)
val DarkOnSurface = Color(0xFFE4E1E9)
val DarkSurfaceVariant = Color(0xFF2C2B36)
val DarkOnSurfaceVariant = Color(0xFFC9C5D0)
val DarkOutline = Color(0xFF928F9A)

val DarkSuccessContainer = Color(0xFF1C4A16)
val DarkOnSuccessContainer = Color(0xFFC0EFB8)

// Category accent colors (used for the colored dot per reminder category)
val CategoryColors = listOf(
    0xFF4F46E5L, // بنفش/آبی - عمومی
    0xFF0D9488L, // سبزآبی - ماشین
    0xFFDC2626L, // قرمز - مالی
    0xFFF59E0BL, // نارنجی - کار
    0xFFDB2777L  // صورتی - شخصی
)

@androidx.compose.runtime.Composable
fun successContainerColor(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) DarkSuccessContainer else LightSuccessContainer

@androidx.compose.runtime.Composable
fun onSuccessContainerColor(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) DarkOnSuccessContainer else LightOnSuccessContainer
