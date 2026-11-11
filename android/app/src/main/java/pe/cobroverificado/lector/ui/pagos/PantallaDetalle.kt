package pe.cobroverificado.lector.ui.pagos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.comun.Avatar
import pe.cobroverificado.lector.ui.comun.CabeceraSeccion
import pe.cobroverificado.lector.ui.comun.ChipBilletera
import pe.cobroverificado.lector.ui.comun.HojaConfirmacion
import pe.cobroverificado.lector.ui.comun.IconoApp
import pe.cobroverificado.lector.ui.comun.LogoBilletera
import pe.cobroverificado.lector.ui.comun.LocalConServidor
import pe.cobroverificado.lector.ui.comun.Tarjeta
import pe.cobroverificado.lector.ui.comun.Tiempo
import pe.cobroverificado.lector.ui.comun.contenedorCompartido
import pe.cobroverificado.lector.ui.comun.leerApp
import pe.cobroverificado.lector.ui.comun.nombreBilletera
import pe.cobroverificado.lector.ui.tema.Cifras
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import java.math.BigDecimal
import java.time.Instant

fun resumenTexto(r: PagoRegistrado): String = buildString {
    appendLine("Pago recibido")
    appendLine("Monto: S/ ${soles(r.pago.monto)}")
    r.pago.remitente?.let { appendLine("De: $it") }
    appendLine("Billetera: ${nombreBilletera(r.pago.billetera)}")
    appendLine("Fecha: ${Tiempo.numerica(r.pago.fechaHora.atZoneSameInstant(ZONA))}")
    r.pago.numeroOperacion?.let { appendLine("Operación: $it") }
}.trimEnd()

private fun copiar(contexto: Context, etiqueta: String, texto: String) {
    contexto.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(etiqueta, texto))
}

@Composable
fun PantallaDetalle(modelo: PrincipalViewModel, id: Long, ahora: Instant, abrir: (Long) -> Unit, volver: () -> Unit) {
    val pagos by modelo.pagos.collectAsStateWithLifecycle()
    val registro = pagos?.firstOrNull { it.id == id }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().contenedorCompartido("pago-$id", MaterialTheme.shapes.extraLarge),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = volver) { Icon(Iconos.Atras, contentDescription = "Volver") }
                Text("Detalle del pago", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (registro != null) Contenido(modelo, registro, pagos.orEmpty(), ahora, abrir)
        }
    }
}

