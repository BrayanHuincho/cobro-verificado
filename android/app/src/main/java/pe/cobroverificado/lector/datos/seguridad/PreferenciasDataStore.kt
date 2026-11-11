package pe.cobroverificado.lector.datos.seguridad

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pe.cobroverificado.lector.dominio.ConfiguracionLectura
import pe.cobroverificado.lector.dominio.Preferencias
import pe.cobroverificado.lector.dominio.Sesion

private val Context.almacen by preferencesDataStore(name = "lector")

// el token se guarda cifrado con una clave del keystore que nunca sale del dispositivo
class PreferenciasDataStore(
    private val contexto: Context,
    private val cifrador: CifradorKeystore = CifradorKeystore(),
) : Preferencias {

    private val url = stringPreferencesKey("url_servidor")
    private val token = stringPreferencesKey("token_cifrado")
    private val pausa = booleanPreferencesKey("pausado")
    private val local = booleanPreferencesKey("sin_servidor")
    private val config = stringPreferencesKey("configuracion")
    private val json = Json { ignoreUnknownKeys = true }

    override val sesion: Flow<Sesion?> = contexto.almacen.data.map { p ->
        val u = p[url] ?: return@map null
        val t = p[token]?.let(cifrador::descifrar) ?: return@map null
        Sesion(u, t)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override val pausado: Flow<Boolean> = contexto.almacen.data.map { it[pausa] ?: false }.distinctUntilChanged()

    override val sinServidor: Flow<Boolean> = contexto.almacen.data.map { it[local] ?: false }.distinctUntilChanged()

    override val configuracion: Flow<ConfiguracionLectura> = contexto.almacen.data.map { p ->
        p[config]?.let { runCatching { json.decodeFromString<ConfiguracionLectura>(it) }.getOrNull() }
            ?: ConfiguracionLectura.porDefecto
    }.distinctUntilChanged()

    override suspend fun guardarSesion(sesion: Sesion) {
        val cifrado = cifrador.cifrar(sesion.token)
        contexto.almacen.edit {
            it[url] = sesion.urlServidor
            it[token] = cifrado
        }
    }

    override suspend fun borrarSesion() {
        contexto.almacen.edit {
            it.remove(url)
            it.remove(token)
        }
    }

    override suspend fun cambiarPausa(pausado: Boolean) {
        contexto.almacen.edit { it[pausa] = pausado }
    }

    override suspend fun usarSinServidor(valor: Boolean) {
        contexto.almacen.edit { it[local] = valor }
    }

    override suspend fun guardarConfiguracion(configuracion: ConfiguracionLectura) {
        contexto.almacen.edit { it[config] = json.encodeToString(configuracion) }
    }
}
