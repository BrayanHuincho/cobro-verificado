package pe.cobroverificado.lector.ui.comun

import java.time.LocalDate
import java.time.ZonedDateTime

// se arma a mano porque el patrón "a" de es-PE cambia entre la jvm y el icu de android
object Tiempo {
    private val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val meses = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre",
    )
    private val mesesCortos = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "set.", "oct.", "nov.", "dic.")

    fun hora(t: ZonedDateTime): String {
        val h = t.hour % 12
        return "${if (h == 0) 12 else h}:${"%02d".format(t.minute)}$ESPACIO${sufijo(t.hour)}"
    }

    fun diaLargo(d: LocalDate): String = "${dias[d.dayOfWeek.value - 1]} ${d.dayOfMonth} de ${meses[d.monthValue - 1]}"

    private val diasCortos = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    fun diaConSemana(d: LocalDate): String = "${diasCortos[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${mesesCortos[d.monthValue - 1].trimEnd('.')}"

    fun diaCorto(d: LocalDate): String = "${d.dayOfMonth} ${mesesCortos[d.monthValue - 1]}"

    fun completa(t: ZonedDateTime): String = "${diaLargo(t.toLocalDate())} de ${t.year}, ${hora(t)}"

    fun mediana(t: ZonedDateTime): String = "${t.dayOfMonth} de ${meses[t.monthValue - 1]}, ${hora(t)}"

    fun corta(t: ZonedDateTime): String = "${diaCorto(t.toLocalDate())}, ${hora(t)}"

    fun numerica(t: ZonedDateTime): String = "%02d/%02d/%d, %s".format(t.dayOfMonth, t.monthValue, t.year, hora(t))

    fun horaDelDia(h: Int): String = "${if (h % 12 == 0) 12 else h % 12}$ESPACIO${sufijo(h)}"

    // espacio sin corte para que "p. m." nunca quede partido en dos líneas
    private const val ESPACIO = " "

    private fun sufijo(hora: Int) = if (hora < 12) "a.${ESPACIO}m." else "p.${ESPACIO}m."
}
