from __future__ import annotations

import hashlib
import hmac
import json
from datetime import UTC, datetime
from uuid import uuid4

from cobroverificado.adaptadores.cifrado import CifradorSecretos
from cobroverificado.adaptadores.postgres import (
    Pool,
    RegistroEntregasPg,
    RepositorioVerificacionesPg,
)
from cobroverificado.adaptadores.webhooks import DespachadorWebhooks, Respuesta, firmar, verificar
from cobroverificado.dominio.modelos import Comercio, Estado, Verificacion

CIFRADOR = CifradorSecretos("11" * 32)


def test_firma_hmac_sha256() -> None:
    cabecera = firmar("s3", b'{"a":1}', 1700000000)
    esperado = hmac.new(b"s3", b'1700000000.{"a":1}', hashlib.sha256).hexdigest()
    assert cabecera == f"t=1700000000,v1={esperado}"
    assert verificar("s3", b'{"a":1}', cabecera, 1700000010)
    assert not verificar("otro", b'{"a":1}', cabecera, 1700000010)
    assert not verificar("s3", b'{"a":2}', cabecera, 1700000010)
    assert not verificar("s3", b'{"a":1}', cabecera, 1700009999)
    assert not verificar("s3", b"x", "basura", 0)


class _Servidor:
    def __init__(self, respuestas: list[Respuesta]) -> None:
        self.respuestas = respuestas
        self.recibidas: list[tuple[str, bytes, dict[str, str]]] = []

    def __call__(self, url: str, cuerpo: bytes, cabeceras: dict[str, str]) -> Respuesta:
        self.recibidas.append((url, cuerpo, cabeceras))
        return self.respuestas.pop(0)


def test_entrega_firmada_con_reintento(pool: Pool, comercio: Comercio) -> None:
    verificacion = RepositorioVerificacionesPg(pool).crear(
        Verificacion(uuid4(), comercio.id, Estado.PENDIENTE, datetime.now(UTC))
    )
    registro = RegistroEntregasPg(pool)
    servidor = _Servidor([Respuesta(500, "http 500"), Respuesta(200, None)])
    despachador = DespachadorWebhooks(
        registro, CIFRADOR.descifrar, servidor, reloj=lambda: 1700000000.0
    )

    assert despachador.avisar(verificacion, "https://ejemplo.test/hook") is not None
    assert despachador.entregar_siguiente()
    assert registro.historial(comercio.id, verificacion.id) == [("pendiente", 1, 500)]
    assert not despachador.entregar_siguiente()

    with pool.connection() as conexion:
        conexion.execute("update entregas_webhook set disponible_en = now()")
    assert despachador.entregar_siguiente()
    assert registro.historial(comercio.id, verificacion.id) == [("entregada", 2, 200)]

    url, cuerpo, cabeceras = servidor.recibidas[-1]
    assert url == "https://ejemplo.test/hook"
    assert verificar("secreto", cuerpo, cabeceras["X-Firma"], 1700000000)
    datos = json.loads(cuerpo)
    assert datos["evento"] == "verificacion.completada"
    assert datos["verificacion"]["id"] == str(verificacion.id)


def test_sin_url_no_programa(pool: Pool, comercio: Comercio) -> None:
    verificacion = Verificacion(uuid4(), comercio.id, Estado.PENDIENTE, datetime.now(UTC))
    assert (
        DespachadorWebhooks(RegistroEntregasPg(pool), CIFRADOR.descifrar).avisar(verificacion, None)
        is None
    )
