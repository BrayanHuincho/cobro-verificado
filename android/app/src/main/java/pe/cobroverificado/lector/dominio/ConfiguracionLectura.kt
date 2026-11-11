package pe.cobroverificado.lector.dominio

import kotlinx.serialization.Serializable

// los patrones por defecto salen de redacción típica, no de notificaciones reales: hay que validarlos con muestras
@Serializable
data class PatronesBilletera(
    val billetera: Billetera,
    val paquetes: List<String>,
    val ingreso: String,
    val egreso: String,
    val monto: List<String>,
    val remitente: List<String>,
    val operacion: List<String>,
)

@Serializable
data class ConfiguracionLectura(val billeteras: List<PatronesBilletera>) {

    fun paraPaquete(paquete: String): PatronesBilletera? = billeteras.firstOrNull { paquete in it.paquetes }

    fun paquetes(billetera: Billetera): List<String> =
        billeteras.firstOrNull { it.billetera == billetera }?.paquetes.orEmpty()

    fun conPaquetes(billetera: Billetera, paquetes: List<String>): ConfiguracionLectura =
        copy(billeteras = billeteras.map { if (it.billetera == billetera) it.copy(paquetes = paquetes) else it })

    companion object {
        private const val MONTO = "S/\\.?\\s*([0-9][0-9.,]*[0-9]|[0-9])"
        private const val NOMBRE = "([\\p{L}][\\p{L} .'-]{1,60}?)"
        private const val OPERACION = "operaci[oó]n\\s*(?:n[°ºo]\\.?\\s*)?[:#]?\\s*([0-9]{4,})"
        private const val DE_NOMBRE = "\\bde\\s+$NOMBRE(?=\\s+(?:por|el|a las)\\b|[.,]|$)"

        val porDefecto = ConfiguracionLectura(
            listOf(
                PatronesBilletera(
                    billetera = Billetera.YAPE,
                    paquetes = listOf("com.bcp.innovacxion.yapeapp"),
                    ingreso = "te envi[oó] un pago|te yape[oó]|recibiste un yape|recibiste un pago",
                    egreso = "yapeaste|enviaste|pagaste|realizaste un pago",
                    monto = listOf(MONTO),
                    remitente = listOf("(?:^|[!:]\\s*)$NOMBRE\\s+te\\s+(?:envi[oó]|yape[oó])", DE_NOMBRE),
                    operacion = listOf(OPERACION),
                ),
                PatronesBilletera(
                    billetera = Billetera.PLIN,
                    paquetes = listOf(
                        "pe.com.interbank.mobilebanking",
                        "com.bbva.nxt_peru",
                        "pe.com.scotiabank.blpm.android.client",
                    ),
                    ingreso = "recibiste un plin|te plinearon|te envi[oó]|recibiste",
                    egreso = "enviaste|plineaste|pagaste|transferiste",
                    monto = listOf(MONTO),
                    remitente = listOf("(?:^|[!:]\\s*)$NOMBRE\\s+te\\s+(?:envi[oó]|pline[oó])", DE_NOMBRE),
                    operacion = listOf(OPERACION),
                ),
            ),
        )
    }
}
