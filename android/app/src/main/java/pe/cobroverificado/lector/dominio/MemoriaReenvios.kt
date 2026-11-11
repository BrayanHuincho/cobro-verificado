package pe.cobroverificado.lector.dominio

import java.time.Duration
import java.time.Instant

// una app que vuelve a publicar el mismo aviso le da otro postTime; se conserva el primero para que la huella no cambie
class MemoriaReenvios(private val ventana: Duration = Duration.ofMinutes(10)) {

    private data class Visto(val texto: String, val publicadaEn: Instant)

    private val vistos = LinkedHashMap<String, Visto>()

    @Synchronized
    fun publicadaEn(clave: String, notificacion: Notificacion): Notificacion {
        val texto = Huella.normalizar(notificacion.titulo + "\n" + notificacion.texto)
        val ahora = notificacion.publicadaEn
        vistos.values.removeAll { Duration.between(it.publicadaEn, ahora) > ventana }
        val previo = vistos[clave]
        if (previo != null && previo.texto == texto) return notificacion.copy(publicadaEn = previo.publicadaEn)
        vistos[clave] = Visto(texto, ahora)
        return notificacion
    }
}
