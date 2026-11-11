from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

from cobroverificado.adaptadores.vision import ErrorVision, ExtractorCompuesto
from cobroverificado.dominio.modelos import Billetera, DatosExtraidos, Fuente

COMPLETOS = DatosExtraidos(
    billetera=Billetera.PLIN,
    monto=Decimal("40.00"),
    fecha_hora=datetime(2026, 11, 9, 10, 0, tzinfo=ZoneInfo("America/Lima")),
    numero_operacion="998877",
    destinatario="Tienda",
    remitente="Luis",
    celular_destino=None,
    confianza=0.95,
    fuente=Fuente.OCR,
)


@dataclass
class Fijo:
    datos: DatosExtraidos | Exception
    llamadas: int = 0

    def extraer(self, imagen: bytes) -> DatosExtraidos:
        self.llamadas += 1
        if isinstance(self.datos, Exception):
            raise self.datos
        return self.datos


def test_ocr_confiable_no_llama_a_vision() -> None:
    vision = Fijo(COMPLETOS)
    resultado = ExtractorCompuesto(Fijo(COMPLETOS), vision).extraer(b"img")
    assert resultado == COMPLETOS
    assert vision.llamadas == 0


def test_sin_vision_configurada_devuelve_ocr() -> None:
    debil = replace(COMPLETOS, confianza=0.2, monto=None)
    assert ExtractorCompuesto(Fijo(debil)).extraer(b"img") == debil


def test_vision_completa_campos_faltantes() -> None:
    ocr = replace(COMPLETOS, monto=None, confianza=0.9)
    vision = replace(COMPLETOS, remitente="Otro", confianza=0.8, fuente=Fuente.VISION)

    resultado = ExtractorCompuesto(Fijo(ocr), Fijo(vision)).extraer(b"img")

    assert resultado.monto == Decimal("40.00")
    assert resultado.remitente == "Luis"
    assert resultado.fuente is Fuente.VISION
    assert resultado.confianza == 0.8


def test_desacuerdo_en_monto_conserva_ocr_y_baja_confianza() -> None:
    ocr = replace(COMPLETOS, confianza=0.7)
    vision = replace(COMPLETOS, monto=Decimal("400.00"), confianza=0.9, fuente=Fuente.VISION)

    resultado = ExtractorCompuesto(Fijo(ocr), Fijo(vision)).extraer(b"img")

    assert resultado.monto == Decimal("40.00")
    assert resultado.confianza < 0.7


def test_desacuerdo_en_operacion_baja_confianza() -> None:
    ocr = replace(COMPLETOS, confianza=0.8)
    vision = replace(COMPLETOS, numero_operacion="111111", confianza=0.9)
    resultado = ExtractorCompuesto(Fijo(ocr), Fijo(vision)).extraer(b"img")
    assert resultado.numero_operacion == "998877"
    assert resultado.confianza < 0.85


def test_coincidencia_sube_la_confianza() -> None:
    ocr = replace(COMPLETOS, confianza=0.6)
    vision = replace(COMPLETOS, confianza=0.9, fuente=Fuente.VISION)
    resultado = ExtractorCompuesto(Fijo(ocr), Fijo(vision)).extraer(b"img")
    assert resultado.confianza == 0.9
    assert resultado.fuente is Fuente.OCR


def test_fallo_de_vision_devuelve_ocr() -> None:
    ocr = replace(COMPLETOS, confianza=0.3)
    resultado = ExtractorCompuesto(Fijo(ocr), Fijo(ErrorVision("caido"))).extraer(b"img")
    assert resultado == ocr
