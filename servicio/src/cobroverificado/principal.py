from __future__ import annotations

import importlib
import logging
from typing import Protocol, cast

import structlog
from fastapi import FastAPI

from cobroverificado.adaptadores.http.app import construir_app
from cobroverificado.adaptadores.http.servicios import Servicios
from cobroverificado.adaptadores.vision import ExtractorCompuesto, ExtractorVision
from cobroverificado.config import Configuracion
from cobroverificado.dominio.modelos import DatosExtraidos


class ExtractorBasico(Protocol):
    def extraer(self, imagen: bytes) -> DatosExtraidos: ...


def extractor_ocr() -> ExtractorBasico:
    modulo = importlib.import_module("cobroverificado.adaptadores.ocr")
    return cast(ExtractorBasico, modulo.ExtractorOcr())


def construir_extractor(config: Configuracion, ocr: ExtractorBasico) -> ExtractorCompuesto:
    vision = None
    if config.anthropic_api_key is not None:
        vision = ExtractorVision.desde_clave(
            config.anthropic_api_key.get_secret_value(), config.modelo_vision
        )
    return ExtractorCompuesto(ocr=ocr, vision=vision, umbral_confianza=config.umbral_confianza_ocr)


def configurar_logs(nivel: str) -> None:
    logging.basicConfig(format="%(message)s", level=nivel.upper())
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(logging.getLevelName(nivel.upper())),
    )


def servicios_postgres(config: Configuracion) -> Servicios:
    # se importa aquí para que crear la app con servicios falsos no cargue postgres ni el ocr
    from cobroverificado.composicion import construir_servicios

    return construir_servicios(config)


def create_app(config: Configuracion | None = None, servicios: Servicios | None = None) -> FastAPI:
    config = config or Configuracion()
    configurar_logs(config.log_level)
    return construir_app(servicios or servicios_postgres(config), config)