@Composable
private fun Contenido(modelo: PrincipalViewModel, r: PagoRegistrado, todos: List<PagoRegistrado>, ahora: Instant, abrir: (Long) -> Unit) {
    val contexto = LocalContext.current
    val hapticos = LocalHapticFeedback.current
    val marca = LocalMarca.current
    val c = MaterialTheme.colorScheme
    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val pago = r.pago
    val nombre = pago.remitente ?: "Remitente sin leer"
    val estado = estadoDe(r)
    var confirmarReporte by remember { mutableStateOf(false) }
    val app by produceState<String?>(null, pago.paquete) {
        value = pago.paquete?.let { p -> withContext(Dispatchers.IO) { leerApp(contexto, p)?.nombre ?: p } }
    }
    val historial = remember(todos, pago.remitente) {
        pago.remitente?.let { n -> todos.filter { it.pago.remitente.equals(n, ignoreCase = true) } }.orEmpty()
    }
    val copiarConAviso: (String, String) -> Unit = { etiqueta, texto ->
        copiar(contexto, etiqueta, texto)
        hapticos.performHapticFeedback(HapticFeedbackType.Confirm)
        alcance.launch {
            avisos.currentSnackbarData?.dismiss()
            avisos.showSnackbar("$etiqueta copiado")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(nombre, 72.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(nombre, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "+ S/ ${soles(pago.monto)}",
                        style = MaterialTheme.typography.displaySmall.merge(Cifras),
                        textDecoration = if (r.reportado) TextDecoration.LineThrough else null,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChipBilletera(pago.billetera)
                        Text(momentoRelativo(pago.fechaHora.toInstant(), ahora, ZONA), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                }

                if (r.reportado) {
                    Row(
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth()
                            .clip(MaterialTheme.shapes.large).background(marca.peligro.fondo)
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Iconos.Alerta, contentDescription = null, tint = marca.peligro.texto, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Marcado con un problema. No suma en los totales.", style = MaterialTheme.typography.bodyMedium, color = marca.peligro.texto, modifier = Modifier.weight(1f))
                        TextButton(onClick = { modelo.marcarReportado(r.id, false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Restaurar", color = marca.peligro.texto)
                        }
                    }
                }

                Tarjeta {
                    Fila("Fecha y hora") {
                        Valor(Tiempo.completa(pago.fechaHora.atZoneSameInstant(ZONA)).replaceFirstChar { it.uppercase() })
                    }
                    Divisor()
                    Fila(
                        "Operación",
                        final = pago.numeroOperacion?.let { op ->
                            { IconButton(onClick = { copiarConAviso("Número de operación", op) }) { Icon(Iconos.Copiar, contentDescription = "Copiar número de operación", tint = c.primary) } }
                        },
                    ) { Valor(pago.numeroOperacion ?: "No aparece en el aviso") }
                    Divisor()
                    // los avisos de prueba salen de la shell de android: se muestra la billetera, que es lo que el usuario reconoce
                    val dePrueba = pago.paquete == null || pago.paquete == "com.android.shell"
                    Fila("App de origen", final = { if (dePrueba) LogoBilletera(pago.billetera, 36.dp) else IconoApp(pago.paquete, 36.dp) { LogoBilletera(pago.billetera, 36.dp) } }) {
                        Valor(if (dePrueba) nombreBilletera(pago.billetera) else app ?: nombreBilletera(pago.billetera))
                    }
                    Divisor()
                    if (!LocalConServidor.current) {
                        Fila("Envío al servidor") {
                            Valor("Sin servidor")
                            Text("Se guarda solo en este teléfono. Vincula un servidor en Ajustes para enviarlo.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else Fila("Envío al servidor") {
                        Row(
                            Modifier.padding(top = 4.dp).clip(CircleShape).background(estado.tono.fondo).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(estado.icono, contentDescription = null, tint = estado.tono.texto, modifier = Modifier.size(16.dp))
                            Text(estado.texto, style = MaterialTheme.typography.labelLarge, color = estado.tono.texto)
                        }
                        val detalle = when {
                            r.estado == EstadoSincronizacion.ENVIADO && r.sincronizadoEn != null -> "El ${Tiempo.mediana(r.sincronizadoEn.atZone(ZONA))}"
                            r.ultimoError != null -> r.ultimoError.replaceFirstChar { it.uppercase() } + if (r.intentos > 0) ", ${r.intentos} intentos" else ""
                            else -> null
                        }
                        detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
                        if (r.estado != EstadoSincronizacion.ENVIADO) {
                            OutlinedButton(onClick = { modelo.reintentar(r.id) }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 44.dp)) {
                                Icon(Iconos.Sincronizar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reintentar envío")
                            }
                        }
                    }
                }

                if (historial.size > 1) {
                    val total = soles(historial.filterNot { it.reportado }.fold(BigDecimal.ZERO) { s, x -> s + x.pago.monto })
                    CabeceraSeccion("Historial con ${nombre.substringBefore(' ')}")
                    Tarjeta {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                            Resumen("Pagos", "${historial.size}", Modifier.weight(0.7f))
                            Resumen("Total", "S/ $total", Modifier.weight(1.3f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val otros = historial.filter { it.id != r.id }.take(3)
                    otros.forEachIndexed { i, otro -> FilaPago(otro, ahora, i == 0, i == otros.lastIndex, abrir) }
                }
            }

            Surface(color = c.surfaceContainerLowest, tonalElevation = 0.dp, shadowElevation = 12.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { copiarConAviso("Detalle del pago", resumenTexto(r)) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(Iconos.Copiar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copiar", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                val intento = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, resumenTexto(r))
                                contexto.startActivity(Intent.createChooser(intento, "Compartir pago"))
                            },
                            shape = MaterialTheme.shapes.medium,
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(Iconos.Exportar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Compartir", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                    if (!r.reportado) {
                        TextButton(
                            onClick = { confirmarReporte = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Iconos.Error, contentDescription = null, tint = c.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reportar un problema", color = c.error)
                        }
                    }
                }
            }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }

    if (confirmarReporte) {
        HojaConfirmacion(
            "¿Marcar este pago con un problema?",
            "Queda guardado pero deja de sumar en los totales y reportes de este teléfono. Puedes restaurarlo cuando quieras.",
            "Marcar con problema",
            destructiva = true,
            confirmar = {
                hapticos.performHapticFeedback(HapticFeedbackType.Reject)
                modelo.marcarReportado(r.id, true)
            },
            cerrar = { confirmarReporte = false },
        )
    }
}

@Composable
private fun Fila(etiqueta: String, final: (@Composable () -> Unit)? = null, valor: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = if (final != null) 12.dp else 20.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            valor()
        }
        final?.let {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { it() }
        }
    }
}

@Composable
private fun Valor(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyLarge.merge(Cifras))
}

@Composable
private fun Divisor() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun Resumen(etiqueta: String, valor: String, modifier: Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleMedium.merge(Cifras), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
