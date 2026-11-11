package pe.cobroverificado.lector.ui.inicio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import pe.cobroverificado.lector.ui.tema.LocalMarca
import pe.cobroverificado.lector.ui.tema.dibujarSello

// las ilustraciones se dibujan en una caja de 280x200 unidades y se escalan al espacio disponible
private const val ANCHO = 280f
private const val ALTO = 200f

@Composable
fun IlustracionAviso(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val marca = LocalMarca.current
    val sello = remember { Animatable(1.6f) }
    LaunchedEffect(Unit) { sello.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Canvas(modifier.semantics { contentDescription = "Un aviso de pago en el teléfono queda sellado como registro" }) {
        val e = minOf(size.width / ANCHO, size.height / ALTO)
        val origen = Offset((size.width - ANCHO * e) / 2, (size.height - ALTO * e) / 2)
        fun p(x: Float, y: Float) = Offset(origen.x + x * e, origen.y + y * e)
        drawRoundRect(c.surfaceContainerLowest, p(85f, 8f), Size(110 * e, 190 * e), CornerRadius(22 * e))
        drawRoundRect(c.outlineVariant, p(85f, 8f), Size(110 * e, 190 * e), CornerRadius(22 * e), style = Stroke(2 * e))
        drawRoundRect(c.surfaceContainerHigh, p(40f, 52f), Size(200 * e, 58 * e), CornerRadius(14 * e))
        drawCircle(c.primary, 12 * e, p(64f, 81f))
        drawRoundRect(c.onSurfaceVariant.copy(alpha = 0.55f), p(86f, 70f), Size(96 * e, 9 * e), CornerRadius(5 * e))
        drawRoundRect(c.onSurfaceVariant.copy(alpha = 0.3f), p(86f, 87f), Size(130 * e, 8 * e), CornerRadius(4 * e))
        drawRoundRect(c.onSurfaceVariant.copy(alpha = 0.18f), p(100f, 128f), Size(80 * e, 8 * e), CornerRadius(4 * e))
        drawRoundRect(c.onSurfaceVariant.copy(alpha = 0.18f), p(100f, 146f), Size(60 * e, 8 * e), CornerRadius(4 * e))
        scale(sello.value, pivot = p(206f, 150f)) {
            drawCircle(marca.sello, 34 * e, p(206f, 150f))
            dibujarSello(p(206f, 150f), 18 * e, marca.sobreSello, marca.ambar)
        }
    }
}

@Composable
fun IlustracionAcceso(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.semantics { contentDescription = "Lista de permisos con el acceso de esta app activado" }) {
        val e = minOf(size.width / ANCHO, size.height / ALTO)
        val origen = Offset((size.width - ANCHO * e) / 2, (size.height - ALTO * e) / 2)
        fun p(x: Float, y: Float) = Offset(origen.x + x * e, origen.y + y * e)
        drawRoundRect(c.surfaceContainerLowest, p(30f, 14f), Size(220 * e, 172 * e), CornerRadius(20 * e))
        drawRoundRect(c.outlineVariant, p(30f, 14f), Size(220 * e, 172 * e), CornerRadius(20 * e), style = Stroke(2 * e))
        listOf(46f, 92f, 138f).forEachIndexed { i, y ->
            val activa = i == 1
            if (activa) drawRoundRect(c.primaryContainer.copy(alpha = 0.6f), p(40f, y - 6), Size(200 * e, 44 * e), CornerRadius(12 * e))
            drawCircle(if (activa) c.primary else c.outlineVariant, 11 * e, p(64f, y + 16))
            drawRoundRect(
                c.onSurfaceVariant.copy(alpha = if (activa) 0.7f else 0.25f),
                p(84f, y + 11), Size((if (activa) 86f else 70f) * e, 9 * e), CornerRadius(5 * e),
            )
            val pista = p(188f, y + 5)
            drawRoundRect(if (activa) c.primary else c.surfaceContainerHighest, pista, Size(40 * e, 22 * e), CornerRadius(11 * e))
            drawCircle(if (activa) c.onPrimary else c.outline, 8 * e, p(if (activa) 217f else 199f, y + 16))
        }
    }
}

@Composable
fun IlustracionVinculo(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val marca = LocalMarca.current
    Canvas(modifier.semantics { contentDescription = "El teléfono se conecta con el servidor de tu negocio" }) {
        val e = minOf(size.width / ANCHO, size.height / ALTO)
        val origen = Offset((size.width - ANCHO * e) / 2, (size.height - ALTO * e) / 2)
        fun p(x: Float, y: Float) = Offset(origen.x + x * e, origen.y + y * e)
        drawRoundRect(c.surfaceContainerLowest, p(26f, 34f), Size(74 * e, 132 * e), CornerRadius(16 * e))
        drawRoundRect(c.outlineVariant, p(26f, 34f), Size(74 * e, 132 * e), CornerRadius(16 * e), style = Stroke(2 * e))
        drawRoundRect(c.primary, p(40f, 60f), Size(46 * e, 30 * e), CornerRadius(8 * e))
        listOf(48f, 92f, 136f).forEach { y ->
            drawRoundRect(c.surfaceContainerLowest, p(178f, y), Size(80 * e, 36 * e), CornerRadius(10 * e))
            drawRoundRect(c.outlineVariant, p(178f, y), Size(80 * e, 36 * e), CornerRadius(10 * e), style = Stroke(2 * e))
            drawCircle(c.primary, 4 * e, p(194f, y + 18))
            drawRoundRect(c.onSurfaceVariant.copy(alpha = 0.3f), p(206f, y + 14), Size(38 * e, 8 * e), CornerRadius(4 * e))
        }
        drawLine(
            c.primary, p(106f, 110f), p(172f, 110f), 3 * e,
            cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * e, 9 * e)),
        )
        drawCircle(marca.sello, 17 * e, p(139f, 110f))
        dibujarSello(p(139f, 110f), 9 * e, marca.sobreSello, marca.ambar)
    }
}
