package com.sproutos.store

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SproutInk = Color(0xFF0F1E17)
val SproutCanvas = Color(0xFFF4F7F1)
val SproutFrame = Color(0xFFD6DED0)
val SproutLeaf = Color(0xFF1D6B3E)
val SproutGrowth = Color(0xFF8ECB4F)
val SproutRust = Color(0xFF8A2E1C)

private val LightColors =
    lightColorScheme(
        primary = SproutLeaf,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEFD7),
        onPrimaryContainer = SproutInk,
        secondary = SproutLeaf,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCEFD7),
        onSecondaryContainer = SproutInk,
        tertiary = SproutInk,
        tertiaryContainer = Color(0xFFE9EEE5),
        onTertiaryContainer = SproutInk,
        background = SproutCanvas,
        onBackground = SproutInk,
        surface = SproutCanvas,
        onSurface = SproutInk,
        surfaceVariant = Color(0xFFE9EEE5),
        onSurfaceVariant = Color(0xFF47534C),
        outline = Color(0xFF6D786F),
        outlineVariant = SproutFrame,
        error = SproutRust,
        onError = Color.White,
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF6FBF84),
        onPrimary = Color(0xFF082113),
        primaryContainer = Color(0xFF214D32),
        onPrimaryContainer = Color(0xFFDCEFD7),
        secondary = Color(0xFF6FBF84),
        secondaryContainer = Color(0xFF214D32),
        onSecondaryContainer = Color(0xFFDCEFD7),
        tertiary = Color(0xFFE6EDE6),
        tertiaryContainer = Color(0xFF182A20),
        onTertiaryContainer = Color(0xFFE6EDE6),
        background = Color(0xFF0F1E17),
        onBackground = Color(0xFFE6EDE6),
        surface = Color(0xFF0F1E17),
        onSurface = Color(0xFFE6EDE6),
        surfaceVariant = Color(0xFF182A20),
        onSurfaceVariant = Color(0xFFBBC8BC),
        outline = Color(0xFF91A095),
        outlineVariant = Color(0xFF2A3B32),
        error = Color(0xFFE38A75),
        onError = Color(0xFF35100A),
    )

private val SproutTypography =
    Typography(
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )

val SproutMono =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

@Composable
fun SproutTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility =
                    if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = SproutTypography,
        shapes =
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ),
        content = content,
    )
}
