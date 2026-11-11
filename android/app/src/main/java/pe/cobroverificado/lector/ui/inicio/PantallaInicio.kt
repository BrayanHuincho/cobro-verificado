package pe.cobroverificado.lector.ui.inicio

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import pe.cobroverificado.lector.ui.comun.FormularioVinculo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import pe.cobroverificado.lector.ui.tema.MarcaSello

@Composable
fun PantallaInicio(modelo: PrincipalViewModel, vinculado: Boolean) {
    val acceso by modelo.accesoNotificaciones.collectAsStateWithLifecycle()
    var leido by rememberSaveable { mutableStateOf(vinculado) }
    val paso = when {
        !leido -> 0
        !acceso -> 1
        else -> 2
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Encabezado(paso)
            AnimatedContent(
                targetState = paso,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                },
                label = "paso",
                modifier = Modifier.weight(1f),
            ) { actual ->
                when (actual) {
                    0 -> Explicacion { leido = true }
                    1 -> PedirAcceso()
                    else -> Eleccion(modelo)
                }
            }
        }
    }
}

@Composable
private fun Encabezado(paso: Int) {
    val marca = LocalMarca.current
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarcaSello(Modifier.size(28.dp), marca.sello, marca.sobreSello, marca.ambar)
            Text("Cobro Verificado", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Paso ${paso + 1} de 3" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val color by animateColorAsState(
                    if (i <= paso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "segmento",
                )
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun Paso(
    ilustracion: @Composable (Modifier) -> Unit,
    titulo: String,
    accion: String,
    alAccionar: () -> Unit,
    enCurso: Boolean = false,
    aviso: @Composable () -> Unit = {},
    contenido: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ilustracion(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp))
            Text(titulo, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            contenido()
        }
        Box(Modifier.padding(horizontal = 24.dp)) { aviso() }
        Button(
            onClick = alAccionar,
            enabled = !enCurso,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 56.dp),
        ) {
            if (enCurso) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(accion, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Punto(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Iconos.Enviado, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Explicacion(continuar: () -> Unit) {
    Paso({ IlustracionAviso(it) }, "Anota solo los pagos que recibes, sin escribir nada", "Continuar", continuar) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Punto("Lee únicamente los avisos de las apps de pago que elijas.")
            Punto("Guarda el monto, quién paga, la hora y el número de operación.")
            Punto("Al servidor de tu negocio solo llegan esos datos. El texto del aviso no sale del teléfono.")
            Punto("Puedes pausar o desvincular cuando quieras.")
        }
    }
}

@Composable
private fun PedirAcceso() {
    val contexto = LocalContext.current
    Paso(
        { IlustracionAcceso(it) },
        "Activa el acceso a notificaciones",
        "Abrir ajustes",
        { contexto.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
    ) {
        Text(
            "Android lo pide una sola vez. En la lista que se abre, toca Cobro Verificado y confirma. Al volver, sigues aquí.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Eleccion(modelo: PrincipalViewModel) {
    var conServidor by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IlustracionVinculo(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp))
            Text(
                if (conServidor) "Vincula este teléfono con tu servidor" else "Todo listo para registrar tus cobros",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (conServidor) {
                Text(
                    "Escanea el código QR que muestra tu servidor o escribe la dirección y el código de vinculación.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormularioVinculo(modelo)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Punto("Funciona solo en este teléfono: guarda tus cobros, arma reportes y los exporta en PDF o CSV.")
                    Punto("Si tu negocio tiene un servidor, puedes vincularlo ahora o más tarde desde Ajustes.")
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { modelo.empezarSinServidor() },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Empezar sin servidor", style = MaterialTheme.typography.labelLarge) }
            if (!conServidor) {
                OutlinedButton(
                    onClick = { conServidor = true },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Vincular con un servidor", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
