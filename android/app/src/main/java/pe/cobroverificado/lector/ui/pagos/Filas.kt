package pe.cobroverificado.lector.ui.pagos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.LectorNotificaciones
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.ui.comun.Avatar
import pe.cobroverificado.lector.ui.comun.ChipBilletera
import pe.cobroverificado.lector.ui.comun.contenedorCompartido
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import pe.cobroverificado.lector.ui.tema.Cifras
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import pe.cobroverificado.lector.ui.tema.Tono
import pe.cobroverificado.lector.ui.comun.InsigniaEstado
import pe.cobroverificado.lector.ui.comun.LocalConServidor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

val ZONA = LectorNotificaciones.ZONA_LIMA

data class Estado(val icono: ImageVector, val tono: Tono, val texto: String)

@Composable
fun estadoDe(registro: PagoRegistrado): Estado {
    val marca = LocalMarca.current
    return when (registro.estado) {
        EstadoSincronizacion.ENVIADO -> Estado(Iconos.Check, marca.exito, "Enviado")
        EstadoSincronizacion.RECHAZADO -> Estado(Iconos.Alerta, marca.peligro, "Rechazado por el servidor")
        EstadoSincronizacion.PENDIENTE ->
            if (registro.intentos > 0) Estado(Iconos.Alerta, marca.peligro, "No se pudo enviar")
            else Estado(Iconos.Reloj, marca.alerta, "Por enviar")
    }
}

fun formaFila(primera: Boolean, ultima: Boolean): RoundedCornerShape {
    val grande = 24.dp
    val chica = 6.dp
    return RoundedCornerShape(
        topStart = if (primera) grande else chica, topEnd = if (primera) grande else chica,
        bottomStart = if (ultima) grande else chica, bottomEnd = if (ultima) grande else chica,
    )
}

@Composable
fun FilaPago(
    registro: PagoRegistrado,
    ahora: Instant,
    primera: Boolean,
    ultima: Boolean,
    abrir: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pago = registro.pago
    val c = MaterialTheme.colorScheme
    val forma = formaFila(primera, ultima)
    val nombre = pago.remitente ?: "Remitente sin leer"
    val momento = momentoRelativo(pago.fechaHora.toInstant(), ahora, ZONA)
    val estado = estadoDe(registro)

    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .contenedorCompartido("pago-${registro.id}", forma)
            .fillMaxWidth()
            .clip(forma)
            .background(c.surfaceContainerLowest)
            .clickable { abrir(registro.id) }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clearAndSetSemantics {
                contentDescription = "${soles(pago.monto)} soles de $nombre por ${nombreBilletera(pago.billetera)}, ${momento.lowercase()}" +
                    (pago.numeroOperacion?.let { ", operación $it" } ?: "") + ", ${estado.texto.lowercase()}" +
                    if (registro.reportado) ", marcado con problema" else ""
                role = Role.Button
                onClick("Ver detalle") { abrir(registro.id); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(nombre)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (registro.reportado) 0.6f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    "+ S/ ${soles(pago.monto)}",
                    style = MaterialTheme.typography.titleMedium.merge(Cifras),
                    textDecoration = if (registro.reportado) TextDecoration.LineThrough else null,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipBilletera(pago.billetera)
                Text(
                    if (registro.reportado) "$momento  Con problema" else momento,
                    style = MaterialTheme.typography.bodySmall.merge(Cifras),
                    color = if (registro.reportado) c.error else c.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (LocalConServidor.current) InsigniaEstado(estado.icono, estado.tono)
            }
            pago.numeroOperacion?.let {
                Spacer(Modifier.height(2.dp))
                Text("Operación $it", style = MaterialTheme.typography.bodySmall.merge(Cifras), color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CabeceraDia(grupo: GrupoDia, hoy: LocalDate, modifier: Modifier = Modifier) {
    val total = soles(grupo.pagos.filterNot { it.reportado }.fold(BigDecimal.ZERO) { suma, r -> suma + r.pago.monto })
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiquetaDia(grupo.dia, hoy), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        Text("S/ $total", style = MaterialTheme.typography.labelMedium.merge(Cifras), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun LazyListScope.filasAgrupadas(
    grupos: List<GrupoDia>,
    hoy: LocalDate,
    ahora: Instant,
    abrir: (Long) -> Unit,
) {
    grupos.forEach { grupo ->
        item(key = "dia-${grupo.dia}") { CabeceraDia(grupo, hoy, Modifier.animateItem()) }
        itemsIndexed(grupo.pagos, key = { _, r -> r.id }) { i, r ->
            FilaAnimada(r, ahora, i == 0, i == grupo.pagos.lastIndex, abrir)
        }
    }
}

@Composable
private fun LazyItemScope.FilaAnimada(r: PagoRegistrado, ahora: Instant, primera: Boolean, ultima: Boolean, abrir: (Long) -> Unit) {
    FilaPago(r, ahora, primera, ultima, abrir, Modifier.animateItem())
}
