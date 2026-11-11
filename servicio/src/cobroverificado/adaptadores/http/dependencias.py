from __future__ import annotations

from dataclasses import dataclass
from typing import Annotated
from uuid import UUID

import structlog
from fastapi import Depends, Request, Security
from fastapi.security import APIKeyHeader, HTTPAuthorizationCredentials, HTTPBearer

from cobroverificado.adaptadores.http.errores import ApiError
from cobroverificado.adaptadores.http.seguridad import LimitadorPeticiones, sha256
from cobroverificado.adaptadores.http.servicios import Servicios
from cobroverificado.config import Configuracion
from cobroverificado.dominio.modelos import Comercio


@dataclass(frozen=True, slots=True)
class EstadoApp:
    servicios: Servicios
    config: Configuracion
    limitador: LimitadorPeticiones


def estado_app(request: Request) -> EstadoApp:
    estado: EstadoApp = request.app.state.cobroverificado
    return estado


Estado = Annotated[EstadoApp, Depends(estado_app)]

_bearer = HTTPBearer(auto_error=False, description="Clave API del comercio")
_dispositivo = APIKeyHeader(
    name="X-Dispositivo", auto_error=False, description="Token de la app Android vinculada"
)


def _limitar(estado: EstadoApp, sujeto: str) -> None:
    permitido, espera = estado.limitador.permitir(sujeto)
    if not permitido:
        raise ApiError(
            429,
            "demasiadas_peticiones",
            "Límite de peticiones excedido",
            {"reintentar_en_segundos": espera},
            {"Retry-After": str(espera)},
        )


def comercio_actual(
    estado: Estado,
    credenciales: Annotated[HTTPAuthorizationCredentials | None, Security(_bearer)],
) -> Comercio:
    if credenciales is None or not credenciales.credentials:
        raise ApiError(
            401, "no_autenticado", "Falta la clave API", cabeceras={"WWW-Authenticate": "Bearer"}
        )
    hash_clave = sha256(credenciales.credentials)
    comercio = estado.servicios.autenticacion.comercio_por_clave(hash_clave)
    if comercio is None:
        raise ApiError(
            401, "no_autenticado", "Clave API inválida", cabeceras={"WWW-Authenticate": "Bearer"}
        )
    _limitar(estado, "c:" + hash_clave)
    structlog.contextvars.bind_contextvars(comercio_id=str(comercio.id))
    return comercio


def comercio_de_dispositivo(
    estado: Estado, token: Annotated[str | None, Security(_dispositivo)]
) -> UUID:
    if not token:
        raise ApiError(401, "no_autenticado", "Falta el token del dispositivo")
    hash_token = sha256(token)
    comercio_id = estado.servicios.autenticacion.comercio_por_dispositivo(hash_token)
    if comercio_id is None:
        raise ApiError(401, "no_autenticado", "Token de dispositivo inválido")
    _limitar(estado, "d:" + hash_token)
    structlog.contextvars.bind_contextvars(comercio_id=str(comercio_id))
    return comercio_id


def limitar_por_ip(estado: Estado, request: Request) -> None:
    _limitar(estado, "ip:" + (request.client.host if request.client else "desconocido"))


ComercioActual = Annotated[Comercio, Depends(comercio_actual)]
ComercioDispositivo = Annotated[UUID, Depends(comercio_de_dispositivo)]
