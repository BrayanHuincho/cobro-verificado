package pe.cobroverificado.lector.ui.pagos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.comun.EstadoVacio
import pe.cobroverificado.lector.ui.comun.Esqueleto
import pe.cobroverificado.lector.ui.comun.Hoja
import pe.cobroverificado.lector.ui.comun.FilaMetricas
import pe.cobroverificado.lector.ui.comun.Metrica
import pe.cobroverificado.lector.ui.comun.LogoBilletera
import pe.cobroverificado.lector.ui.comun.SelectorBilletera
import pe.cobroverificado.lector.ui.comun.Tarjeta
import pe.cobroverificado.lector.ui.comun.Tiempo
import pe.cobroverificado.lector.ui.comun.Titulo
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import pe.cobroverificado.lector.ui.tema.Cifras
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

enum class Rango(val etiqueta: String) { TODO("Todo"), HOY("Hoy"), SEMANA("7 días"), MES("30 días"), ELEGIDO("Elegir fechas") }

data class Seleccion(
    val billetera: Billetera? = null,
    val rango: Rango = Rango.TODO,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val minimo: Int? = null,
    val maximo: Int? = null,
) : Serializable {
    fun filtro(texto: String, hoy: LocalDate) = when (rango) {
        Rango.TODO -> Filtro(texto, billetera)
        Rango.HOY -> Filtro(texto, billetera, hoy, hoy)
        Rango.SEMANA -> Filtro(texto, billetera, hoy.minusDays(6), hoy)
        Rango.MES -> Filtro(texto, billetera, hoy.minusDays(29), hoy)
        Rango.ELEGIDO -> Filtro(texto, billetera, desde, hasta)
    }.copy(montoMinimo = minimo?.let { BigDecimal(it) }, montoMaximo = maximo?.let { BigDecimal(it) })

    fun etiquetaRango() = if (rango == Rango.ELEGIDO && desde != null) {
        "${Tiempo.diaCorto(desde)} a ${Tiempo.diaCorto(hasta ?: desde)}"
    } else {
        rango.etiqueta
    }
}

private const val PAGINA = 30

private fun entero(n: Int) = "%,d".format(java.util.Locale.US, n)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorial(modelo: PrincipalViewModel, ahora: Instant, abrir: (Long) -> Unit, relleno: PaddingValues) {
    val pagos by modelo.pagos.collectAsStateWithLifecycle()
    val refrescando by modelo.refrescando.collectAsStateWithLifecycle()
    val hoy = ahora.atZone(ZONA).toLocalDate()
    var texto by rememberSaveable { mutableStateOf("") }
    var seleccion by rememberSaveable { mutableStateOf(Seleccion()) }
    var abrirFiltros by remember { mutableStateOf(false) }
    var limite by rememberSaveable { mutableIntStateOf(PAGINA) }

    val filtro = seleccion.filtro(texto, hoy)
    val filtrados = remember(pagos, filtro) { filtrar(pagos.orEmpty(), filtro, ZONA) }
    LaunchedEffect(filtro) { limite = PAGINA }
    val visibles = remember(filtrados, limite) { agruparPorDia(filtrados.take(limite), ZONA) }
    val estadoLista = rememberLazyListState()
    val cercaDelFinal by remember {
        derivedStateOf { estadoLista.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= estadoLista.layoutInfo.totalItemsCount - 5 } ?: false }
    }
    LaunchedEffect(cercaDelFinal, filtrados.size) {
        if (cercaDelFinal && limite < filtrados.size) limite += PAGINA
    }

    PullToRefreshBox(isRefreshing = refrescando, onRefresh = modelo::refrescar, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = estadoLista,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = relleno.calculateTopPadding(), bottom = relleno.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "titulo") { Titulo("Pagos") }
            item(key = "buscar") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Buscador(texto, { texto = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    BotonFiltros(filtro.activos) { abrirFiltros = true }
                }
            }
            if (filtro.activos > 0) {
                item(key = "activos") { FiltrosActivos(seleccion) { seleccion = it } }
            }
            if (pagos != null && filtrados.isNotEmpty()) {
                item(key = "conteo") { Estadisticas(filtrados) }
            }
            when {
                pagos == null -> item(key = "esqueleto") { Box(Modifier.padding(top = 16.dp)) { Esqueleto(5) } }
                filtrados.isEmpty() && (texto.isNotBlank() || filtro.activos > 0) -> item(key = "sinresultados") {
                    EstadoVacio(Iconos.Buscar, "Sin resultados", "Ningún pago coincide con la búsqueda y los filtros elegidos.") {
                        OutlinedButton(onClick = { texto = ""; seleccion = Seleccion() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quitar filtros") }
                    }
                }
                filtrados.isEmpty() -> item(key = "vacio") {
                    EstadoVacio(Iconos.Pagos, "Tu historial está vacío", "Los pagos que recibas quedarán guardados aquí, agrupados por día.")
                }
                else -> filasAgrupadas(visibles, hoy, ahora, abrir)
            }
        }
    }

    if (abrirFiltros) {
        val tope = remember(pagos) {
            (pagos.orEmpty().maxOfOrNull { it.pago.monto } ?: BigDecimal(100)).divide(BigDecimal(50), 0, RoundingMode.CEILING).toInt().coerceAtLeast(1) * 50
        }
        HojaFiltros(seleccion, tope, { s -> filtrar(pagos.orEmpty(), s.filtro(texto, hoy), ZONA).size }, { seleccion = it }) { abrirFiltros = false }
    }
}

