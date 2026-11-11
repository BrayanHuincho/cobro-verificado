package pe.cobroverificado.lector.ui.reportes

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportePdfTest {

    @Test
    fun unaListaLargaSeReparteEnVariasPaginas() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val nombres = listOf("Carmen Salazar", "Luis Fernández", "María Fernanda de los Ángeles Gutiérrez Villanueva", "Diego Castillo")
        val pagos = (1..90).map { i ->
            listOf("%02d/11/2026, %d:%02d p. m.".format(1 + i % 28, 1 + i % 11, i % 60), nombres[i % 4], if (i % 3 == 0) "Plin" else "Yape", "${10200000 + i}", "%,d.50".format(i * 37))
        }
        val datos = DatosPdf(
            "1 nov. al 30 nov. 2026", "30/11/2026, 6:05 p. m.", "S/ 152,736.00", "90", "S/ 1,697.07",
            listOf(listOf("Yape", "60", "101,824.00", "67 %"), listOf("Plin", "30", "50,912.00", "33 %")),
            (1..28).map { listOf("$it nov. 2026", "3", "3,636.00", "1,818.00", "5,454.00") },
            listOf("Total", "90", "101,824.00", "50,912.00", "152,736.00"),
            pagos,
            listOf("Total", "90 pagos", "", "", "152,736.00"),
        )
        val archivo = File(contexto.cacheDir, "exportes/muestra-larga.pdf").apply { parentFile?.mkdirs() }
        ReportePdf(contexto).generar(datos, "Equipo de prueba", archivo)
        val paginas = PdfRenderer(ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
        assertEquals(4, paginas)
    }
}
