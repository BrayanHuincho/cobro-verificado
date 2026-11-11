from __future__ import annotations

from collections.abc import Awaitable, Callable

from fastapi import FastAPI, Request, Response

from cobroverificado.adaptadores.http.dependencias import EstadoApp
from cobroverificado.adaptadores.http.errores import registrar_manejadores, respuesta_error
from cobroverificado.adaptadores.http.middleware import registrar_middleware
from cobroverificado.adaptadores.http.rutas import salud, v1
from cobroverificado.adaptadores.http.seguridad import LimitadorPeticiones
from cobroverificado.adaptadores.http.servicios import Servicios
from cobroverificado.config import Configuracion

DESCRIPCION = (
    "Verifica capturas de pago cruzándolas con los pagos que realmente recibió el comercio. "
    "Una captura por sí sola nunca se considera prueba de pago."
)
_MARGEN_MULTIPART = 64 * 1024


def construir_app(servicios: Servicios, config: Configuracion) -> FastAPI:
    app = FastAPI(
        title="cobro-verificado",
        version="0.1.0",
        description=DESCRIPCION,
        openapi_tags=[
            {"name": "verificaciones", "description": "Capturas enviadas por el comercio"},
            {"name": "pagos", "description": "Pagos recibidos según la app del comercio"},
            {"name": "dispositivos", "description": "Vinculación de la app Android"},
            {"name": "comercio", "description": "Configuración del comercio"},
        ],
    )
    app.state.cobroverificado = EstadoApp(
        servicios=servicios,
        config=config,
        limitador=LimitadorPeticiones(config.peticiones_por_minuto),
    )
    limite_cuerpo = config.tamano_maximo_imagen + _MARGEN_MULTIPART

    @app.middleware("http")
    async def limitar_cuerpo(
        request: Request, siguiente: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        longitud = request.headers.get("content-length", "")
        if longitud.isdigit() and int(longitud) > limite_cuerpo:
            return respuesta_error(
                413,
                "imagen_demasiado_grande",
                "La solicitud es demasiado grande",
                {"maximo_bytes": config.tamano_maximo_imagen},
            )
        return await siguiente(request)

    registrar_middleware(app)
    registrar_manejadores(app)
    app.include_router(salud)
    app.include_router(v1)
    return app
