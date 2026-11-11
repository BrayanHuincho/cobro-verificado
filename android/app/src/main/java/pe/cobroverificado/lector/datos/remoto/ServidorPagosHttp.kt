package pe.cobroverificado.lector.datos.remoto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import pe.cobroverificado.lector.dominio.PagoDetectado
import pe.cobroverificado.lector.dominio.RespuestaEnvio
import pe.cobroverificado.lector.dominio.ResultadoVinculo
import pe.cobroverificado.lector.dominio.ServidorPagos
import pe.cobroverificado.lector.dominio.Sesion
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Serializable
private data class SolicitudVincular(val codigo: String)

@Serializable
private data class RespuestaVincular(val token: String)

// el texto de la notificación nunca sale del teléfono: solo los campos leídos y la huella
@Serializable
private data class SolicitudPago(
    val billetera: String,
    val monto: String,
    val fecha_hora: String,
    val remitente: String?,
    val numero_operacion: String?,
    val huella: String,
)

class ServidorPagosHttp(
    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : ServidorPagos {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val tipoJson = "application/json; charset=utf-8".toMediaType()

    override suspend fun vincular(urlServidor: String, codigo: String): ResultadoVinculo = withContext(Dispatchers.IO) {
        val peticion = Request.Builder()
            .url(urlServidor.trimEnd('/') + "/v1/dispositivos/vincular")
            .post(json.encodeToString(SolicitudVincular(codigo.trim())).toRequestBody(tipoJson))
            .build()
        try {
            cliente.newCall(peticion).execute().use { r ->
                val cuerpo = r.body?.string().orEmpty()
                if (r.isSuccessful) ResultadoVinculo.Vinculado(json.decodeFromString<RespuestaVincular>(cuerpo).token)
                else ResultadoVinculo.Fallo(mensajeError(cuerpo) ?: "el servidor respondió ${r.code}")
            }
        } catch (e: IOException) {
            ResultadoVinculo.Fallo("no se pudo conectar: ${e.message}")
        } catch (e: IllegalArgumentException) {
            ResultadoVinculo.Fallo("respuesta inválida del servidor")
        }
    }

    override suspend fun enviar(sesion: Sesion, pago: PagoDetectado): RespuestaEnvio = withContext(Dispatchers.IO) {
        val cuerpo = SolicitudPago(
            billetera = pago.billetera.valor,
            monto = pago.monto.toPlainString(),
            fecha_hora = pago.fechaHora.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            remitente = pago.remitente,
            numero_operacion = pago.numeroOperacion,
            huella = pago.huella,
        )
        val peticion = Request.Builder()
            .url(sesion.urlServidor.trimEnd('/') + "/v1/pagos-recibidos")
            .header("X-Dispositivo", sesion.token)
            .post(json.encodeToString(cuerpo).toRequestBody(tipoJson))
            .build()
        try {
            cliente.newCall(peticion).execute().use(::interpretar)
        } catch (e: IOException) {
            RespuestaEnvio.ErrorTemporal(e.message ?: "sin conexión")
        }
    }

    private fun interpretar(r: Response): RespuestaEnvio {
        val mensaje = { mensajeError(r.body?.string().orEmpty()) ?: "http ${r.code}" }
        return when {
            r.isSuccessful -> RespuestaEnvio.Aceptado
            r.code == 409 -> RespuestaEnvio.Duplicado
            r.code == 401 || r.code == 403 -> RespuestaEnvio.NoAutorizado
            r.code == 408 || r.code == 429 || r.code >= 500 -> RespuestaEnvio.ErrorTemporal(mensaje())
            else -> RespuestaEnvio.Rechazado(mensaje())
        }
    }

    private fun mensajeError(cuerpo: String): String? = runCatching {
        json.parseToJsonElement(cuerpo).jsonObject["error"]?.jsonObject?.get("mensaje")?.jsonPrimitive?.content
    }.getOrNull()
}
