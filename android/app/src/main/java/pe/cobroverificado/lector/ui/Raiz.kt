package pe.cobroverificado.lector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pe.cobroverificado.lector.ui.ajustes.PantallaAjustes
import pe.cobroverificado.lector.ui.ajustes.PantallaApps
import pe.cobroverificado.lector.ui.comun.LocalTransicion
import pe.cobroverificado.lector.ui.comun.LocalConServidor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.cobroverificado.lector.ui.comun.LocalVisibilidad
import pe.cobroverificado.lector.ui.pagos.PantallaDetalle
import pe.cobroverificado.lector.ui.pagos.PantallaHistorial
import pe.cobroverificado.lector.ui.reportes.PantallaReportes
import pe.cobroverificado.lector.ui.resumen.PantallaResumen
import pe.cobroverificado.lector.ui.tema.Iconos
import pe.cobroverificado.lector.ui.tema.LocalMarca
import java.time.Instant

private enum class Pestana(val etiqueta: String, val icono: ImageVector, val lleno: ImageVector) {
    INICIO("Inicio", Iconos.Inicio, Iconos.InicioLleno),
    PAGOS("Pagos", Iconos.Pagos, Iconos.PagosLleno),
    REPORTES("Reportes", Iconos.Reportes, Iconos.ReportesLleno),
    AJUSTES("Ajustes", Iconos.Ajustes, Iconos.AjustesLleno),
}

private const val PRINCIPAL = ""
private const val APPS = "apps"
private val ALTO_BARRA = 64.dp

// destino vacío es la vista con pestañas; "apps" y "pago:<id>" se apilan encima con su propia transición
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Raiz(modelo: PrincipalViewModel) {
    var destino by rememberSaveable { mutableStateOf(PRINCIPAL) }
    var pestana by rememberSaveable { mutableStateOf(Pestana.INICIO) }
    val ahora by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    BackHandler(enabled = destino != PRINCIPAL) { destino = PRINCIPAL }
    BackHandler(enabled = destino == PRINCIPAL && pestana != Pestana.INICIO) { pestana = Pestana.INICIO }
    val abrir: (Long) -> Unit = { destino = "pago:$it" }
    val sesion by modelo.sesion.collectAsStateWithLifecycle()

    SharedTransitionLayout {
        CompositionLocalProvider(LocalTransicion provides this, LocalConServidor provides (sesion != null)) {
            AnimatedContent(
                targetState = destino,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "destino",
            ) { actual ->
                CompositionLocalProvider(LocalVisibilidad provides this) {
                    when {
                        actual == APPS -> PantallaApps(modelo) { destino = PRINCIPAL }
                        actual.startsWith("pago:") -> PantallaDetalle(modelo, actual.removePrefix("pago:").toLong(), ahora, abrir) { destino = PRINCIPAL }
                        else -> ConPestanas(modelo, pestana, { pestana = it }, ahora, abrir) { destino = APPS }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConPestanas(
    modelo: PrincipalViewModel,
    pestana: Pestana,
    elegir: (Pestana) -> Unit,
    ahora: Instant,
    abrir: (Long) -> Unit,
    abrirApps: () -> Unit,
) {
    val estados = rememberSaveableStateHolder()
    val densidad = LocalDensity.current
    val arriba = with(densidad) { WindowInsets.statusBars.getTop(this).toDp() }
    val abajo = with(densidad) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val relleno = PaddingValues(top = arriba, bottom = abajo + ALTO_BARRA + 20.dp)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = pestana,
            transitionSpec = {
                val direccion = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { direccion * it / 14 }) togetherWith fadeOut(tween(90))
            },
            label = "pestana",
            modifier = Modifier.fillMaxSize(),
        ) { actual ->
            estados.SaveableStateProvider(actual.name) {
                when (actual) {
                    Pestana.INICIO -> PantallaResumen(modelo, ahora, abrir, { elegir(Pestana.PAGOS) }, relleno)
                    Pestana.PAGOS -> PantallaHistorial(modelo, ahora, abrir, relleno)
                    Pestana.REPORTES -> PantallaReportes(modelo, ahora, relleno)
                    Pestana.AJUSTES -> PantallaAjustes(modelo, abrirApps, relleno)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(arriba).background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)))
        BarraFlotante(
            pestana,
            elegir,
            Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = abajo + 12.dp),
        )
    } }
}

// barra flotante opaca con sombra; el indicador es una píldora suave que se desliza entre pestañas
@Composable
private fun BarraFlotante(pestana: Pestana, elegir: (Pestana) -> Unit, modifier: Modifier) {
    val hapticos = LocalHapticFeedback.current
    val c = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(ALTO_BARRA)
            .shadow(18.dp, forma, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(forma)
            .background(c.surfaceContainerLowest)
            .border(1.dp, LocalMarca.current.borde, forma)
            .selectableGroup(),
    ) {
        val ancho = maxWidth / Pestana.entries.size
        val desplazamiento by animateDpAsState(
            ancho * pestana.ordinal,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "indicador",
        )
        Box(
            Modifier
                .offset(x = desplazamiento)
                .width(ancho)
                .fillMaxHeight()
                .padding(6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.primary.copy(alpha = 0.10f)),
        )
        Row(Modifier.fillMaxSize()) {
            Pestana.entries.forEach { p ->
                val elegida = p == pestana
                val color by animateColorAsState(if (elegida) c.primary else c.onSurfaceVariant, label = "color")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = elegida,
                            onClick = {
                                if (!elegida) hapticos.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                elegir(p)
                            },
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Crossfade(elegida, label = "icono") { lleno ->
                        Icon(if (lleno) p.lleno else p.icono, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        p.etiqueta,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                        ),
                        color = color,
                    )
                }
            }
        }
    }
}