@Composable
private fun Buscador(texto: String, cambiar: (String) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(c.surfaceContainerLowest)
            .border(1.dp, LocalMarca.current.borde, CircleShape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Buscar, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (texto.isEmpty()) Text("Buscar pagos", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, maxLines = 1)
            BasicTextField(
                value = texto,
                onValueChange = cambiar,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar pagos por nombre, monto u operación" },
            )
        }
        if (texto.isNotEmpty()) {
            IconButton(onClick = { cambiar("") }) { Icon(Iconos.Cerrar, contentDescription = "Borrar búsqueda", modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun BotonFiltros(activos: Int, abrir: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(if (activos > 0) c.primaryContainer else c.surfaceContainerLowest)
                .border(1.dp, if (activos > 0) c.primary.copy(alpha = 0.4f) else LocalMarca.current.borde, MaterialTheme.shapes.medium)
                .clickable(onClick = abrir)
                .semantics { contentDescription = if (activos > 0) "Filtros, $activos activos" else "Filtros" },
            contentAlignment = Alignment.Center,
        ) { Icon(Iconos.Filtros, contentDescription = null, tint = if (activos > 0) c.onPrimaryContainer else c.onSurface) }
        if (activos > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(20.dp).clip(CircleShape).background(c.primary),
                contentAlignment = Alignment.Center,
            ) { Text("$activos", style = MaterialTheme.typography.labelSmall, color = c.onPrimary) }
        }
    }
}

@Composable
private fun FiltrosActivos(seleccion: Seleccion, cambiar: (Seleccion) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seleccion.billetera?.let { b ->
            ChipQuitable(nombreBilletera(b), { LogoBilletera(b, 18.dp) }) { cambiar(seleccion.copy(billetera = null)) }
        }
        if (seleccion.rango != Rango.TODO) {
            ChipQuitable(seleccion.etiquetaRango(), { Icon(Iconos.Calendario, null, Modifier.size(16.dp)) }) {
                cambiar(seleccion.copy(rango = Rango.TODO, desde = null, hasta = null))
            }
        }
        if (seleccion.minimo != null || seleccion.maximo != null) {
            ChipQuitable("S/ ${entero(seleccion.minimo ?: 0)} a ${seleccion.maximo?.let { "S/ ${entero(it)}" } ?: "más"}", null) { cambiar(seleccion.copy(minimo = null, maximo = null)) }
        }
    }
}

