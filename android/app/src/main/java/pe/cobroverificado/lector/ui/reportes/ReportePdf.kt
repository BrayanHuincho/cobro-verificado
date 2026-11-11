package pe.cobroverificado.lector.ui.reportes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import pe.cobroverificado.lector.R
import java.io.File

private const val ANCHO = 595f
private const val ALTO = 842f
private const val MARGEN = 40f
private const val PIE = 48f
private const val FILA = 18f

private const val VERDE = 0xFF0E6B53.toInt()
private const val TINTA = 0xFF15201C.toInt()
private const val SUAVE = 0xFF5B6863.toInt()
private const val LINEA = 0xFFE3E8E5.toInt()
private const val BORDE = 0xFFD5DCD8.toInt()
private const val AMBAR = 0xFFF2C14E.toInt()

private class Columna(val titulo: String, val ancho: Float, val derecha: Boolean = false)

// se compone dos veces: la primera solo mide para saber cuántas páginas habrá y poder escribir "Página X de Y"
class ReportePdf(private val contexto: Context) {

    private val base: Typeface = ResourcesCompat.getFont(contexto, R.font.onest) ?: Typeface.DEFAULT
    private fun peso(p: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, p, false) else Typeface.create(base, if (p >= 600) Typeface.BOLD else Typeface.NORMAL)

    private val regular = peso(400)
    private val medio = peso(500)
    private val fuerte = peso(650)
    private val logos = mapOf(
        "Yape" to BitmapFactory.decodeResource(contexto.resources, R.drawable.logo_yape),
        "Plin" to BitmapFactory.decodeResource(contexto.resources, R.drawable.logo_plin),
    )

