package pe.cobroverificado.lector.ui.tema

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// la marca: un anillo de sello abierto arriba a la derecha y un check que sale por la abertura
fun DrawScope.dibujarSello(centro: Offset, radio: Float, anillo: Color, check: Color, grosor: Float = radio * 0.23f) {
    drawArc(
        color = anillo,
        startAngle = -30f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(centro.x - radio, centro.y - radio),
        size = Size(radio * 2, radio * 2),
        style = Stroke(grosor, cap = StrokeCap.Round),
    )
    val u = radio / 22f
    val trazo = Path().apply {
        moveTo(centro.x - 11 * u, centro.y)
        lineTo(centro.x - 3 * u, centro.y + 8 * u)
        lineTo(centro.x + 17 * u, centro.y - 15 * u)
    }
    drawPath(trazo, check, style = Stroke(grosor * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun MarcaSello(modifier: Modifier, fondo: Color, anillo: Color, check: Color) {
    Canvas(modifier) {
        drawCircle(fondo)
        dibujarSello(center, size.minDimension * 0.3f, anillo, check)
    }
}
