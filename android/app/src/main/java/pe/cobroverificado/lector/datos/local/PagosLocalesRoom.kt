package pe.cobroverificado.lector.datos.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoDetectado
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.dominio.PagosLocales
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

class PagosLocalesRoom(private val dao: PagoDao) : PagosLocales {

    override suspend fun registrar(pago: PagoDetectado): Boolean =
        dao.insertar(
            PagoEntidad(
                billetera = pago.billetera.valor,
                monto = pago.monto.toPlainString(),
                fechaHora = pago.fechaHora.toString(),
                remitente = pago.remitente,
                numeroOperacion = pago.numeroOperacion,
                huella = pago.huella,
                estado = EstadoSincronizacion.PENDIENTE.name,
                registradoEn = System.currentTimeMillis(),
                paquete = pago.paquete,
            ),
        ) != -1L

    override fun observar(): Flow<List<PagoRegistrado>> = dao.observarTodos().map { it.map(::aDominio) }

    override suspend fun pendientes(limite: Int): List<PagoRegistrado> = dao.pendientes(limite).map(::aDominio)

    override suspend fun actualizar(id: Long, estado: EstadoSincronizacion, intentos: Int, error: String?) =
        dao.actualizar(id, estado.name, intentos, error, System.currentTimeMillis())

    override suspend fun marcarReportado(id: Long, reportado: Boolean) = dao.marcarReportado(id, reportado)

    override suspend fun borrarTodo() = dao.borrarTodo()

    private fun aDominio(e: PagoEntidad) = PagoRegistrado(
        id = e.id,
        pago = PagoDetectado(
            billetera = Billetera.entries.first { it.valor == e.billetera },
            monto = BigDecimal(e.monto),
            fechaHora = OffsetDateTime.parse(e.fechaHora),
            remitente = e.remitente,
            numeroOperacion = e.numeroOperacion,
            huella = e.huella,
            paquete = e.paquete,
        ),
        estado = EstadoSincronizacion.valueOf(e.estado),
        intentos = e.intentos,
        ultimoError = e.ultimoError,
        sincronizadoEn = e.sincronizadoEn?.let(Instant::ofEpochMilli),
        reportado = e.reportado,
    )
}
