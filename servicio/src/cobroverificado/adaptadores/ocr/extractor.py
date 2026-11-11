from __future__ import annotations

import io
from typing import Any

import numpy as np
from PIL import Image, UnidentifiedImageError

from cobroverificado.dominio.modelos import DatosExtraidos, Fuente

from .lector import Linea, leer_comprobante
from .patrones import PATRONES_POR_DEFECTO, Patrones

_VACIO = DatosExtraidos(None, None, None, None, None, None, None, 0.0, Fuente.OCR)


class ExtractorOcr:
    def __init__(self, patrones: Patrones = PATRONES_POR_DEFECTO) -> None:
        self._patrones = patrones
        self._motor: Any = None

    def extraer(self, imagen: bytes) -> DatosExtraidos:
        lineas = self.lineas(imagen)
        return leer_comprobante(lineas, self._patrones) if lineas else _VACIO

    def lineas(self, imagen: bytes) -> list[Linea]:
        try:
            with Image.open(io.BytesIO(imagen)) as abierta:
                matriz = np.asarray(abierta.convert("RGB"))
        except (UnidentifiedImageError, OSError):
            return []
        resultado, _ = self._obtener_motor()(matriz)
        return [_a_linea(fila) for fila in resultado or []]

    def _obtener_motor(self) -> Any:
        # el modelo tarda en cargar, así que se crea al primer uso y no al importar
        if self._motor is None:
            from rapidocr_onnxruntime import RapidOCR

            self._motor = RapidOCR()
        return self._motor


def _a_linea(fila: Any) -> Linea:
    puntos, texto, puntaje = fila
    xs = [float(p[0]) for p in puntos]
    ys = [float(p[1]) for p in puntos]
    return Linea(str(texto), (min(xs), min(ys), max(xs), max(ys)), float(puntaje))
