package pe.cobroverificado.lector.ui.ajustes

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.cobroverificado.lector.ui.PrincipalViewModel
import pe.cobroverificado.lector.ui.comun.CabeceraSeccion
import pe.cobroverificado.lector.ui.comun.FilaAjuste
import pe.cobroverificado.lector.ui.comun.Hoja
import pe.cobroverificado.lector.ui.comun.FormularioVinculo
import androidx.compose.runtime.LaunchedEffect
import pe.cobroverificado.lector.ui.comun.InsigniaEstado
import androidx.compose.foundation.layout.Box
import pe.cobroverificado.lector.ui.comun.HojaConfirmacion
import pe.cobroverificado.lector.ui.comun.Tarjeta
import pe.cobroverificado.lector.ui.comun.Titulo
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import pe.cobroverificado.lector.ui.tema.MarcaSello

@Composable
fun PantallaAjustes(modelo: PrincipalViewModel, abrirApps: () -> Unit, relleno: PaddingValues) {
    val contexto = LocalContext.current
    val hapticos = LocalHapticFeedback.current
    val pausado by modelo.pausado.collectAsStateWithLifecycle()
    val configuracion by modelo.configuracion.collectAsStateWithLifecycle()
    val sesion by modelo.sesion.collectAsStateWithLifecycle()
    val acceso by modelo.accesoNotificaciones.collectAsStateWithLifecycle()
    val enviando by modelo.enviando.collectAsStateWithLifecycle()
    val pagos by modelo.pagos.collectAsStateWithLifecycle()
    var desvincular by remember { mutableStateOf(false) }
    var vincular by remember { mutableStateOf(false) }
    val estadoVinculo by modelo.vinculo.collectAsStateWithLifecycle()
    LaunchedEffect(sesion) { if (sesion != null) vincular = false }
    var privacidad by remember { mutableStateOf(false) }
    val apps = configuracion.billeteras.sumOf { it.paquetes.size }
    val pendientes = pagos.orEmpty().count { it.estado.name == "PENDIENTE" }
    val marca = LocalMarca.current
    val version = remember { runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }.getOrNull() ?: "" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = relleno.calculateTopPadding(), bottom = relleno.calculateBottomPadding() + 24.dp),
    ) {
        Titulo("Ajustes")

        CabeceraSeccion("Registro")
        Tarjeta {
            FilaAjuste(
                if (pausado) Iconos.Pausa else Iconos.Check,
                "Registrar pagos",
                if (pausado) "En pausa: los avisos nuevos no se anotan" else "Activo",
                tono = if (pausado) marca.alerta else marca.exito,
                alTocar = {
                    hapticos.performHapticFeedback(if (pausado) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    modelo.cambiarPausa(!pausado)
                },
                final = { Switch(checked = !pausado, onCheckedChange = null) },
            )
            Divisor()
            FilaAjuste(Iconos.Apps, "Apps de pago", "$apps ${if (apps == 1) "app activa" else "apps activas"}", tono = marca.exito, alTocar = abrirApps)
            Divisor()
            FilaAjuste(
                Iconos.Campana,
                "Acceso a notificaciones",
                if (acceso) "Permitido" else "Desactivado: no se registran pagos",
                tono = if (acceso) marca.alerta else marca.peligro,
                alTocar = { contexto.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
        }

        CabeceraSeccion("Servidor")
        if (sesion == null) Tarjeta {
            FilaAjuste(
                Iconos.Servidor,
                "Sin servidor",
                "Los cobros se guardan solo en este teléfono",
                tono = marca.info,
                alTocar = null,
            )
            Divisor()
            FilaAjuste(Iconos.Qr, "Vincular con un servidor", "Escanea el código QR o ingresa los datos", tono = marca.exito, alTocar = { vincular = true })
            Divisor()
            FilaAjuste(Iconos.Telefono, "Este teléfono", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}", tono = marca.info, alTocar = null)
        } else Tarjeta {
            FilaAjuste(
                Iconos.Sincronizar,
                "Enviar pendientes ahora",
                when {
                    enviando -> "Enviando…"
                    pendientes == 0 -> "No hay pagos por enviar"
                    pendientes == 1 -> "1 pago por enviar"
                    else -> "$pendientes pagos por enviar"
                },
                tono = marca.info,
                alTocar = {
                    hapticos.performHapticFeedback(HapticFeedbackType.Confirm)
                    modelo.sincronizarAhora()
                },
                final = null,
            )
            Divisor()
            FilaAjuste(Iconos.Servidor, "Servidor", sesion?.urlServidor ?: "Sin vincular", tono = marca.info, alTocar = null)
            Divisor()
            FilaAjuste(Iconos.Telefono, "Este teléfono", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}", tono = marca.info, alTocar = null)
            Divisor()
            FilaAjuste(
                Iconos.Desvincular,
                "Desvincular servidor",
                "El historial se queda en el teléfono",
                tinte = MaterialTheme.colorScheme.error,
                tono = marca.peligro,
                alTocar = { desvincular = true },
                final = null,
            )
        }

        CabeceraSeccion("Privacidad")
        Tarjeta {
            FilaAjuste(Iconos.Escudo, "Qué datos se envían", "El texto del aviso nunca sale del teléfono", tono = marca.privado, alTocar = { privacidad = true })
        }

        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarcaSello(Modifier.size(28.dp), marca.sello, marca.sobreSello, marca.ambar)
            Text("Cobro Verificado $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }

    if (vincular) {
        Hoja({ vincular = false; modelo.limpiarErrorVinculo() }, desplazable = true) {
            Text("Vincular con un servidor", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "Los cobros que ya tienes se enviarán al servidor apenas se vincule.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FormularioVinculo(modelo)
        }
    }
    if (desvincular) {
        HojaConfirmacion(
            "¿Desvincular el servidor?",
            "El teléfono deja de enviar cobros. El historial se queda aquí y los pagos ya enviados se conservan en el servidor.",
            "Desvincular",
            destructiva = true,
            confirmar = { modelo.desvincular() },
            cerrar = { desvincular = false },
        )
    }
    if (privacidad) {
        Hoja({ privacidad = false }) {
            Text("Qué datos se envían", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            listOf(
                "Solo se leen los avisos de las apps de pago que activaste.",
                "De cada pago recibido se guardan el monto, quién paga, la hora, el número de operación y la app de origen.",
                "Al servidor de tu negocio llegan esos datos y una huella cifrada del aviso, que evita registrar dos veces el mismo pago.",
                "El texto completo de la notificación nunca sale del teléfono, y los avisos de pagos que tú haces se ignoran.",
            ).forEach {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 2.dp)) { InsigniaEstado(Iconos.Check, marca.exito, 22.dp) }
                    Spacer(Modifier.size(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Divisor() {
    HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}
