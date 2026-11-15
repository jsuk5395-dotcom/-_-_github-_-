package com.example.pnu_me.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4F5F6),
    onPrimary = Color(0xFF121416),
    primaryContainer = Color(0xFF2A2F34),
    onPrimaryContainer = Color(0xFFF4F5F6),
    secondary = Color(0xFFC4C8CC),
    onSecondary = Color(0xFF171A1D),
    secondaryContainer = Color(0xFF25292D),
    onSecondaryContainer = Color(0xFFE5E7E9),
    tertiary = Color(0xFF9AB8FF),
    onTertiary = Color(0xFF0B2B63),
    tertiaryContainer = Color(0xFF183D77),
    onTertiaryContainer = Color(0xFFDCE8FF),
    background = Color(0xFF0B0D0F),
    onBackground = Color(0xFFE8EAEC),
    surface = CockpitSurfaceDark,
    onSurface = Color(0xFFE8EAEC),
    surfaceVariant = Color(0xFF22262A),
    onSurfaceVariant = Color(0xFFB9BEC3),
    surfaceDim = Color(0xFF090B0D),
    surfaceBright = Color(0xFF30353A),
    surfaceContainerLowest = Color(0xFF080A0C),
    surfaceContainerLow = Color(0xFF121517),
    surfaceContainer = Color(0xFF181B1E),
    surfaceContainerHigh = Color(0xFF202428),
    surfaceContainerHighest = Color(0xFF292E33),
    outline = CockpitOutlineDark,
    outlineVariant = Color(0xFF30353A),
    inverseSurface = Color(0xFFE9EAEC),
    inverseOnSurface = Color(0xFF202326),
    error = CockpitErrorDark,
    surfaceTint = Color.Transparent,
)

private val LightColorScheme = lightColorScheme(
    primary = CockpitBlack,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E4E7),
    onPrimaryContainer = CockpitBlack,
    secondary = CockpitGrey,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E9EB),
    onSecondaryContainer = Color(0xFF282B2E),
    tertiary = CockpitBlue,
    onTertiary = Color.White,
    tertiaryContainer = CockpitBlueLight,
    onTertiaryContainer = Color(0xFF0B2B63),
    background = CockpitSurface,
    onBackground = CockpitBlack,
    surface = CockpitWhite,
    onSurface = CockpitBlack,
    surfaceVariant = Color(0xFFE5E8EB),
    onSurfaceVariant = Color(0xFF555B61),
    surfaceDim = Color(0xFFD8DCE0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6F7),
    surfaceContainer = Color(0xFFEEF0F2),
    surfaceContainerHigh = Color(0xFFE7EAED),
    surfaceContainerHighest = Color(0xFFDDE1E5),
    outline = CockpitOutline,
    outlineVariant = Color(0xFFD3D7DB),
    inverseSurface = Color(0xFF24272A),
    inverseOnSurface = Color(0xFFF4F5F6),
    error = CockpitError,
    surfaceTint = Color.Transparent,
)

@Composable
fun Pnu_meTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
