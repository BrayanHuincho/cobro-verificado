package pe.cobroverificado.lector.dominio

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object Huella {
    private val espacios = Regex("\\s+")

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(espacios, " ")
            .trim()

    // postTime separa dos pagos reales con el mismo texto; un reenvío del mismo aviso conserva su postTime
    fun calcular(texto: String, publicadaEnMilis: Long): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${normalizar(texto)}\n$publicadaEnMilis".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
