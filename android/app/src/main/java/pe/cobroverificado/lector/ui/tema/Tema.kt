package pe.cobroverificado.lector.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.cobroverificado.lector.R

// identidad propia: verde de sello y ámbar, lejos de los colores de cualquier billetera
private val claro = lightColorScheme(
    primary = Color(0xFF0E6B53), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEADF), onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4D635A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6E0), onSecondaryContainer = Color(0xFF17201C),
    tertiary = Color(0xFF8A5A00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAE), onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF3F5F2), onBackground = Color(0xFF15201C),
    surface = Color(0xFFF3F5F2), onSurface = Color(0xFF15201C),
    surfaceVariant = Color(0xFFE1E7E3), onSurfaceVariant = Color(0xFF4F5D57),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEDF1EE),
    surfaceContainer = Color(0xFFE7ECE9), surfaceContainerHigh = Color(0xFFE1E7E3),
    surfaceContainerHighest = Color(0xFFDBE2DE),
    outline = Color(0xFF6F7D77), outlineVariant = Color(0xFFC9D2CD),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

@Immutable
data class ColoresMarca(
    val sello: Color,
    val sobreSello: Color,
    val sobreSelloSuave: Color,
    val pausa: Color,
    val ambar: Color,
    val avatares: List<Pair<Color, Color>>,
    val yape: Tono,
    val plin: Tono,
    val borde: Color,
    val exito: Tono,
    val alerta: Tono,
    val peligro: Tono,
    val info: Tono,
    val privado: Tono,
)

@Immutable
data class Tono(val fondo: Color, val texto: Color, val solido: Color)

private val marcaClara = ColoresMarca(
    sello = Color(0xFF0E6B53),
    sobreSello = Color.White,
    sobreSelloSuave = Color(0xFFCFEADF),
    pausa = Color(0xFF46534D),
    ambar = Color(0xFFF2C14E),
    avatares = listOf(
        Color(0xFFD7ECE2) to Color(0xFF0B4A3A),
        Color(0xFFFBE5C0) to Color(0xFF5A3A00),
        Color(0xFFDDE3F3) to Color(0xFF26345A),
        Color(0xFFF3DCD6) to Color(0xFF5C2A1E),
        Color(0xFFE6E2D3) to Color(0xFF3F3A26),
    ),
    yape = Tono(Color(0xFFEFE4FA), Color(0xFF4B1D73), Color(0xFF7E4BB0)),
    plin = Tono(Color(0xFFD5F2EE), Color(0xFF0B4D47), Color(0xFF138A7E)),
    borde = Color(0xFFE4E9E6),
    exito = Tono(Color(0xFFD7F0E4), Color(0xFF0B6A50), Color(0xFF0E6B53)),
    alerta = Tono(Color(0xFFFCEBC8), Color(0xFF7A4F00), Color(0xFFB7791F)),
    peligro = Tono(Color(0xFFF9DEDC), Color(0xFFA4241C), Color(0xFFB3261E)),
    info = Tono(Color(0xFFDCE8FB), Color(0xFF1F4F9A), Color(0xFF2F64C0)),
    privado = Tono(Color(0xFFECE3F8), Color(0xFF5B3591), Color(0xFF7A52B3)),
)

val LocalMarca = staticCompositionLocalOf { marcaClara }

@OptIn(ExperimentalTextApi::class)
private fun onest(peso: Int) = Font(
    R.font.onest,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

private val Onest = FontFamily(onest(400), onest(500), onest(600), onest(700))

private fun estilo(tamano: Int, linea: Int, peso: Int, espaciado: Double = 0.0) = TextStyle(
    fontFamily = Onest,
    fontSize = tamano.sp,
    lineHeight = linea.sp,
    fontWeight = FontWeight(peso),
    letterSpacing = espaciado.sp,
)

private val tipografia = Typography(
    displayLarge = estilo(52, 60, 600, -1.0),
    displayMedium = estilo(44, 52, 600, -0.8),
    displaySmall = estilo(36, 44, 600, -0.5),
    headlineLarge = estilo(30, 38, 600, -0.4),
    headlineMedium = estilo(26, 34, 600, -0.3),
    headlineSmall = estilo(22, 30, 600, -0.2),
    titleLarge = estilo(20, 28, 600),
    titleMedium = estilo(16, 22, 600),
    titleSmall = estilo(14, 20, 600),
    bodyLarge = estilo(16, 24, 400),
    bodyMedium = estilo(14, 21, 400),
    bodySmall = estilo(12, 17, 400, 0.1),
    labelLarge = estilo(15, 20, 600),
    labelMedium = estilo(12, 16, 500, 0.1),
    labelSmall = estilo(11, 14, 500, 0.2),
)

val Cifras = TextStyle(fontFeatureSettings = "tnum")

private val formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TemaLector(contenido: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMarca provides marcaClara) {
        MaterialTheme(
            colorScheme = claro,
            typography = tipografia,
            shapes = formas,
            content = contenido,
        )
    }
}
