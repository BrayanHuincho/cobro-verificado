from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

import pytest

from cobroverificado.adaptadores.ocr import (
    Linea,
    Patrones,
    leer_comprobante,
    parsear_fecha_hora,
    parsear_monto,
)
from cobroverificado.dominio.modelos import Billetera, Fuente

LIMA = ZoneInfo("America/Lima")


def lineas(*filas: str | tuple[str, ...], alto: float = 20, puntaje: float = 0.95) -> list[Linea]:
    resultado = []
    y = 0.0
    for fila in filas:
        textos = (fila,) if isinstance(fila, str) else fila
        h = alto * 2.5 if textos[0].startswith("S/") else alto
        for j, texto in enumerate(textos):
            resultado.append(Linea(texto, (j * 300, y, j * 300 + 12 * len(texto), y + h), puntaje))
        y += h + alto
    return resultado


@pytest.mark.parametrize(
    ("texto", "esperado"),
    [
        ("S/ 25", "25.00"),
        ("S/ 25.50", "25.50"),
        ("S/. 1,250.00", "1250.00"),
        ("S/1.250,75", "1250.75"),
        ("S/ 1 250.5", "1250.50"),
        ("S/ 12,5", "12.50"),
        ("S/ 3,000", "3000.00"),
        ("S/ 0", None),
        ("sin monto", None),
    ],
)
def test_parsear_monto(texto: str, esperado: str | None) -> None:
    resultado = parsear_monto(texto)
    assert resultado == (Decimal(esperado) if esperado else None)


@pytest.mark.parametrize(
    ("texto", "esperado"),
    [
        ("09 nov. 2026 - 03:45 pm", datetime(2026, 11, 9, 15, 45, tzinfo=LIMA)),
        ("9 de noviembre de 2026, 15:45", datetime(2026, 11, 9, 15, 45, tzinfo=LIMA)),
        ("Lun, 09 Nov 2026 12:05 a. m.", datetime(2026, 11, 9, 0, 5, tzinfo=LIMA)),
        ("09/11/2026 08:30:12", datetime(2026, 11, 9, 8, 30, 12, tzinfo=LIMA)),
        ("1 set 2026 12:10 p.m.", datetime(2026, 9, 1, 12, 10, tzinfo=LIMA)),
        ("09-11-26 7:00 AM", datetime(2026, 11, 9, 7, 0, tzinfo=LIMA)),
        ("09 nov 2026", None),
        ("31/02/2026 10:00", None),
    ],
)
def test_parsear_fecha_hora(texto: str, esperado: datetime | None) -> None:
    assert parsear_fecha_hora(texto) == esperado


def test_comprobante_tipo_yape() -> None:
    datos = leer_comprobante(
        lineas(
            "¡Yapeaste!",
            "S/ 150",
            "Maria Lopez R.",
            "09 nov. 2026 | 03:45 p. m.",
            ("Nro. de celular", "*** *** 321"),
            ("Destino", "Yape"),
            ("Nro. de operación", "04512378"),
        )
    )
    assert datos.billetera is Billetera.YAPE
    assert datos.monto == Decimal("150.00")
    assert datos.fecha_hora == datetime(2026, 11, 9, 15, 45, tzinfo=LIMA)
    assert datos.numero_operacion == "04512378"
    assert datos.destinatario == "Maria Lopez R"
    assert datos.celular_destino == "321"
    assert datos.fuente is Fuente.OCR
    assert datos.confianza == pytest.approx(0.95)


def test_comprobante_tipo_plin_con_valores_debajo() -> None:
    datos = leer_comprobante(
        lineas(
            "Pago exitoso con Plin",
            "S/",
            "1,280.90",
            "Para:",
            "Comercial Andes SAC",
            "De: Juan Perez",
            "Fecha: 9 de noviembre de 2026",
            "Hora: 10:15",
            "Código de operación: 8812 3344",
        )
    )
    assert datos.billetera is Billetera.PLIN
    assert datos.monto == Decimal("1280.90")
    assert datos.destinatario == "Comercial Andes SAC"
    assert datos.remitente == "Juan Perez"
    assert datos.fecha_hora == datetime(2026, 11, 9, 10, 15, tzinfo=LIMA)
    assert datos.numero_operacion == "88123344"


def test_destinatario_sin_etiqueta_usa_nombre_bajo_el_monto() -> None:
    datos = leer_comprobante(lineas("Yapeaste", "S/ 20.00", "Bodega Rosita", "09/11/2026 09:00"))
    assert datos.destinatario == "Bodega Rosita"


def test_operacion_ignora_texto_sin_digitos() -> None:
    datos = leer_comprobante(lineas("Operación exitosa", "S/ 10", "Operación: 99887766"))
    assert datos.numero_operacion == "99887766"


def test_monto_principal_es_el_mas_grande() -> None:
    filas = lineas("S/ 300", "Comisión S/ 1.50")
    assert leer_comprobante(filas).monto == Decimal("300.00")


def test_confianza_baja_con_pocos_campos_y_puntajes_bajos() -> None:
    completo = leer_comprobante(
        lineas("Yapeaste", "S/ 5", "09/11/2026 09:00", "Operación: 12345678", "Destino: Ana")
    )
    parcial = leer_comprobante(lineas("S/ 5", puntaje=0.6))
    assert completo.confianza > 0.9
    assert parcial.confianza == pytest.approx(0.3 * 0.6)


def test_sin_lineas() -> None:
    datos = leer_comprobante([])
    assert datos.monto is None
    assert datos.confianza == 0.0


def test_patrones_configurables() -> None:
    patrones = Patrones(etiquetas_operacion=("ref",))
    assert leer_comprobante(lineas("Ref: 556677"), patrones).numero_operacion == "556677"


def test_mes_con_cero_leido_por_ocr() -> None:
    fecha = parsear_fecha_hora("09 n0v. 2026 - 03:45 pm")
    assert fecha is not None
    assert (fecha.month, fecha.day, fecha.hour) == (11, 9, 15)
