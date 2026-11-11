package pe.cobroverificado.lector.ui.resumen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.comun.CabeceraSeccion
import pe.cobroverificado.lector.ui.comun.EstadoVacio
import pe.cobroverificado.lector.ui.comun.Esqueleto
import pe.cobroverificado.lector.ui.pagos.EstadoEnvio
import pe.cobroverificado.lector.ui.pagos.FilaPago
import pe.cobroverificado.lector.ui.pagos.ResumenDia
import pe.cobroverificado.lector.ui.pagos.ZONA
import pe.cobroverificado.lector.ui.pagos.estadoEnvio
import pe.cobroverificado.lector.ui.pagos.resumenDe
import pe.cobroverificado.lector.ui.pagos.soles
import pe.cobroverificado.lector.ui.tema.Cifras
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.ui.comun.InsigniaEstado
import pe.cobroverificado.lector.ui.comun.LocalConServidor
import pe.cobroverificado.lector.ui.comun.LogoBilletera
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import androidx.compose.foundation.layout.fillMaxHeight
import pe.cobroverificado.lector.ui.tema.LocalMarca
import pe.cobroverificado.lector.ui.tema.MarcaSello
import pe.cobroverificado.lector.ui.tema.dibujarSello
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResumen(modelo: PrincipalViewModel, ahora: Instant, abrir: (Long) -> Unit, verTodos: () -> Unit, relleno: PaddingValues) {
    val pagos by modelo.pagos.collectAsStateWithLifecycle()
    val pausado by modelo.pausado.collectAsStateWithLifecycle()
    val configuracion by modelo.configuracion.collectAsStateWithLifecycle()
    val refrescando by modelo.refrescando.collectAsStateWithLifecycle()
    val sesion by modelo.sesion.collectAsStateWithLifecycle()
    val lista = pagos.orEmpty()
    val hoy = ahora.atZone(ZONA).toLocalDate()
    val resumen = remember(lista, hoy) { resumenDe(lista, hoy, ZONA) }
    val envio = remember(lista) { estadoEnvio(lista) }
    val recientes = remember(lista) { lista.sortedByDescending { it.pago.fechaHora.toInstant() }.take(5) }
    val apps = configuracion.billeteras.sumOf { it.paquetes.size }

    PullToRefreshBox(isRefreshing = refrescando, onRefresh = modelo::refrescar, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = relleno.calculateTopPadding(), bottom = relleno.calculateBottomPadding() + 24.dp)) {
            item(key = "barra") { Barra() }
            item(key = "resumen") { Tarjeton(resumen, envio, pausado) }
            item(key = "pausa") { ControlPausa(pausado, apps) { modelo.cambiarPausa(it) } }
            if (envio is EstadoEnvio.ConError && sesion != null) item(key = "error") { AvisoError(envio, modelo::sincronizarAhora) }
            item(key = "cabecera") {
                CabeceraSeccion("Últimos pagos") {
                    if (lista.isNotEmpty()) {
                        TextButton(onClick = verTodos, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver todos") }
                    }
                }
            }
            when {
                pagos == null -> item(key = "esqueleto") { Esqueleto(3) }
                lista.isEmpty() -> item(key = "vacio") {
                    EstadoVacio(
                        Iconos.Campana,
                        if (pausado) "El registro está en pausa" else "Aún no hay pagos",
                        if (pausado) "Actívalo arriba para anotar los pagos que lleguen a este teléfono."
                        else "Cuando llegue el aviso de un pago recibido, aparecerá aquí al instante.",
                    )
                }
                else -> itemsIndexed(recientes, key = { _, r -> r.id }) { i, r ->
                    FilaPago(r, ahora, i == 0, i == recientes.lastIndex, abrir, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun Barra() {
    val marca = LocalMarca.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        MarcaSello(Modifier.size(32.dp), marca.sello, marca.sobreSello, marca.ambar)
        Spacer(Modifier.width(10.dp))
        Text("Cobro Verificado", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Tarjeton(resumen: ResumenDia, envio: EstadoEnvio, pausado: Boolean) {
    val marca = LocalMarca.current
    val fondo by animateColorAsState(if (pausado) marca.pausa else marca.sello, label = "fondo")
    val animado by animateFloatAsState(resumen.total.toFloat(), label = "total")
    val mostrado = if (animado == resumen.total.toFloat()) resumen.total else BigDecimal(animado.toDouble()).setScale(2, RoundingMode.HALF_UP)
    val cantidad = if (resumen.cantidad == 1) "1 pago" else "${resumen.cantidad} pagos"
    val (icono, estado) = if (!LocalConServidor.current) Iconos.Telefono to "Solo en este teléfono" else when (envio) {
        EstadoEnvio.AlDia -> Iconos.Enviado to if (resumen.cantidad == 0) "Sin pendientes" else "Todo enviado"
        is EstadoEnvio.PorEnviar -> Iconos.Pendiente to "${envio.cantidad} por enviar"
        is EstadoEnvio.ConError -> Iconos.Error to "${envio.cantidad} sin enviar"
    }
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(fondo)
            .semantics(mergeDescendants = true) {
                contentDescription = "Recibido hoy: ${soles(resumen.total)} soles en $cantidad. $estado." + if (pausado) " Registro en pausa." else ""
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radio = size.height * 0.62f
            dibujarSello(Offset(size.width - radio * 0.55f, size.height * 0.38f), radio, marca.sobreSello.copy(alpha = 0.06f), marca.sobreSello.copy(alpha = 0.06f))
        }
        Column(Modifier.padding(24.dp)) {
            Text(if (pausado) "Recibido hoy, registro en pausa" else "Recibido hoy", style = MaterialTheme.typography.labelLarge, color = marca.sobreSelloSuave)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("S/", style = MaterialTheme.typography.headlineSmall, color = marca.sobreSelloSuave, modifier = Modifier.padding(bottom = 6.dp, end = 6.dp))
                Text(soles(mostrado), style = MaterialTheme.typography.displayMedium.merge(Cifras), color = marca.sobreSello, maxLines = 1)
            }
            Spacer(Modifier.height(18.dp))
            PorBilletera(resumen)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cantidad, style = MaterialTheme.typography.titleSmall, color = marca.sobreSello, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(marca.sobreSello.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icono, contentDescription = null, tint = marca.sobreSello, modifier = Modifier.size(16.dp))
                    Text(estado, style = MaterialTheme.typography.labelMedium, color = marca.sobreSello)
                }
            }
        }
    }
}

@Composable
private fun PorBilletera(resumen: ResumenDia) {
    val marca = LocalMarca.current
    val yape = resumen.porBilletera[Billetera.YAPE]?.total?.toFloat() ?: 0f
    val parte = if (resumen.total.signum() == 0) 0.5f else yape / resumen.total.toFloat()
    Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(marca.sobreSello.copy(alpha = 0.18f))) {
        if (parte > 0f && resumen.total.signum() != 0) Box(Modifier.weight(parte).fillMaxHeight().background(marca.sobreSello))
        if (parte < 1f) Spacer(Modifier.weight(1f - parte))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Billetera.entries.forEach { b ->
            val sub = resumen.porBilletera[b]
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                LogoBilletera(b, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("S/ ${soles(sub?.total ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.titleSmall.merge(Cifras), color = marca.sobreSello, maxLines = 1)
                    val n = sub?.cantidad ?: 0
                    Text(
                        "${nombreBilletera(b)}, ${if (n == 1) "1 pago" else "$n pagos"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = marca.sobreSelloSuave,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ControlPausa(pausado: Boolean, apps: Int, cambiar: (Boolean) -> Unit) {
    val hapticos = LocalHapticFeedback.current
    val activo = !pausado
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, LocalMarca.current.borde, MaterialTheme.shapes.large)
            .toggleable(value = activo, role = Role.Switch) {
                hapticos.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                cambiar(!it)
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val marca = LocalMarca.current
        InsigniaEstado(if (activo) Iconos.Check else Iconos.Pausa, if (activo) marca.exito else marca.alerta, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (activo) "Registrando pagos" else "Registro en pausa", style = MaterialTheme.typography.titleMedium)
            Text(
                if (activo) "Leyendo avisos de $apps ${if (apps == 1) "app" else "apps"} de pago" else "Los avisos que lleguen ahora no se anotan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = activo, onCheckedChange = null)
    }
}

@Composable
private fun AvisoError(envio: EstadoEnvio.ConError, reintentar: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(c.errorContainer)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Error, contentDescription = null, tint = c.onErrorContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (envio.cantidad == 1) "1 pago no se pudo enviar" else "${envio.cantidad} pagos no se pudieron enviar",
                style = MaterialTheme.typography.titleSmall,
                color = c.onErrorContainer,
            )
            envio.mensaje?.let {
                Text(it.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = reintentar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar", color = c.onErrorContainer) }
    }
}
