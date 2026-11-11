from __future__ import annotations

import re
import time
import uuid
from collections.abc import Awaitable, Callable

import structlog
from fastapi import FastAPI, Request, Response

log = structlog.get_logger()

_ID_VALIDO = re.compile(r"^[A-Za-z0-9._-]{8,64}$")

CABECERAS_SEGURIDAD = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Strict-Transport-Security": "max-age=63072000; includeSubDomains",
    "Cross-Origin-Resource-Policy": "same-origin",
    "Cache-Control": "no-store",
}
_CSP_API = "default-src 'none'; frame-ancestors 'none'"


def registrar_middleware(app: FastAPI) -> None:
    @app.middleware("http")
    async def contexto(
        request: Request, siguiente: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        entrante = request.headers.get("X-Request-ID", "")
        id_peticion = entrante if _ID_VALIDO.match(entrante) else uuid.uuid4().hex
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(id_peticion=id_peticion)
        inicio = time.perf_counter()
        respuesta = await siguiente(request)
        respuesta.headers["X-Request-ID"] = id_peticion
        for nombre, valor in CABECERAS_SEGURIDAD.items():
            respuesta.headers.setdefault(nombre, valor)
        # la página de /docs carga recursos propios, el resto es json puro
        if not request.url.path.startswith(("/docs", "/redoc")):
            respuesta.headers.setdefault("Content-Security-Policy", _CSP_API)
        log.info(
            "peticion",
            metodo=request.method,
            ruta=request.url.path,
            estado=respuesta.status_code,
            duracion_ms=round((time.perf_counter() - inicio) * 1000, 1),
        )
        return respuesta
