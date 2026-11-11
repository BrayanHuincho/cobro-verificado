from __future__ import annotations

from typing import Any

import structlog
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from starlette.exceptions import HTTPException as StarletteHTTPException

log = structlog.get_logger()


class ApiError(Exception):
    def __init__(
        self,
        estado: int,
        codigo: str,
        mensaje: str,
        detalles: dict[str, Any] | None = None,
        cabeceras: dict[str, str] | None = None,
    ) -> None:
        super().__init__(mensaje)
        self.estado = estado
        self.codigo = codigo
        self.mensaje = mensaje
        self.detalles = detalles or {}
        self.cabeceras = cabeceras


class CuerpoError(BaseModel):
    codigo: str
    mensaje: str
    detalles: dict[str, Any]


class RespuestaError(BaseModel):
    error: CuerpoError


CODIGOS_HTTP = {
    400: "solicitud_invalida",
    401: "no_autenticado",
    403: "prohibido",
    404: "no_encontrado",
    405: "metodo_no_permitido",
    409: "conflicto",
    413: "demasiado_grande",
    415: "tipo_no_soportado",
    422: "validacion",
    429: "demasiadas_peticiones",
}


def respuesta_error(
    estado: int,
    codigo: str,
    mensaje: str,
    detalles: dict[str, Any] | None = None,
    cabeceras: dict[str, str] | None = None,
) -> JSONResponse:
    cuerpo = RespuestaError(
        error=CuerpoError(codigo=codigo, mensaje=mensaje, detalles=detalles or {})
    )
    return JSONResponse(cuerpo.model_dump(), status_code=estado, headers=cabeceras)


def registrar_manejadores(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def _error_api(_: Request, exc: ApiError) -> JSONResponse:
        return respuesta_error(exc.estado, exc.codigo, exc.mensaje, exc.detalles, exc.cabeceras)

    @app.exception_handler(RequestValidationError)
    async def _validacion(_: Request, exc: RequestValidationError) -> JSONResponse:
        # sin "input": podría repetir datos enviados por el cliente
        campos = [
            {"campo": ".".join(str(p) for p in e.get("loc", ())), "problema": e.get("msg", "")}
            for e in exc.errors()
        ]
        return respuesta_error(422, "validacion", "La solicitud no es válida", {"campos": campos})

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        cabeceras = dict(exc.headers) if exc.headers else None
        codigo = CODIGOS_HTTP.get(exc.status_code, "error")
        return respuesta_error(exc.status_code, codigo, str(exc.detail), cabeceras=cabeceras)

    @app.exception_handler(Exception)
    async def _inesperado(_: Request, exc: Exception) -> JSONResponse:
        log.error("error_inesperado", tipo=type(exc).__name__)
        return respuesta_error(500, "error_interno", "Error interno del servidor")
