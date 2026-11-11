import io
from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

from PIL import Image, ImageDraw, ImageFont

from cobroverificado.adaptadores.ocr import ExtractorOcr
from cobroverificado.dominio.modelos import Billetera


def captura_sintetica() -> bytes:
    imagen = Image.new("RGB", (720, 1280), (255, 255, 255))
    dibujo = ImageDraw.Draw(imagen)
    filas = [
        ("Yapeaste", 36),
        ("S/ 1,250.50", 64),
        ("Bodega Rosita", 32),
        ("09 nov. 2026 - 03:45 pm", 28),
        ("Nro. de operacion: 04512378", 28),
    ]
    y = 120
    for texto, tamano in filas:
        dibujo.text((60, y), texto, fill=(20, 20, 20), font=ImageFont.load_default(size=tamano))
        y += tamano + 50
    buffer = io.BytesIO()
    imagen.save(buffer, "PNG")
    return buffer.getvalue()


def test_extrae_de_imagen_sintetica() -> None:
    datos = ExtractorOcr().extraer(captura_sintetica())
    assert datos.billetera is Billetera.YAPE
    assert datos.monto == Decimal("1250.50")
    assert datos.fecha_hora == datetime(2026, 11, 9, 15, 45, tzinfo=ZoneInfo("America/Lima"))
    assert datos.numero_operacion == "04512378"
    assert datos.destinatario == "Bodega Rosita"
    assert datos.confianza > 0.8


def test_bytes_invalidos_devuelven_confianza_cero() -> None:
    assert ExtractorOcr().extraer(b"basura").confianza == 0.0