    fun generar(d: DatosPdf, dispositivo: String, destino: File) {
        val paginas = Composicion(null, d, dispositivo, 0).apply { componer() }.numero
        val doc = PdfDocument()
        Composicion(doc, d, dispositivo, paginas).componer()
        destino.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private inner class Composicion(val doc: PdfDocument?, val d: DatosPdf, val dispositivo: String, val total: Int) {
        var numero = 0
        var y = 0f
        private var pagina: PdfDocument.Page? = null
        private val c: Canvas? get() = pagina?.canvas

        fun componer() {
            nueva()
            encabezado()
            resumen()
            seccion("Por billetera")
            tabla(
                listOf(Columna("Billetera", 215f), Columna("Pagos", 100f, true), Columna("Monto (S/)", 120f, true), Columna("Participación", 80f, true)),
                d.billeteras, null, conLogo = true,
            )
            if (d.dias.isNotEmpty()) {
                seccion("Por día")
                tabla(
                    listOf(Columna("Fecha", 135f), Columna("Pagos", 70f, true), Columna("Yape (S/)", 100f, true), Columna("Plin (S/)", 100f, true), Columna("Total (S/)", 110f, true)),
                    d.dias, d.totalDias,
                )
            }
            seccion("Detalle de pagos")
            if (d.pagos.isEmpty()) {
                texto("No hay pagos en este periodo.", MARGEN, y + 14f, 9.5f, regular, SUAVE)
                y += 24f
            } else {
                tabla(
                    listOf(Columna("Fecha y hora", 120f), Columna("Remitente", 150f), Columna("Billetera", 65f), Columna("Operación", 85f), Columna("Monto (S/)", 95f, true)),
                    d.pagos, d.totalPagos,
                )
            }
            cerrar()
        }

        private fun nueva() {
            cerrar()
            numero++
            pagina = doc?.startPage(PdfDocument.PageInfo.Builder(ANCHO.toInt(), ALTO.toInt(), numero).create())
            y = MARGEN
            if (numero > 1) {
                sello(MARGEN + 6f, y + 6f, 6f)
                texto("Cobro Verificado", MARGEN + 17f, y + 9f, 8.5f, fuerte, TINTA)
                texto("Reporte de pagos recibidos, ${d.periodo}", ANCHO - MARGEN, y + 9f, 8.5f, regular, SUAVE, derecha = true)
                y += 18f
                c?.drawLine(MARGEN, y, ANCHO - MARGEN, y, pintura(VERDE).apply { strokeWidth = 0.8f })
                y += 14f
            }
        }

        private fun cerrar() {
            val p = pagina ?: run { return }
            linea(ALTO - PIE + 8f)
            texto("Cobro Verificado. Generado el ${d.generado}", MARGEN, ALTO - PIE + 24f, 8f, regular, SUAVE)
            texto("Página $numero de $total", ANCHO - MARGEN, ALTO - PIE + 24f, 8f, medio, SUAVE, derecha = true)
            doc?.finishPage(p)
            pagina = null
        }

        private fun espacio(alto: Float): Boolean {
            if (y + alto <= ALTO - PIE) return false
            nueva()
            return true
        }

        private fun encabezado() {
            sello(MARGEN + 9f, MARGEN + 9f, 9f)
            texto("Cobro Verificado", MARGEN + 24f, MARGEN + 13f, 11f, fuerte, TINTA)
            texto("Reporte de pagos recibidos", ANCHO - MARGEN, MARGEN + 8f, 13f, fuerte, TINTA, derecha = true)
            texto(d.periodo, ANCHO - MARGEN, MARGEN + 23f, 9.5f, regular, SUAVE, derecha = true)
            y = MARGEN + 34f
            c?.drawLine(MARGEN, y, ANCHO - MARGEN, y, pintura(VERDE).apply { strokeWidth = 1.2f })
            y += 18f
            texto("Dispositivo", MARGEN, y, 7.5f, medio, SUAVE)
            texto(dispositivo, MARGEN, y + 12f, 9f, medio, TINTA)
            texto("Generado", ANCHO / 2, y, 7.5f, medio, SUAVE)
            texto(d.generado, ANCHO / 2, y + 12f, 9f, medio, TINTA)
            y += 28f
        }

        private fun resumen() {
            val datos = listOf("Total recibido" to d.total, "Pagos" to d.cantidad, "Ticket promedio" to d.promedio)
            val sep = 10f
            val ancho = (ANCHO - 2 * MARGEN - sep * 2) / 3
            datos.forEachIndexed { i, (k, v) ->
                val x = MARGEN + i * (ancho + sep)
                val caja = RectF(x, y, x + ancho, y + 54f)
                c?.drawRoundRect(caja, 6f, 6f, pintura(BORDE).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f })
                if (i == 0) c?.drawRect(RectF(x, y + 12f, x + 2f, y + 42f), pintura(VERDE))
                texto(k, x + 12f, y + 18f, 8f, medio, SUAVE)
                texto(v, x + 12f, y + 40f, 15f, fuerte, if (i == 0) VERDE else TINTA)
            }
            y += 54f + 6f
        }

        private fun seccion(titulo: String) {
            espacio(22f + FILA * 3)
            y += 16f
            texto(titulo, MARGEN, y + 10f, 10.5f, fuerte, VERDE)
            y += 18f
        }

        private fun tabla(columnas: List<Columna>, filas: List<List<String>>, totales: List<String>?, conLogo: Boolean = false) {
            fun cabecera() {
                var x = MARGEN
                columnas.forEach { col ->
                    celda(col.titulo, x, col, 7.5f, medio, SUAVE)
                    x += col.ancho
                }
                y += FILA
                c?.drawLine(MARGEN, y, ANCHO - MARGEN, y, pintura(BORDE).apply { strokeWidth = 0.7f })
            }
            espacio(FILA * 2)
            cabecera()
            filas.forEachIndexed { i, fila ->
                if (espacio(FILA)) cabecera()
                if (i > 0) c?.drawLine(MARGEN, y, ANCHO - MARGEN, y, pintura(LINEA).apply { strokeWidth = 0.4f })
                var x = MARGEN
                fila.forEachIndexed { j, valor ->
                    val col = columnas[j]
                    val logo = if (conLogo && j == 0) logos[valor] else null
                    if (logo != null) {
                        imagen(logo, x + 8f, y + 3.5f, 11f)
                        celda(valor, x + 16f, col, 8.5f, medio, TINTA)
                    } else {
                        celda(valor, x, col, 8.5f, if (j == fila.lastIndex) medio else regular, TINTA)
                    }
                    x += col.ancho
                }
                y += FILA
            }
            if (totales != null) {
                if (espacio(FILA + 2f)) cabecera()
                c?.drawLine(MARGEN, y, ANCHO - MARGEN, y, pintura(TINTA).apply { strokeWidth = 0.7f })
                var x = MARGEN
                totales.forEachIndexed { j, valor ->
                    celda(valor, x, columnas[j], 9f, fuerte, if (j == totales.lastIndex) VERDE else TINTA)
                    x += columnas[j].ancho
                }
                y += FILA
            }
            linea(y)
        }

        private fun celda(valor: String, x: Float, col: Columna, tamano: Float, tipo: Typeface, color: Int) {
            val interior = col.ancho - 16f
            val v = recortar(valor, interior, tamano, tipo)
            val base = y + FILA / 2 + tamano * 0.36f
            if (col.derecha) texto(v, x + col.ancho - 8f, base, tamano, tipo, color, derecha = true) else texto(v, x + 8f, base, tamano, tipo, color)
        }

        private fun recortar(valor: String, ancho: Float, tamano: Float, tipo: Typeface): String {
            val p = textoPintura(tamano, tipo, TINTA)
            if (p.measureText(valor) <= ancho) return valor
            var corte = valor.length
            while (corte > 1 && p.measureText(valor.take(corte) + "…") > ancho) corte--
            return valor.take(corte).trimEnd() + "…"
        }

        private fun texto(t: String, x: Float, base: Float, tamano: Float, tipo: Typeface, color: Int, derecha: Boolean = false) {
            val p = textoPintura(tamano, tipo, color).apply { textAlign = if (derecha) Paint.Align.RIGHT else Paint.Align.LEFT }
            c?.drawText(t, x, base, p)
        }

        private fun linea(en: Float) {
            c?.drawLine(MARGEN, en, ANCHO - MARGEN, en, pintura(LINEA).apply { strokeWidth = 0.6f })
        }

        private fun imagen(b: Bitmap, x: Float, yy: Float, lado: Float) {
            c?.drawBitmap(b, null, RectF(x, yy, x + lado, yy + lado), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        private fun sello(cx: Float, cy: Float, r: Float) {
            val canvas = c ?: return
            canvas.drawCircle(cx, cy, r, pintura(VERDE))
            val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val radio = r * 0.6f
            trazo.color = 0xFFFFFFFF.toInt(); trazo.strokeWidth = radio * 0.23f
            canvas.drawArc(RectF(cx - radio, cy - radio, cx + radio, cy + radio), -30f, 300f, false, trazo)
            val u = radio / 22f
            trazo.color = AMBAR; trazo.strokeWidth = radio * 0.28f
            val path = android.graphics.Path().apply {
                moveTo(cx - 11 * u, cy); lineTo(cx - 3 * u, cy + 8 * u); lineTo(cx + 17 * u, cy - 15 * u)
            }
            canvas.drawPath(path, trazo)
        }
    }

    private fun pintura(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun textoPintura(tamano: Float, tipo: Typeface, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = tamano
        this.typeface = tipo
        this.color = color
        isSubpixelText = true
    }
}
