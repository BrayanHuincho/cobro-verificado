package pe.cobroverificado.lector.ui.comun

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.ui.pagos.iniciales
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import pe.cobroverificado.lector.ui.tema.Tono
import pe.cobroverificado.lector.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalTransicion = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalVisibilidad = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

// sin servidor la app es un registro local: se ocultan los estados de envío
val LocalConServidor = staticCompositionLocalOf { true }

// transformación de contenedor entre la fila y el detalle; sin escena compartida no hace nada
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.contenedorCompartido(clave: String, forma: Shape): Modifier {
    val transicion = LocalTransicion.current ?: return this
    val visibilidad = LocalVisibilidad.current ?: return this
    return with(transicion) {
        this@contenedorCompartido.sharedBounds(
            rememberSharedContentState(clave),
            visibilidad,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(forma),
        )
    }
}

fun nombreBilletera(b: Billetera) = if (b == Billetera.YAPE) "Yape" else "Plin"

@Composable
fun ChipBilletera(billetera: Billetera, modifier: Modifier = Modifier) {
    val marca = LocalMarca.current
    val tono = if (billetera == Billetera.YAPE) marca.yape else marca.plin
    Row(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(tono.fondo)
            .padding(start = 3.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LogoBilletera(billetera, 15.dp)
        Text(nombreBilletera(billetera), style = MaterialTheme.typography.labelSmall, color = tono.texto)
    }
}

@Composable
fun LogoBilletera(billetera: Billetera, tamano: Dp) {
    Image(
        painterResource(if (billetera == Billetera.YAPE) R.drawable.logo_yape else R.drawable.logo_plin),
        contentDescription = null,
        modifier = Modifier.size(tamano).clip(RoundedCornerShape(tamano * 0.28f)),
    )
}

@Composable
fun InsigniaEstado(icono: ImageVector, tono: Tono, tamano: Dp = 20.dp) {
    Box(Modifier.size(tamano).clip(CircleShape).background(tono.fondo), contentAlignment = Alignment.Center) {
        Icon(icono, contentDescription = null, tint = tono.texto, modifier = Modifier.size(tamano * 0.66f))
    }
}

// selector de billetera en tarjetas; tocar la elegida otra vez la suelta cuando se permite ninguna
@Composable
fun SelectorBilletera(elegida: Billetera?, elegir: (Billetera?) -> Unit, permitirNinguna: Boolean = false) {
    val hapticos = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Billetera.entries.forEach { b ->
            val activa = b == elegida
            val borde by animateColorAsState(if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "borde")
            Box(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (activa) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(if (activa) 2.dp else 1.dp, borde, MaterialTheme.shapes.medium)
                    .selectable(activa, role = Role.RadioButton) {
                        hapticos.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        elegir(if (activa && permitirNinguna) null else b)
                    }
                    .heightIn(min = 64.dp)
                    .padding(14.dp),
            ) {
                Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                    LogoBilletera(b, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(nombreBilletera(b), style = MaterialTheme.typography.titleMedium)
                }
                if (activa) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Iconos.Check, contentDescription = "Elegida", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

// control segmentado con una píldora que se desliza bajo la opción elegida
@Composable
fun Segmentado(opciones: List<String>, indice: Int, elegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hapticos = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val ancho = maxWidth / opciones.size
        val x by animateDpAsState(ancho * indice, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "segmento")
        Box(
            Modifier.offset(x = x).width(ancho).fillMaxHeight()
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            opciones.forEachIndexed { i, texto ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(i == indice, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            if (i != indice) hapticos.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            elegir(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        texto,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == indice) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun Avatar(nombre: String, tamano: Dp = 44.dp) {
    val paleta = LocalMarca.current.avatares
    val (fondo, texto) = paleta[Math.floorMod(nombre.lowercase().hashCode(), paleta.size)]
    Box(Modifier.size(tamano).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Text(
            iniciales(nombre),
            style = if (tamano > 50.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            color = texto,
        )
    }
}

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
fun CabeceraSeccion(texto: String, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        accion?.invoke()
    }
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, LocalMarca.current.borde, MaterialTheme.shapes.large),
        content = contenido,
    )
}

@Composable
fun FilaAjuste(
    icono: ImageVector,
    titulo: String,
    detalle: String? = null,
    tinte: Color = MaterialTheme.colorScheme.onSurface,
    tono: Tono? = null,
    alTocar: (() -> Unit)? = null,
    final: @Composable (() -> Unit)? = { if (alTocar != null) Icon(Iconos.Siguiente, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(tono?.fondo ?: MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icono, contentDescription = null, tint = tono?.texto ?: tinte, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = tinte)
            detalle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        final?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

@Composable
fun Esqueleto(filas: Int = 4) {
    val pulso by rememberInfiniteTransition(label = "esqueleto").animateFloat(
        0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulso",
    )
    val tono = MaterialTheme.colorScheme.surfaceContainerHigh
    Tarjeta(Modifier.alpha(pulso)) {
        repeat(filas) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(tono))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(CircleShape).background(tono))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(CircleShape).background(tono))
                }
                Box(Modifier.width(64.dp).height(14.dp).clip(CircleShape).background(tono))
            }
        }
    }
}

@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, texto: String, accion: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        accion?.let {
            Spacer(Modifier.height(4.dp))
            it()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hoja(cerrar: () -> Unit, desplazable: Boolean = false, contenido: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = cerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.then(if (desplazable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            content = contenido,
        )
    }
}

@Composable
fun HojaConfirmacion(
    titulo: String,
    texto: String,
    accion: String,
    destructiva: Boolean,
    confirmar: () -> Unit,
    cerrar: () -> Unit,
) {
    Hoja(cerrar) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { confirmar(); cerrar() },
            shape = MaterialTheme.shapes.medium,
            colors = if (destructiva) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(accion, style = MaterialTheme.typography.labelLarge) }
        TextButton(onClick = cerrar, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancelar") }
    }
}

data class AppInstalada(val paquete: String, val nombre: String, val icono: ImageBitmap?)

fun leerApp(contexto: android.content.Context, paquete: String): AppInstalada? = runCatching {
    val pm = contexto.packageManager
    val info = pm.getApplicationInfo(paquete, 0)
    AppInstalada(paquete, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap())
}.getOrNull()

fun appsLanzables(contexto: android.content.Context): List<AppInstalada> {
    val pm = contexto.packageManager
    val intento = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intento, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != contexto.packageName }
        .mapNotNull { leerApp(contexto, it) }
        .sortedBy { it.nombre.lowercase() }
}

@Composable
fun IconoApp(paquete: String?, tamano: Dp = 40.dp, respaldo: (@Composable () -> Unit)? = null) {
    val contexto = LocalContext.current
    val app by produceState<AppInstalada?>(null, paquete) {
        value = paquete?.let { withContext(Dispatchers.IO) { leerApp(contexto, it) } }
    }
    val icono = app?.icono
    if (icono != null) {
        Image(icono, contentDescription = null, modifier = Modifier.size(tamano).clip(MaterialTheme.shapes.small))
    } else if (respaldo != null) {
        respaldo()
    } else {
        Box(
            Modifier.size(tamano).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Iconos.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}

data class Metrica(val icono: ImageVector, val etiqueta: String, val valor: String, val peso: Float = 1f)

@Composable
fun FilaMetricas(metricas: List<Metrica>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        metricas.forEachIndexed { i, m ->
            if (i > 0) androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Column(Modifier.weight(m.peso).padding(horizontal = 14.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(m.icono, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(m.etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text(m.valor, style = MaterialTheme.typography.titleMedium.merge(pe.cobroverificado.lector.ui.tema.Cifras), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
