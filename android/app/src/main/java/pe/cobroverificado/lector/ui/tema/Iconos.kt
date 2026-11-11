package pe.cobroverificado.lector.ui.tema

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// los rellenos vienen de material symbols (apache 2.0); los de trazo son propios, en una grilla de 24
private fun relleno(nombre: String, trazo: String) = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(trazo), fill = SolidColor(Color.Black))
    .build()

private fun linea(nombre: String, trazo: String) = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
    .addPath(
        addPathNodes(trazo),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()

private fun lleno(nombre: String, contorno: String, detalle: String = "") = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
    .addPath(
        addPathNodes(contorno),
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .apply {
        if (detalle.isNotEmpty()) {
            addPath(addPathNodes(detalle), stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round)
        }
    }
    .build()

object Iconos {
    val Enviado = relleno("enviado", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z")
    val Pendiente = relleno("pendiente", "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z")
    val Error = relleno("error", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z")
    val Pausa = relleno("pausa", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")

    val Inicio = linea("inicio", "M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM10 20v-5h4v5")
    val InicioLleno = lleno("inicio_lleno", "M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM9.8 20v-5.6h4.4V20z")
    val Pagos = linea("pagos", "M6 3.5h12v17l-3-2-3 2-3-2-3 2zM9.5 8.5h5M9.5 12.5h5")
    val PagosLleno = lleno("pagos_lleno", "M6 3.5h12v17l-3-2-3 2-3-2-3 2zM9.3 7.6h5.4v1.8H9.3zM9.3 11.6h5.4v1.8H9.3z")
    val Reportes = linea("reportes", "M4.5 13h3.5v7H4.5zM10.25 5h3.5v15h-3.5zM16 9h3.5v11H16z")
    val ReportesLleno = lleno("reportes_lleno", "M4.5 13h3.5v7H4.5zM10.25 5h3.5v15h-3.5zM16 9h3.5v11H16z")
    val Ajustes = linea("ajustes", "M4 7h2.6M11.4 7H20M4 17h8.6M17.4 17H20M6.6 7a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0-4.8 0M12.6 17a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0-4.8 0")
    val AjustesLleno = lleno(
        "ajustes_lleno",
        "M6.6 7a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0-4.8 0M12.6 17a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0-4.8 0",
        "M4 7h2.6M11.4 7H20M4 17h8.6M17.4 17H20",
    )
    val Buscar = linea("buscar", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0-12 0M15.5 15.5L20 20")
    val Copiar = linea("copiar", "M9 9h10v11H9zM5 15V4h10")
    val Siguiente = linea("siguiente", "M9 6l6 6-6 6")
    val Atras = linea("atras", "M15 6l-6 6 6 6")
    val Cerrar = linea("cerrar", "M6 6l12 12M18 6L6 18")
    val Agregar = linea("agregar", "M12 5v14M5 12h14")
    val Exportar = linea("exportar", "M12 4v11M8 8l4-4 4 4M5 14v5h14v-5")
    val Servidor = linea("servidor", "M4 5h16v6H4zM4 13h16v6H4zM8 8h.01M8 16h.01")
    val Telefono = linea("telefono", "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2")
    val Escudo = linea("escudo", "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z")
    val Desvincular = linea("desvincular", "M9.5 14.5l-1.5 1.5a3.5 3.5 0 0 1-5-5l1.5-1.5M14.5 9.5l1.5-1.5a3.5 3.5 0 0 1 5 5l-1.5 1.5M4 4l16 16")
    val Campana = linea("campana", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 21h4")
    val Info = linea("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 11v6M12 7.5h.01")
    val Apps = linea("apps", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z")
    val Calendario = linea("calendario", "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4")
    val Codigo = linea("codigo", "M8 8l-4 4 4 4M16 8l4 4-4 4")
    val Qr = linea("qr", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5zM14 20h.01M20 14h.01")
    val Monedas = linea("monedas", "M12 3.5v17M16 7.5h-5.5a2.5 2.5 0 0 0 0 5h3a2.5 2.5 0 0 1 0 5H7.5")
    val Tendencia = linea("tendencia", "M4 17l5.5-5.5 4 4L20 9M15 9h5v5")
    val Check = linea("check", "M5 12.5l4.5 4.5L19 7.5")
    val Reloj = linea("reloj", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0M12 7.5V12l3 2")
    val Alerta = linea("alerta", "M12 6.5v7M12 17.5h.01")
    val Filtros = linea("filtros", "M4 6h16M7 12h10M10 18h4")
    val Sincronizar = linea("sincronizar", "M20 12a8 8 0 0 1-14.3 4.9M4 12a8 8 0 0 1 14.3-4.9M18.5 3v4.2h-4.2M5.5 21v-4.2h4.2")
}
