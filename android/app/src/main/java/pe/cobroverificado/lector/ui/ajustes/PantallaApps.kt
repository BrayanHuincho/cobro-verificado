package pe.cobroverificado.lector.ui.ajustes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.ConfiguracionLectura
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.comun.AppInstalada
import pe.cobroverificado.lector.ui.comun.CabeceraSeccion
import pe.cobroverificado.lector.ui.comun.FilaAjuste
import pe.cobroverificado.lector.ui.comun.Hoja
import pe.cobroverificado.lector.ui.comun.IconoApp
import pe.cobroverificado.lector.ui.comun.Tarjeta
import pe.cobroverificado.lector.ui.comun.appsLanzables
import pe.cobroverificado.lector.ui.comun.leerApp
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import pe.cobroverificado.lector.ui.comun.LogoBilletera
import pe.cobroverificado.lector.ui.comun.SelectorBilletera
import pe.cobroverificado.lector.ui.tema.LocalMarca
import androidx.compose.foundation.background
import pe.cobroverificado.lector.ui.tema.Iconos

private const val YAPE = "com.bcp.innovacxion.yapeapp"

@Composable
private fun Monograma(nombre: String) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (nombre.trim().contains(' ')) pe.cobroverificado.lector.ui.pagos.iniciales(nombre) else nombre.trim().take(2).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private val nombresConocidos = mapOf(
    "com.bcp.innovacxion.yapeapp" to "Yape",
    "pe.com.interbank.mobilebanking" to "Interbank",
    "com.bbva.nxt_peru" to "BBVA Perú",
    "pe.com.scotiabank.blpm.android.client" to "Scotiabank Perú",
)

@Composable
fun PantallaApps(modelo: PrincipalViewModel, volver: () -> Unit) {
    val contexto = LocalContext.current
    val configuracion by modelo.configuracion.collectAsStateWithLifecycle()
    var agregarA by remember { mutableStateOf<Billetera?>(null) }
    var porPaquete by remember { mutableStateOf(false) }
    val filas by produceState<Map<Billetera, List<Pair<String, AppInstalada?>>>?>(null, configuracion) {
        value = withContext(Dispatchers.IO) {
            Billetera.entries.associateWith { b ->
                (ConfiguracionLectura.porDefecto.paquetes(b) + configuracion.paquetes(b)).distinct().map { it to leerApp(contexto, it) }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = volver) { Icon(Iconos.Atras, contentDescription = "Volver") }
                Text("Apps de pago", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Activa las apps cuyos avisos de pago recibido quieres anotar. Las demás notificaciones se ignoran.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                Billetera.entries.forEach { b ->
                    item(key = "cab-$b") {
                        Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LogoBilletera(b, 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(if (b == Billetera.YAPE) "Yape" else "Plin, apps bancarias", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        }
                    }
                    item(key = "lista-$b") {
                        Tarjeta {
                            filas?.get(b).orEmpty().forEachIndexed { i, (paquete, app) ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                FilaApp(paquete, app, b, paquete in configuracion.paquetes(b)) { modelo.cambiarPaquete(b, paquete, it) }
                            }
                            HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            FilaAjuste(Iconos.Agregar, "Agregar otra app instalada", tinte = MaterialTheme.colorScheme.primary, tono = LocalMarca.current.exito, alTocar = { agregarA = b }, final = null)
                        }
                    }
                }
                item(key = "avanzado") {
                    CabeceraSeccion("Avanzado")
                    Tarjeta {
                        FilaAjuste(Iconos.Codigo, "Agregar por nombre de paquete", "Para apps que no aparecen en la lista", tono = LocalMarca.current.info, alTocar = { porPaquete = true })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    agregarA?.let { b -> HojaAgregarApp(b, { agregarA = null }) { modelo.cambiarPaquete(b, it, true) } }
    if (porPaquete) HojaPaquete({ porPaquete = false }) { b, p -> modelo.cambiarPaquete(b, p, true) }
}

@Composable
private fun FilaApp(paquete: String, app: AppInstalada?, billetera: Billetera, activa: Boolean, cambiar: (Boolean) -> Unit) {
    val hapticos = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .toggleable(activa, role = Role.Switch) {
                hapticos.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                cambiar(it)
            }
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nombre = app?.nombre ?: nombresConocidos[paquete] ?: paquete
        IconoApp(paquete) {
            if (paquete == YAPE) LogoBilletera(Billetera.YAPE, 40.dp) else Monograma(nombre)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app?.nombre ?: nombresConocidos[paquete] ?: paquete, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (app == null) "No instalada en este teléfono" else paquete,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = activa, onCheckedChange = null)
    }
}

@Composable
private fun HojaAgregarApp(billetera: Billetera, cerrar: () -> Unit, agregar: (String) -> Unit) {
    val contexto = LocalContext.current
    var buscado by remember { mutableStateOf("") }
    val apps by produceState<List<AppInstalada>?>(null) { value = withContext(Dispatchers.IO) { appsLanzables(contexto) } }
    val visibles = apps.orEmpty().filter { buscado.isBlank() || it.nombre.contains(buscado, true) || it.paquete.contains(buscado, true) }
    Hoja(cerrar) {
        Text("Agregar a ${nombreBilletera(billetera)}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = buscado,
            onValueChange = { buscado = it },
            placeholder = { Text("Buscar app instalada") },
            leadingIcon = { Icon(Iconos.Buscar, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (apps == null) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(visibles, key = { it.paquete }) { app ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .clickable { agregar(app.paquete); cerrar() }
                        .heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    app.icono?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(app.paquete, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Iconos.Agregar, contentDescription = "Agregar ${app.nombre}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun HojaPaquete(cerrar: () -> Unit, agregar: (Billetera, String) -> Unit) {
    var paquete by remember { mutableStateOf("") }
    var billetera by remember { mutableStateOf(Billetera.YAPE) }
    val valido = Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z_][\\w]*)+$").matches(paquete.trim())
    Hoja(cerrar) {
        Text("Agregar por nombre de paquete", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text("Útil para pruebas o apps que no aparecen en el buscador.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = paquete,
            onValueChange = { paquete = it },
            label = { Text("Nombre de paquete") },
            placeholder = { Text("com.ejemplo.app") },
            singleLine = true,
            isError = paquete.isNotBlank() && !valido,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("Billetera", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 10.dp))
        SelectorBilletera(billetera, { if (it != null) billetera = it })
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { agregar(billetera, paquete.trim()); cerrar() },
            enabled = valido,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Agregar", style = MaterialTheme.typography.labelLarge) }
    }
}
