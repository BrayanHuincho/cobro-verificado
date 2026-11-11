package pe.cobroverificado.lector.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

@Serializable
enum class Billetera(val valor: String) {
    @SerialName("yape") YAPE("yape"),
    @SerialName("plin") PLIN("plin"),
}

data class Notificacion(
    val paquete: String,
    val titulo: String,
    val texto: String,
    val publicadaEn: Instant,
)

data class PagoDetectado(
    val billetera: Billetera,
    val monto: BigDecimal,
    val fechaHora: OffsetDateTime,
    val remitente: String?,
    val numeroOperacion: String?,
    val huella: String,
    val paquete: String? = null,
)

enum class EstadoSincronizacion { PENDIENTE, ENVIADO, RECHAZADO }

data class PagoRegistrado(
    val id: Long,
    val pago: PagoDetectado,
    val estado: EstadoSincronizacion,
    val intentos: Int,
    val ultimoError: String?,
    val sincronizadoEn: Instant? = null,
    val reportado: Boolean = false,
)
