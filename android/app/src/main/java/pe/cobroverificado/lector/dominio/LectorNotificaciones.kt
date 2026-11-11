package pe.cobroverificado.lector.dominio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class LectorNotificaciones(
    private val configuracion: ConfiguracionLectura,
    private val zona: ZoneId = ZONA_LIMA,
) {

    fun leer(notificacion: Notificacion): PagoDetectado? {
        val patrones = configuracion.paraPaquete(notificacion.paquete) ?: return null
        val texto = listOf(notificacion.titulo, notificacion.texto).filter { it.isNotBlank() }.joinToString("\n")
        if (!regex(patrones.ingreso).containsMatchIn(texto)) return null
        if (regex(patrones.egreso).containsMatchIn(texto)) return null

        val monto = primerGrupo(patrones.monto, texto)?.let(::interpretarMonto) ?: return null
        if (monto.signum() <= 0) return null
        return PagoDetectado(
            billetera = patrones.billetera,
            monto = monto,
            fechaHora = interpretarFechaHora(texto, notificacion.publicadaEn, zona),
            remitente = primerGrupo(patrones.remitente, texto)?.trim()?.trimEnd('.')?.trim(),
            numeroOperacion = primerGrupo(patrones.operacion, texto),
            huella = Huella.calcular(texto, notificacion.publicadaEn.toEpochMilli()),
            paquete = notificacion.paquete,
        )
    }

    private fun primerGrupo(patrones: List<String>, texto: String): String? =
        patrones.firstNotNullOfOrNull { patron ->
            regex(patron).find(texto)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }

    private fun regex(patron: String) = Regex(patron, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    companion object {
        val ZONA_LIMA: ZoneId = ZoneId.of("America/Lima")

        private val hora = Regex("\\b(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(?:([ap])\\.?\\s*m\\b\\.?)?", RegexOption.IGNORE_CASE)
        private val fechaNumerica = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b")
        private val fechaTexto = Regex(
            "\\b(\\d{1,2})\\s+(?:de\\s+)?(ene|feb|mar|abr|may|jun|jul|ago|set|sep|oct|nov|dic)[a-z]*\\.?\\s+(?:de\\s+)?(\\d{4})\\b",
            RegexOption.IGNORE_CASE,
        )
        private val meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic")

        fun interpretarMonto(crudo: String): BigDecimal? {
            val limpio = crudo.trim()
            val punto = limpio.lastIndexOf('.')
            val coma = limpio.lastIndexOf(',')
            val decimal = when {
                punto >= 0 && coma >= 0 -> if (punto > coma) '.' else ','
                punto >= 0 || coma >= 0 -> {
                    val separador = if (punto >= 0) '.' else ','
                    if (limpio.length - limpio.lastIndexOf(separador) - 1 == 3) null else separador
                }
                else -> null
            }
            val entero = if (decimal == null) limpio else limpio.substringBeforeLast(decimal)
            val fraccion = if (decimal == null) "" else limpio.substringAfterLast(decimal)
            if (fraccion.length > 2 || !fraccion.all(Char::isDigit)) return null
            val digitos = entero.filter(Char::isDigit)
            if (digitos.isEmpty()) return null
            return BigDecimal(if (fraccion.isEmpty()) digitos else "$digitos.$fraccion").setScale(2)
        }

        // sin fecha en el texto se usa la del aviso; una hora posterior al aviso es del día anterior
        fun interpretarFechaHora(texto: String, publicadaEn: Instant, zona: ZoneId = ZONA_LIMA): OffsetDateTime {
            val referencia = publicadaEn.atZone(zona)
            val horaLeida = hora.find(texto)?.let { m ->
                var h = m.groupValues[1].toInt()
                val minuto = m.groupValues[2].toInt()
                when (m.groupValues[3].lowercase()) {
                    "p" -> if (h < 12) h += 12
                    "a" -> if (h == 12) h = 0
                }
                if (h in 0..23 && minuto in 0..59) LocalTime.of(h, minuto) else null
            } ?: return referencia.toOffsetDateTime()

            val fechaLeida = leerFecha(texto)
            var resultado = ZonedDateTime.of(fechaLeida ?: referencia.toLocalDate(), horaLeida, zona)
            if (fechaLeida == null && resultado.isAfter(referencia.plusMinutes(5))) resultado = resultado.minusDays(1)
            return resultado.toOffsetDateTime()
        }

        private fun leerFecha(texto: String): LocalDate? = runCatching {
            val numerica = fechaNumerica.find(texto)
            if (numerica != null) {
                val anio = numerica.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(anio, numerica.groupValues[2].toInt(), numerica.groupValues[1].toInt())
            } else {
                fechaTexto.find(texto)?.let { m ->
                    val mes = meses.indexOf(m.groupValues[2].lowercase().replace("sep", "set")) + 1
                    LocalDate.of(m.groupValues[3].toInt(), mes, m.groupValues[1].toInt())
                }
            }
        }.getOrNull()
    }
}
