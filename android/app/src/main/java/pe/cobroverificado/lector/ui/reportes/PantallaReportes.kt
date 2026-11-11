package pe.cobroverificado.lector.ui.reportes

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.comun.Avatar
import pe.cobroverificado.lector.ui.comun.CabeceraSeccion
import pe.cobroverificado.lector.ui.comun.EstadoVacio
import pe.cobroverificado.lector.ui.comun.Esqueleto
import pe.cobroverificado.lector.ui.comun.FilaMetricas
import pe.cobroverificado.lector.ui.comun.Hoja
import pe.cobroverificado.lector.ui.comun.LogoBilletera
import pe.cobroverificado.lector.ui.comun.Metrica
import pe.cobroverificado.lector.ui.comun.Segmentado
import pe.cobroverificado.lector.ui.comun.Tarjeta
import pe.cobroverificado.lector.ui.comun.Tiempo
import pe.cobroverificado.lector.ui.comun.Titulo
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import pe.cobroverificado.lector.ui.pagos.ZONA
import pe.cobroverificado.lector.ui.pagos.soles
import pe.cobroverificado.lector.ui.tema.Cifras
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val opciones = listOf("Hoy", "7 días", "30 días", "Fechas")
private val comparaciones = listOf("vs. ayer", "vs. 7 días anteriores", "vs. 30 días anteriores", "vs. periodo anterior")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaReportes(modelo: PrincipalViewModel, ahora: Instant, relleno: PaddingValues) {
    val pagos by modelo.pagos.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val hoy = ahora.atZone(ZONA).toLocalDate()
    var indice by rememberSaveable { mutableStateOf(1) }
    var desdeElegido by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var hastaElegido by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var elegir by remember { mutableStateOf(false) }
    var exportar by remember { mutableStateOf(false) }
    var generando by remember { mutableStateOf(false) }
    val (desde, hasta) = when (indice) {
        0 -> hoy to hoy
        2 -> hoy.minusDays(29) to hoy
        3 -> (desdeElegido ?: hoy) to (hastaElegido ?: desdeElegido ?: hoy)
        else -> hoy.minusDays(6) to hoy
    }
    val datos = remember(pagos, desde, hasta) { pagos?.let { reporte(it, desde, hasta, ZONA) } }
    val cambio = remember(pagos, desde, hasta) {
        pagos?.let {
            val (d, h) = periodoAnterior(desde, hasta)
            variacion(datos?.total ?: BigDecimal.ZERO, reporte(it, d, h, ZONA).total)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = relleno.calculateTopPadding(), bottom = relleno.calculateBottomPadding() + 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Titulo("Reportes", Modifier.weight(1f))
            if ((datos?.cantidad ?: 0) > 0) {
                FilledTonalButton(
                    onClick = { exportar = true },
                    shape = CircleShape,
                    contentPadding = PaddingValues(start = 14.dp, end = 16.dp),
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 40.dp),
                ) {
                    Icon(Iconos.Exportar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Segmentado(opciones, indice, { if (it == 3) elegir = true else indice = it }, Modifier.padding(horizontal = 16.dp))
        Text(
            etiquetaPeriodo(desde, hasta),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        when {
            datos == null -> Esqueleto(3)
            datos.cantidad == 0 -> EstadoVacio(
                Iconos.Reportes,
                "Sin pagos en este periodo",
                "Elige otro periodo o espera a que lleguen pagos. Aquí verás totales por billetera y por día.",
            )
            else -> Contenido(datos, cambio, comparaciones[indice])
        }
    }

    if (exportar && datos != null) {
        Hoja({ exportar = false }) {
            Text("Exportar reporte", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "${etiquetaPeriodo(desde, hasta)}, ${if (datos.cantidad == 1) "1 pago" else "${datos.cantidad} pagos"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    generando = true
                    alcance.launch {
                        val archivo = withContext(Dispatchers.Default) { generarPdf(contexto, datos) }
                        generando = false
                        exportar = false
                        compartir(contexto, archivo, "application/pdf", "Reporte de pagos ${etiquetaPeriodo(desde, hasta)}")
                    }
                },
                enabled = !generando,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (generando) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Iconos.Pagos, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("PDF con resumen y detalle", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { exportar = false; exportarCsv(contexto, datos.pagos, desde, hasta) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("CSV para hoja de cálculo", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (elegir) {
        val estado = rememberDateRangePickerState(
            initialSelectedStartDateMillis = desdeElegido?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = hastaElegido?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        fun dia(m: Long?) = m?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        Hoja({ elegir = false }, desplazable = true) {
            Text("Elige el periodo", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            DateRangePicker(state = estado, showModeToggle = false, title = null, headline = null, modifier = Modifier.fillMaxWidth().height(420.dp))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    desdeElegido = dia(estado.selectedStartDateMillis)
                    hastaElegido = dia(estado.selectedEndDateMillis) ?: desdeElegido
                    indice = 3
                    elegir = false
                },
                enabled = estado.selectedStartDateMillis != null,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Ver reporte", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun Contenido(r: Reporte, cambio: Int?, comparacion: String) {
    val marca = LocalMarca.current
    Tarjeta {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total recibido", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (cambio != null) {
                    val tono = if (cambio >= 0) marca.exito else marca.peligro
                    Text(
                        "${if (cambio >= 0) "+" else "−"}${kotlin.math.abs(cambio)} % $comparacion",
                        style = MaterialTheme.typography.labelMedium,
                        color = tono.texto,
                        modifier = Modifier.clip(CircleShape).background(tono.fondo).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text("S/", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp, end = 6.dp))
                Text(soles(r.total), style = MaterialTheme.typography.displaySmall.merge(Cifras), maxLines = 1)
            }
        }
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        FilaMetricas(
            listOf(
                Metrica(Iconos.Pagos, "Pagos", "${r.cantidad}", 0.8f),
                Metrica(Iconos.Tendencia, "Ticket promedio", "S/ ${soles(r.promedio)}", 1.25f),
                Metrica(Iconos.Calendario, "Días", "${r.dias.size}", 0.7f),
            ),
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
        )
    }

    CabeceraSeccion("Por billetera")
    Tarjeta {
        Column(Modifier.padding(16.dp)) {
            val yape = r.porBilletera[Billetera.YAPE] ?: BigDecimal.ZERO
            val parte = if (r.total.signum() == 0) 0f else yape.toFloat() / r.total.toFloat()
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                if (parte > 0f) Box(Modifier.weight(parte).fillMaxHeight().background(marca.yape.solido))
                if (parte < 1f) Box(Modifier.weight(1f - parte).fillMaxHeight().background(marca.plin.solido))
            }
            Spacer(Modifier.height(8.dp))
            Billetera.entries.forEach { b ->
                val n = r.cantidadPorBilletera[b] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoBilletera(b, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nombreBilletera(b), style = MaterialTheme.typography.titleSmall)
                        Text(if (n == 1) "1 pago" else "$n pagos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("S/ ${soles(r.porBilletera[b] ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.titleSmall.merge(Cifras))
                }
            }
        }
    }

    CabeceraSeccion("Por día")
    Tarjeta {
        r.dias.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FilaDiaVista(Tiempo.diaConSemana(d.dia), d.cantidad, d.yape, d.plin, d.total)
        }
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            FilaDiaVista("Total", r.cantidad, r.porBilletera[Billetera.YAPE] ?: BigDecimal.ZERO, r.porBilletera[Billetera.PLIN] ?: BigDecimal.ZERO, r.total, pie = true)
        }
    }

    if (r.principales.isNotEmpty()) {
        CabeceraSeccion("Quiénes más te pagan")
        Tarjeta {
            r.principales.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.nombre, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (p.cantidad == 1) "1 pago" else "${p.cantidad} pagos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("S/ ${soles(p.total)}", style = MaterialTheme.typography.titleSmall.merge(Cifras))
                }
            }
        }
    }
}

@Composable
private fun FilaDiaVista(titulo: String, cantidad: Int, yape: BigDecimal, plin: BigDecimal, total: BigDecimal, pie: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(if (cantidad == 1) "1 pago" else "$cantidad pagos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "S/ ${soles(total)}",
                style = (if (pie) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall).merge(Cifras),
                color = if (pie) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Parcial(Billetera.YAPE, yape)
                Parcial(Billetera.PLIN, plin)
            }
        }
    }
}

@Composable
private fun Parcial(b: Billetera, monto: BigDecimal) {
    val tono = if (b == Billetera.YAPE) LocalMarca.current.yape else LocalMarca.current.plin
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoBilletera(b, 14.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            if (monto.signum() == 0) "—" else soles(monto),
            style = MaterialTheme.typography.labelMedium.merge(Cifras),
            color = if (monto.signum() == 0) MaterialTheme.colorScheme.outline else tono.texto,
        )
    }
}

fun generarPdf(contexto: Context, r: Reporte): File {
    val carpeta = File(contexto.cacheDir, "exportes").apply { mkdirs() }
    val archivo = File(carpeta, "reporte-${r.desde}-a-${r.hasta}.pdf")
    val dispositivo = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    ReportePdf(contexto).generar(datosPdf(r, Instant.now().atZone(ZONA), ZONA), dispositivo, archivo)
    return archivo
}

private fun exportarCsv(contexto: Context, pagos: List<PagoRegistrado>, desde: LocalDate, hasta: LocalDate) {
    val carpeta = File(contexto.cacheDir, "exportes").apply { mkdirs() }
    val archivo = File(carpeta, "pagos-$desde-a-$hasta.csv").apply { writeText(csv(pagos, ZONA)) }
    compartir(contexto, archivo, "text/csv", "Pagos ${etiquetaPeriodo(desde, hasta)}")
}

private fun compartir(contexto: Context, archivo: File, tipo: String, asunto: String) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    val intento = Intent(Intent.ACTION_SEND)
        .setType(tipo)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, asunto)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    contexto.startActivity(Intent.createChooser(intento, "Compartir reporte"))
}