@Composable
private fun ChipQuitable(texto: String, inicio: (@Composable () -> Unit)?, quitar: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(c.primaryContainer.copy(alpha = 0.6f))
            .clickable(onClickLabel = "Quitar filtro", onClick = quitar)
            .padding(start = if (inicio != null) 8.dp else 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        inicio?.invoke()
        Text(texto, style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer, maxLines = 1)
        Icon(Iconos.Cerrar, contentDescription = "Quitar $texto", tint = c.onPrimaryContainer, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Estadisticas(pagos: List<PagoRegistrado>) {
    val validos = pagos.filterNot { it.reportado }
    val total = validos.fold(BigDecimal.ZERO) { s, r -> s + r.pago.monto }
    val promedio = if (validos.isEmpty()) BigDecimal.ZERO else total.divide(BigDecimal(validos.size), 2, RoundingMode.HALF_UP)
    Tarjeta(Modifier.padding(top = 16.dp)) {
        FilaMetricas(
            listOf(
                Metrica(Iconos.Pagos, "Pagos", "${pagos.size}", 0.75f),
                Metrica(Iconos.Monedas, "Total", "S/ ${soles(total)}", 1.3f),
                Metrica(Iconos.Tendencia, "Promedio", "S/ ${soles(promedio)}", 1.1f),
            ),
            Modifier.padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleLarge.merge(Cifras), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HojaFiltros(inicial: Seleccion, tope: Int, contar: (Seleccion) -> Int, aplicar: (Seleccion) -> Unit, cerrar: () -> Unit) {
    var s by remember { mutableStateOf(inicial) }
    var rangoMonto by remember { mutableStateOf((s.minimo ?: 0).toFloat()..(s.maximo ?: tope).toFloat().coerceAtMost(tope.toFloat())) }
    val conMonto = s.copy(
        minimo = rangoMonto.start.toInt().takeIf { it > 0 },
        maximo = rangoMonto.endInclusive.toInt().takeIf { it < tope },
    )
    val estadoFechas = rememberDateRangePickerState(
        initialSelectedStartDateMillis = s.desde?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = s.hasta?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    fun dia(m: Long?) = m?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val final = if (s.rango == Rango.ELEGIDO) {
        conMonto.copy(desde = dia(estadoFechas.selectedStartDateMillis), hasta = dia(estadoFechas.selectedEndDateMillis) ?: dia(estadoFechas.selectedStartDateMillis))
    } else {
        conMonto
    }
    val cantidad = contar(final)

    Hoja(cerrar, desplazable = true) {
        Text("Filtros", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Seccion("Billetera")
        SelectorBilletera(s.billetera, { s = s.copy(billetera = it) }, permitirNinguna = true)
        Text(
            if (s.billetera == null) "Sin elegir se muestran ambas" else "Toca de nuevo para mostrar ambas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Seccion("Fecha")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rango.entries.forEach { r ->
                FilterChip(
                    selected = s.rango == r,
                    onClick = { s = s.copy(rango = r) },
                    label = { Text(r.etiqueta) },
                    leadingIcon = if (r == Rango.ELEGIDO) ({ Icon(Iconos.Calendario, null, Modifier.size(18.dp)) }) else null,
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
        if (s.rango == Rango.ELEGIDO) {
            DateRangePicker(
                state = estadoFechas,
                showModeToggle = false,
                title = null,
                headline = null,
                modifier = Modifier.fillMaxWidth().height(400.dp).padding(top = 8.dp),
            )
        }
        Seccion("Monto")
        Row(Modifier.fillMaxWidth()) {
            Text("S/ ${entero(rangoMonto.start.toInt())}", style = MaterialTheme.typography.titleSmall.merge(Cifras), modifier = Modifier.weight(1f))
            Text(
                if (rangoMonto.endInclusive.toInt() >= tope) "S/ ${entero(tope)} o más" else "S/ ${entero(rangoMonto.endInclusive.toInt())}",
                style = MaterialTheme.typography.titleSmall.merge(Cifras),
            )
        }
        RangeSlider(
            value = rangoMonto,
            onValueChange = { rangoMonto = it.start.roundToInt().toFloat()..it.endInclusive.roundToInt().toFloat() },
            valueRange = 0f..tope.toFloat(),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { s = Seleccion(); rangoMonto = 0f..tope.toFloat() },
                modifier = Modifier.heightIn(min = 52.dp),
            ) { Text("Limpiar") }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { aplicar(final); cerrar() },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text(if (cantidad == 1) "Ver 1 pago" else "Ver $cantidad pagos", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp).semantics { heading() })
}
