from __future__ import annotations

import pytest
from falsos_http import (
    CLAVE,
    AutenticacionFalsa,
    DispositivosFalsos,
    PagosFalsos,
    SaludFalsa,
    VerificacionesFalsas,
    WebhookFalso,
)
from fastapi.testclient import TestClient

from cobroverificado.adaptadores.http.servicios import Servicios
from cobroverificado.config import Configuracion
from cobroverificado.principal import create_app


@pytest.fixture
def servicios() -> Servicios:
    return Servicios(
        AutenticacionFalsa(),
        VerificacionesFalsas(),
        DispositivosFalsos(),
        PagosFalsos(),
        SaludFalsa(),
        WebhookFalso(),
    )


@pytest.fixture
def config() -> Configuracion:
    return Configuracion(tamano_maximo_imagen=1024, peticiones_por_minuto=1000)


@pytest.fixture
def cliente(servicios: Servicios, config: Configuracion) -> TestClient:
    return TestClient(create_app(config, servicios))


@pytest.fixture
def auth() -> dict[str, str]:
    return {"Authorization": f"Bearer {CLAVE}"}
