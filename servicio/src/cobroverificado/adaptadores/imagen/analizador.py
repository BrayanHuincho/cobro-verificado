from __future__ import annotations

import io
import re
import statistics
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Protocol

import numpy as np
from PIL import Image, UnidentifiedImageError

from cobroverificado.dominio.modelos import DatosExtraidos, Senal, Severidad


class LineaTexto(Protocol):
    @property
    def texto(self) -> str: ...
    @property
    def caja(self) -> tuple[float, float, float, float]: ...


Detector = Callable[[bytes], Sequence[LineaTexto]]

EDITORES = (
    "photoshop",
    "gimp",
    "canva",
    "picsart",
    "snapseed",
    "pixlr",
    "paint.net",
    "lightroom",
    "affinity",
    "illustrator",
    "inkscape",
    "fotor",
    "polarr",
)


@dataclass(frozen=True, slots=True)
class Umbrales:
    proporcion_minima: float = 1.3
    proporcion_maxima: float = 2.6
    ancho_minimo: int = 360
    ela_calidad: int = 90
    ela_bloque: int = 16
    ela_razon: float = 4.0
    ela_piso: float = 2.0
    ela_fraccion_minima: float = 0.02
    ela_fraccion_maxima: float = 0.4
    desfase_base: float = 0.3
    razon_alturas: float = 1.35
    desvio_ancho_monto: float = 0.45


UMBRALES = Umbrales()


class AnalizadorEdicion:
    def __init__(self, detector: Detector | None = None, umbrales: Umbrales = UMBRALES) -> None:
        self._detector = detector
        self._u = umbrales

    def analizar(self, imagen: bytes, datos: DatosExtraidos) -> list[Senal]:
        try:
            with Image.open(io.BytesIO(imagen)) as abierta:
                abierta.load()
                formato = abierta.format
                info = dict(abierta.info)
                exif = abierta.getexif()
                rgb = abierta.convert("RGB")
        except (UnidentifiedImageError, OSError):
            return [
                Senal("imagen_ilegible", Severidad.ADVERTENCIA, "no se pudo decodificar la imagen")
            ]

        senales = [*self._metadatos(info, exif), *self._dimensiones(rgb)]
        if formato == "JPEG":
            senales.extend(self._error_compresion(rgb))
        if self._detector is not None:
            lineas = list(self._detector(imagen))
            senales.extend(self._alineacion(lineas))
            senales.extend(self._caja_monto(lineas, datos))
        return senales

    def _metadatos(
        self, info: dict[str | tuple[int, int], object], exif: Image.Exif
    ) -> list[Senal]:
        textos = [str(v) for v in exif.values() if isinstance(v, str | bytes)]
        textos += [str(v) for v in info.values() if isinstance(v, str)]
        encontrados = sorted({e for e in EDITORES for t in textos if e in t.lower()})
        if not encontrados:
            return []
        return [
            Senal(
                "metadatos_editor",
                Severidad.ADVERTENCIA,
                f"los metadatos mencionan un editor: {', '.join(encontrados)}",
            )
        ]

    def _dimensiones(self, rgb: Image.Image) -> list[Senal]:
        ancho, alto = rgb.size
        senales = []
        if ancho < self._u.ancho_minimo:
            senales.append(
                Senal(
                    "baja_resolucion",
                    Severidad.INFO,
                    f"imagen de {ancho}x{alto}, menor a una captura habitual",
                )
            )
        proporcion = alto / ancho
        if not self._u.proporcion_minima <= proporcion <= self._u.proporcion_maxima:
            senales.append(
                Senal(
                    "dimensiones_inusuales",
                    Severidad.INFO,
                    f"proporción {proporcion:.2f} poco común para una captura de celular",
                )
            )
        return senales

    def _error_compresion(self, rgb: Image.Image) -> list[Senal]:
        # una zona pegada después de la última compresión responde distinto al recomprimir
        buffer = io.BytesIO()
        rgb.save(buffer, "JPEG", quality=self._u.ela_calidad)
        recomprimida = Image.open(buffer).convert("RGB")
        original = np.asarray(rgb, dtype=np.int16)
        diferencia = np.abs(original - np.asarray(recomprimida, dtype=np.int16)).max(axis=2)
        b = self._u.ela_bloque
        alto, ancho = (diferencia.shape[0] // b) * b, (diferencia.shape[1] // b) * b
        if alto == 0 or ancho == 0:
            return []

        def por_bloque(matriz: np.ndarray, funcion: str) -> np.ndarray:
            bloques = matriz[:alto, :ancho].reshape(alto // b, b, ancho // b, b).swapaxes(1, 2)
            return np.asarray(getattr(bloques.reshape(alto // b, ancho // b, -1), funcion)(axis=2))

        contenido = por_bloque(original.mean(axis=2), "std") > 2
        errores = por_bloque(diferencia.astype(np.float32), "mean")[contenido]
        if errores.size < 8:
            return []
        referencia = float(np.median(errores))
        anomalos = errores > max(referencia * self._u.ela_razon, self._u.ela_piso)
        fraccion = float(anomalos.mean())
        if self._u.ela_fraccion_minima <= fraccion <= self._u.ela_fraccion_maxima:
            return [
                Senal(
                    "compresion_inconsistente",
                    Severidad.ADVERTENCIA,
                    f"{fraccion:.1%} de la imagen responde distinto a la recompresión",
                )
            ]
        return []

    def _alineacion(self, lineas: list[LineaTexto]) -> list[Senal]:
        desalineadas = []
        for i, a in enumerate(lineas):
            for b in lineas[i + 1 :]:
                alto_a, alto_b = a.caja[3] - a.caja[1], b.caja[3] - b.caja[1]
                menor = min(alto_a, alto_b)
                solape = min(a.caja[3], b.caja[3]) - max(a.caja[1], b.caja[1])
                if menor <= 0 or solape < 0.5 * menor:
                    continue
                if (
                    abs(a.caja[3] - b.caja[3]) > self._u.desfase_base * menor
                    or max(alto_a, alto_b) / menor > self._u.razon_alturas
                ):
                    desalineadas.append(f"'{a.texto}' / '{b.texto}'")
        if not desalineadas:
            return []
        return [
            Senal(
                "texto_desalineado",
                Severidad.ADVERTENCIA,
                "textos de una misma fila con altura o base distinta: "
                + "; ".join(desalineadas[:3]),
            )
        ]

    def _caja_monto(self, lineas: list[LineaTexto], datos: DatosExtraidos) -> list[Senal]:
        if datos.monto is None:
            return []
        entero = str(int(datos.monto))

        def proporcion(linea: LineaTexto) -> float:
            alto = linea.caja[3] - linea.caja[1]
            caracteres = len(linea.texto.replace(" ", ""))
            return (
                (linea.caja[2] - linea.caja[0]) / (alto * caracteres)
                if alto > 0 and caracteres
                else 0.0
            )

        numericas = [x for x in lineas if re.search(r"\d", x.texto)]
        montos = [x for x in numericas if entero in re.sub(r"\D", "", x.texto)]
        otras = [proporcion(x) for x in numericas if x not in montos and proporcion(x) > 0]
        if not montos or len(otras) < 2:
            return []
        mediana = statistics.median(otras)
        desvio = max(abs(proporcion(x) - mediana) / mediana for x in montos)
        if desvio <= self._u.desvio_ancho_monto:
            return []
        return [
            Senal(
                "caja_monto_inconsistente",
                Severidad.ADVERTENCIA,
                f"el ancho del monto difiere {desvio:.0%} del resto de números",
            )
        ]
