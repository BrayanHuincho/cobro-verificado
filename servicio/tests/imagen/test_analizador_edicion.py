import io
from collections.abc import Sequence
from dataclasses import dataclass
from decimal import Decimal

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from PIL.PngImagePlugin import PngInfo

from cobroverificado.adaptadores.imagen import AnalizadorEdicion, LineaTexto
from cobroverificado.dominio.modelos import Billetera, DatosExtraidos, Fuente, Severidad

DATOS = DatosExtraidos(
    Billetera.YAPE, Decimal("150.00"), None, "123456", None, None, None, 0.9, Fuente.OCR
)


@dataclass(frozen=True)
class Caja:
    texto: str
    caja: tuple[float, float, float, float]


def captura(ancho: int = 720, alto: int = 1440) -> Image.Image:
    imagen = Image.new("RGB", (ancho, alto), (245, 245, 245))
    dibujo = ImageDraw.Draw(imagen)
    fuente = ImageFont.load_default(size=28)
    for i, texto in enumerate(
        ["Pago enviado", "S/ 150", "Destino: Ana", "Operacion: 123456", "09/11/2026 10:00"]
    ):
        dibujo.text((60, 120 + i * 90), texto, fill=(30, 30, 30), font=fuente)
    return imagen


def a_bytes(imagen: Image.Image, formato: str, **opciones: object) -> bytes:
    buffer = io.BytesIO()
    imagen.save(buffer, formato, **opciones)
    return buffer.getvalue()


def codigos(imagen: bytes, detector: Sequence[LineaTexto] | None = None) -> set[str]:
    analizador = AnalizadorEdicion(detector=(lambda _: detector) if detector is not None else None)
    return {s.codigo for s in analizador.analizar(imagen, DATOS)}


def test_captura_limpia_sin_senales() -> None:
    assert codigos(a_bytes(captura(), "PNG")) == set()
    assert codigos(a_bytes(captura(), "JPEG", quality=85)) == set()


def test_bytes_invalidos() -> None:
    senales = AnalizadorEdicion().analizar(b"no es imagen", DATOS)
    assert [s.codigo for s in senales] == ["imagen_ilegible"]


def test_metadatos_de_editor_en_png() -> None:
    info = PngInfo()
    info.add_text("Software", "GIMP 2.10")
    senales = AnalizadorEdicion().analizar(a_bytes(captura(), "PNG", pnginfo=info), DATOS)
    assert [(s.codigo, s.severidad) for s in senales] == [
        ("metadatos_editor", Severidad.ADVERTENCIA)
    ]


def test_metadatos_de_editor_en_exif() -> None:
    exif = Image.Exif()
    exif[0x0131] = "Adobe Photoshop 25.0"
    assert "metadatos_editor" in codigos(a_bytes(captura(), "JPEG", quality=90, exif=exif))


def test_dimensiones_inusuales_son_informativas() -> None:
    senales = AnalizadorEdicion().analizar(a_bytes(captura(300, 300), "PNG"), DATOS)
    assert {s.codigo for s in senales} == {"baja_resolucion", "dimensiones_inusuales"}
    assert all(s.severidad is Severidad.INFO for s in senales)


def test_zona_pegada_tras_compresion() -> None:
    guardada = a_bytes(
        Image.open(io.BytesIO(a_bytes(captura(), "JPEG", quality=90))), "JPEG", quality=90
    )
    base = Image.open(io.BytesIO(guardada)).convert("RGB")
    ruido = np.random.default_rng(7).integers(0, 255, (48, 96, 3), dtype=np.uint8)
    base.paste(Image.fromarray(ruido), (64, 640))
    assert "compresion_inconsistente" in codigos(a_bytes(base, "JPEG", quality=90))


def test_texto_desalineado_en_misma_fila() -> None:
    lineas = [Caja("Operacion", (60, 300, 200, 330)), Caja("123456", (300, 312, 420, 352))]
    assert "texto_desalineado" in codigos(a_bytes(captura(), "PNG"), lineas)


def test_fila_alineada_no_genera_senal() -> None:
    lineas = [Caja("Operacion", (60, 300, 200, 330)), Caja("123456", (300, 301, 420, 331))]
    assert "texto_desalineado" not in codigos(a_bytes(captura(), "PNG"), lineas)


def test_caja_del_monto_demasiado_ancha() -> None:
    lineas = [
        Caja("S/ 150", (60, 200, 600, 260)),
        Caja("Operacion: 123456", (60, 400, 330, 430)),
        Caja("09/11/2026 10:00", (60, 500, 320, 530)),
    ]
    assert "caja_monto_inconsistente" in codigos(a_bytes(captura(), "PNG"), lineas)


def test_caja_del_monto_proporcionada() -> None:
    lineas = [
        Caja("S/ 150", (60, 200, 300, 260)),
        Caja("Operacion: 123456", (60, 400, 330, 430)),
        Caja("09/11/2026 10:00", (60, 500, 320, 530)),
    ]
    assert "caja_monto_inconsistente" not in codigos(a_bytes(captura(), "PNG"), lineas)
