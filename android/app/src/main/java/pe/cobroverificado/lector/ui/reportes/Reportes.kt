package pe.cobroverificado.lector.ui.reportes

import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.ui.comun.Tiempo
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import pe.cobroverificado.lector.ui.pagos.soles
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class Periodo(val etiqueta: String, val dias: Long) {
    HOY("Hoy", 1),
    SEMANA("7 días", 7),
    MES("30 días", 30),
}

data class FilaDia(val dia: LocalDate, val cantidad: Int, val yape: BigDecimal, val plin: BigDecimal) {
    val total: BigDecimal get() = yape + plin
}

data class Remitente(val nombre: String, val total: BigDecimal, val cantidad: Int)

data class Reporte(
    val desde: LocalDate,
    val hasta: LocalDate,
    val total: BigDecimal,
    val cantidad: Int,
    val promedio: BigDecimal,
    val porBilletera: Map<Billetera, BigDecimal>,
    val cantidadPorBilletera: Map<Billetera, Int>,
    val dias: List<FilaDia>,
    val principales: List<Remitente>,
    val pagos: List<PagoRegistrado>,
)

private val CERO = BigDecimal.ZERO.setScale(2)

private fun suma(pagos: List<PagoRegistrado>) = pagos.fold(CERO) { s, r -> s + r.pago.monto }

fun reporte(pagos: List<PagoRegistrado>, periodo: Periodo, hoy: LocalDate, zona: ZoneId): Reporte =
    reporte(pagos, hoy.minusDays(periodo.dias - 1), hoy, zona)

// los pagos marcados con un problema no cuentan; los días sin pagos no se listan
fun reporte(pagos: List<PagoRegistrado>, desde: LocalDate, hasta: LocalDate, zona: ZoneId): Reporte {
    fun dia(r: PagoRegistrado) = r.pago.fechaHora.atZoneSameInstant(zona).toLocalDate()
    val enRango = pagos.filter { !it.reportado && !dia(it).isBefore(desde) && !dia(it).isAfter(hasta) }
        .sortedBy { it.pago.fechaHora.toInstant() }
    val total = suma(enRango)
    val promedio = if (enRango.isEmpty()) CERO else total.divide(BigDecimal(enRango.size), 2, RoundingMode.HALF_UP)
    val dias = enRango.groupBy(::dia).map { (d, del) ->
        FilaDia(d, del.size, suma(del.filter { it.pago.billetera == Billetera.YAPE }), suma(del.filter { it.pago.billetera == Billetera.PLIN }))
    }.sortedByDescending { it.dia }
    val principales = enRango.filter { it.pago.remitente != null }
        .groupBy { it.pago.remitente!!.trim().lowercase() }
        .map { (_, g) -> Remitente(g.last().pago.remitente!!.trim(), suma(g), g.size) }
        .sortedWith(compareByDescending<Remitente> { it.total }.thenByDescending { it.cantidad })
        .take(5)
    return Reporte(
        desde, hasta, total, enRango.size, promedio,
        Billetera.entries.associateWith { b -> suma(enRango.filter { it.pago.billetera == b }) },
        Billetera.entries.associateWith { b -> enRango.count { it.pago.billetera == b } },
        dias, principales, enRango,
    )
}

// variación porcentual redondeada; sin base anterior no hay comparación posible
fun variacion(actual: BigDecimal, anterior: BigDecimal): Int? =
    if (anterior.signum() == 0) null
    else (actual - anterior).multiply(BigDecimal(100)).divide(anterior, 0, RoundingMode.HALF_UP).toInt()

fun periodoAnterior(desde: LocalDate, hasta: LocalDate): Pair<LocalDate, LocalDate> {
    val dias = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1
    return desde.minusDays(dias) to hasta.minusDays(dias)
}

fun etiquetaPeriodo(desde: LocalDate, hasta: LocalDate): String =
    if (desde == hasta) "${Tiempo.diaCorto(desde)} ${desde.year}"
    else "${Tiempo.diaCorto(desde)} al ${Tiempo.diaCorto(hasta)} ${hasta.year}"

data class DatosPdf(
    val periodo: String,
    val generado: String,
    val total: String,
    val cantidad: String,
    val promedio: String,
    val billeteras: List<List<String>>,
    val dias: List<List<String>>,
    val totalDias: List<String>,
    val pagos: List<List<String>>,
    val totalPagos: List<String>,
)

fun datosPdf(r: Reporte, generado: ZonedDateTime, zona: ZoneId): DatosPdf {
    fun porcentaje(m: BigDecimal) =
        if (r.total.signum() == 0) "0 %" else "${m.multiply(BigDecimal(100)).divide(r.total, 0, RoundingMode.HALF_UP)} %"
    val total = soles(r.total)
    return DatosPdf(
        periodo = etiquetaPeriodo(r.desde, r.hasta),
        generado = Tiempo.numerica(generado),
        total = "S/ $total",
        cantidad = "${r.cantidad}",
        promedio = "S/ ${soles(r.promedio)}",
        billeteras = Billetera.entries.map { b ->
            val m = r.porBilletera[b] ?: CERO
            listOf(nombreBilletera(b), "${r.cantidadPorBilletera[b] ?: 0}", soles(m), porcentaje(m))
        },
        dias = r.dias.map { listOf("${Tiempo.diaCorto(it.dia)} ${it.dia.year}", "${it.cantidad}", soles(it.yape), soles(it.plin), soles(it.total)) },
        totalDias = listOf("Total", "${r.cantidad}", soles(r.porBilletera[Billetera.YAPE] ?: CERO), soles(r.porBilletera[Billetera.PLIN] ?: CERO), total),
        pagos = r.pagos.map {
            listOf(
                Tiempo.numerica(it.pago.fechaHora.atZoneSameInstant(zona)),
                it.pago.remitente ?: "Sin nombre",
                nombreBilletera(it.pago.billetera),
                it.pago.numeroOperacion ?: "",
                soles(it.pago.monto),
            )
        },
        totalPagos = listOf("Total", if (r.cantidad == 1) "1 pago" else "${r.cantidad} pagos", "", "", total),
    )
}

private val formatoFecha = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun celda(valor: String?): String {
    val v = valor.orEmpty()
    return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

fun csv(pagos: List<PagoRegistrado>, zona: ZoneId): String = buildString {
    append("fecha,hora,billetera,monto,remitente,numero_operacion,estado\n")
    pagos.sortedBy { it.pago.fechaHora.toInstant() }.forEach { r ->
        val momento = r.pago.fechaHora.atZoneSameInstant(zona)
        append(
            listOf(
                momento.format(formatoFecha),
                momento.format(formatoHora),
                r.pago.billetera.valor,
                r.pago.monto.setScale(2).toPlainString(),
                celda(r.pago.remitente),
                celda(r.pago.numeroOperacion),
                r.estado.name.lowercase(),
            ).joinToString(","),
        )
        append('\n')
    }
}
