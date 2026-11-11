package pe.cobroverificado.lector.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.cobroverificado.lector.ui.inicio.PantallaInicio
import pe.cobroverificado.lector.ui.tema.TemaLector

class PrincipalActivity : ComponentActivity() {

    private val modelo: PrincipalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        arranque.setKeepOnScreenCondition { modelo.listo.value == null }
        val claro = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(claro, claro)
        setContent {
            TemaLector {
                val listo by modelo.listo.collectAsStateWithLifecycle()
                val acceso by modelo.accesoNotificaciones.collectAsStateWithLifecycle()
                when {
                    listo == null -> Unit
                    listo == true && acceso -> Raiz(modelo)
                    else -> PantallaInicio(modelo, listo == true)
                }
            }
        }
        if (savedInstanceState == null) abrirEnlace(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirEnlace(intent)
    }

    private fun abrirEnlace(intent: Intent?) {
        val datos = intent?.data ?: return
        if (datos.scheme == "cobroverificado") modelo.vincularConEnlace(datos.toString())
    }

    override fun onResume() {
        super.onResume()
        modelo.revisarAcceso()
    }
}
