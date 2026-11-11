package pe.cobroverificado.lector.dominio

import java.net.URI
import java.net.URLDecoder

data class DatosVinculo(val url: String, val codigo: String)

object EnlaceVinculo {
    // formato del qr: cobroverificado://vincular?url=<dirección codificada>&codigo=<código>
    fun leer(texto: String): DatosVinculo? {
        val uri = runCatching { URI(texto.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("cobroverificado", ignoreCase = true) || !uri.host.equals("vincular", ignoreCase = true)) return null
        val parametros = uri.rawQuery.orEmpty().split('&').mapNotNull { par ->
            val i = par.indexOf('=')
            if (i <= 0) null else par.substring(0, i) to URLDecoder.decode(par.substring(i + 1), Charsets.UTF_8)
        }.toMap()
        val url = parametros["url"]?.trim().orEmpty()
        val codigo = parametros["codigo"]?.trim().orEmpty()
        return if (url.isEmpty() || codigo.isEmpty()) null else DatosVinculo(url, codigo)
    }
}
