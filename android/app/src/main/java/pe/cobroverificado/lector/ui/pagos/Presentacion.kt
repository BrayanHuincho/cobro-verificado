package pe.cobroverificado.lector.ui.pagos

import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoRegistrado
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import pe.cobroverificado.lector.ui.comun.Tiempo
import java.util.Locale

data class Subtotal(val total: BigDecimal, val cantidad: Int)

data class ResumenDia(val total: BigDecimal, val cantidad: Int, val porBilletera: Map<Billetera, Subtotal> = emptyMap())

sealed interface EstadoEnvio {
    data object AlDia : EstadoEnvio
    data class PorEnviar(val cantidad: Int) : EstadoEnvio
    data class ConError(val cantidad: Int, val mensaje: String?) : EstadoEnvio
}

data class GrupoDia(val dia: LocalDate, val pagos: List<PagoRegistrado>)

private val espanol = Locale.forLanguageTag("es-PE")
private val formatoSoles = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

fun soles(monto: BigDecimal): String = formatoSoles.format(monto)

fun resumenDe(pagos: List<PagoRegistrado>, dia: LocalDate, zona: ZoneId): ResumenDia {
    val delDia = pagos.filter { !it.reportado && it.pago.fechaHora.atZoneSameInstant(zona).toLocalDate() == dia }
    val porBilletera = Billetera.entries.associateWith { b ->
        val deEsta = delDia.filter { it.pago.billetera == b }
        Subtotal(deEsta.fold(BigDecimal.ZERO) { suma, r -> suma + r.pago.monto }.setScale(2), deEsta.size)
    }
    return ResumenDia(delDia.fold(BigDecimal.ZERO) { suma, r -> suma + r.pago.monto }.setScale(2), delDia.size, porBilletera)
}

// un pago pendiente con intentos ya falló al menos una vez; se muestra como error hasta que pase
fun estadoEnvio(pagos: List<PagoRegistrado>): EstadoEnvio {
    val fallidos = pagos.filter {
        it.estado == EstadoSincronizacion.RECHAZADO || (it.estado == EstadoSincronizacion.PENDIENTE && it.intentos > 0)
    }
    if (fallidos.isNotEmpty()) return EstadoEnvio.ConError(fallidos.size, fallidos.firstNotNullOfOrNull { it.ultimoError })
    val pendientes = pagos.count { it.estado == EstadoSincronizacion.PENDIENTE }
    return if (pendientes > 0) EstadoEnvio.PorEnviar(pendientes) else EstadoEnvio.AlDia
}

fun agruparPorDia(pagos: List<PagoRegistrado>, zona: ZoneId): List<GrupoDia> =
    pagos.sortedByDescending { it.pago.fechaHora.toInstant() }
        .groupBy { it.pago.fechaHora.atZoneSameInstant(zona).toLocalDate() }
        .map { (dia, delDia) -> GrupoDia(dia, delDia) }

fun etiquetaDia(dia: LocalDate, hoy: LocalDate): String = when (dia) {
    hoy -> "Hoy"
    hoy.minusDays(1) -> "Ayer"
    else -> Tiempo.diaLargo(dia).replaceFirstChar { it.titlecase(espanol) }
}

fun momentoRelativo(momento: Instant, ahora: Instant, zona: ZoneId): String {
    val pasado = Duration.between(momento, ahora)
    return when {
        pasado.isNegative || pasado < Duration.ofMinutes(1) -> "Ahora"
        pasado < Duration.ofHours(1) -> "Hace ${pasado.toMinutes()} min"
        else -> {
            val local = momento.atZone(zona)
            when (local.toLocalDate()) {
                ahora.atZone(zona).toLocalDate() -> Tiempo.hora(local)
                ahora.atZone(zona).toLocalDate().minusDays(1) -> "Ayer, ${Tiempo.hora(local)}"
                else -> Tiempo.corta(local)
            }
        }
    }
}

fun iniciales(nombre: String?): String {
    val partes = nombre.orEmpty().split(' ', '-').filter { it.firstOrNull()?.isLetter() == true }
    return when {
        partes.isEmpty() -> "?"
        partes.size == 1 -> partes[0].take(1).uppercase(espanol)
        else -> (partes[0].take(1) + partes[1].take(1)).uppercase(espanol)
    }
}

data class Filtro(
    val texto: String = "",
    val billetera: Billetera? = null,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val montoMinimo: BigDecimal? = null,
    val montoMaximo: BigDecimal? = null,
) {
    val activos: Int get() = listOf(billetera != null, desde != null || hasta != null, montoMinimo != null || montoMaximo != null).count { it }
}

fun filtrar(pagos: List<PagoRegistrado>, filtro: Filtro, zona: ZoneId): List<PagoRegistrado> {
    val buscado = filtro.texto.trim().lowercase(espanol)
    return pagos.filter { r ->
        val dia = r.pago.fechaHora.atZoneSameInstant(zona).toLocalDate()
        (filtro.billetera == null || r.pago.billetera == filtro.billetera) &&
            (filtro.desde == null || !dia.isBefore(filtro.desde)) &&
            (filtro.hasta == null || !dia.isAfter(filtro.hasta)) &&
            (filtro.montoMinimo == null || r.pago.monto >= filtro.montoMinimo) &&
            (filtro.montoMaximo == null || r.pago.monto <= filtro.montoMaximo) &&
            (buscado.isEmpty() || r.pago.remitente.orEmpty().lowercase(espanol).contains(buscado) ||
                r.pago.numeroOperacion.orEmpty().contains(buscado) || soles(r.pago.monto).contains(buscado))
    }
}
