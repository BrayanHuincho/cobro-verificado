from __future__ import annotations

import logging

from cobroverificado.adaptadores.vision.extractor_vision import ErrorVision
from cobroverificado.dominio.modelos import DatosExtraidos, Fuente
from cobroverificado.puertos import Extractor

UMBRAL_POR_DEFECTO = 0.85
PENALIZACION_DESACUERDO = 0.5

log = logging.getLogger(__name__)


class ExtractorCompuesto:
    def __init__(
        self,
        ocr: Extractor,
        vision: Extractor | None = None,
        umbral_confianza: float = UMBRAL_POR_DEFECTO,
    ) -> None:
        self._ocr = ocr
        self._vision = vision
        self._umbral = umbral_confianza

    def extraer(self, imagen: bytes) -> DatosExtraidos:
        datos_ocr = self._ocr.extraer(imagen)
        if self._vision is None or not self._necesita_vision(datos_ocr):
            return datos_ocr
        try:
            datos_vision = self._vision.extraer(imagen)
        except ErrorVision as error:
            log.warning("se continúa solo con ocr", extra={"motivo": str(error)})
            return datos_ocr
        return combinar(datos_ocr, datos_vision, self._umbral)

    def _necesita_vision(self, datos: DatosExtraidos) -> bool:
        faltan = datos.monto is None or datos.fecha_hora is None or datos.numero_operacion is None
        return faltan or datos.confianza < self._umbral


def combinar(ocr: DatosExtraidos, vision: DatosExtraidos, umbral: float) -> DatosExtraidos:
    pares = [
        (ocr.billetera, vision.billetera),
        (ocr.monto, vision.monto),
        (ocr.fecha_hora, vision.fecha_hora),
        (ocr.numero_operacion, vision.numero_operacion),
        (ocr.destinatario, vision.destinatario),
        (ocr.remitente, vision.remitente),
        (ocr.celular_destino, vision.celular_destino),
    ]
    usa_vision = any(de_ocr is None and de_vision is not None for de_ocr, de_vision in pares)
    desacuerdo = any(
        a is not None and b is not None and a != b
        for a, b in ((ocr.monto, vision.monto), (ocr.numero_operacion, vision.numero_operacion))
    )

    # ante un desacuerdo se conserva lo que leyó el ocr y es la confianza la que avisa
    if desacuerdo:
        confianza = min(ocr.confianza, vision.confianza, umbral) * PENALIZACION_DESACUERDO
    elif usa_vision:
        confianza = vision.confianza
    else:
        confianza = max(ocr.confianza, vision.confianza)

    return DatosExtraidos(
        billetera=ocr.billetera if ocr.billetera is not None else vision.billetera,
        monto=ocr.monto if ocr.monto is not None else vision.monto,
        fecha_hora=ocr.fecha_hora if ocr.fecha_hora is not None else vision.fecha_hora,
        numero_operacion=ocr.numero_operacion or vision.numero_operacion,
        destinatario=ocr.destinatario or vision.destinatario,
        remitente=ocr.remitente or vision.remitente,
        celular_destino=ocr.celular_destino or vision.celular_destino,
        confianza=confianza,
        fuente=Fuente.VISION if usa_vision else Fuente.OCR,
    )
