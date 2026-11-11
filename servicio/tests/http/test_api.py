from __future__ import annotations

import hashlib
import uuid
from dataclasses import replace

import pytest
from falsos_http import PNG, TOKEN, VerificacionesFalsas
from fastapi.testclient import TestClient

from cobroverificado.adaptadores.http.servicios import Servicios
from cobroverificado.cli import main
from cobroverificado.config import Configuracion
from cobroverificado.dominio.modelos import Comercio, Estado
from cobroverificado.principal import create_app

HUELLA = "a" * 64


def test_salud(cliente: TestClient) -> None:
    r = cliente.get("/salud")
    assert r.status_code == 200
    assert r.json() == {"estado": "ok"}
    assert r.headers["X-Content-Type-Options"] == "nosniff"
    assert r.headers["X-Request-ID"]


def test_request_id_entrante_se_respeta(cliente: TestClient) -> None:
    assert (
        cliente.get("/salud", headers={"X-Request-ID": "abc12345"}).headers["X-Request-ID"]
        == "abc12345"
    )
    assert (
        cliente.get("/salud", headers={"X-Request-ID": "corto"}).headers["X-Request-ID"] != "corto"
    )


def test_sin_clave_devuelve_error_uniforme(cliente: TestClient) -> None:
    r = cliente.get(f"/v1/verificaciones/{uuid.uuid4()}")
    assert r.status_code == 401
    assert r.json()["error"]["codigo"] == "no_autenticado"
    assert (
        cliente.get(
            f"/v1/verificaciones/{uuid.uuid4()}", headers={"Authorization": "Bearer otra"}
        ).status_code
        == 401
    )


def test_crear_y_obtener_verificacion(cliente: TestClient, auth: dict[str, str]) -> None:
    r = cliente.post(
        "/v1/verificaciones",
        headers=auth,
        files={"imagen": ("c.png", PNG, "image/png")},
        data={"monto_esperado": "45.50", "referencia": "pedido-1"},
    )
    assert r.status_code == 202
    cuerpo = r.json()
    assert cuerpo["estado"] == "procesando"
    assert cuerpo["monto_esperado"] == "45.50"
    assert r.headers["Location"] == f"/v1/verificaciones/{cuerpo['id']}"
    assert (
        cliente.get(f"/v1/verificaciones/{cuerpo['id']}", headers=auth).json()["id"] == cuerpo["id"]
    )
    assert cliente.get(f"/v1/verificaciones/{uuid.uuid4()}", headers=auth).status_code == 404


@pytest.mark.parametrize(
    "contenido",
    [
        b"\xff\xd8\xff\xe0" + b"\x00" * 20,
        b"RIFF\x00\x00\x00\x00WEBPVP8 " + b"\x00" * 8,
    ],
)
def test_acepta_jpeg_y_webp(cliente: TestClient, auth: dict[str, str], contenido: bytes) -> None:
    r = cliente.post(
        "/v1/verificaciones", headers=auth, files={"imagen": ("x", contenido, "image/png")}
    )
    assert r.status_code == 202


def test_rechaza_tipo_por_bytes_aunque_diga_png(cliente: TestClient, auth: dict[str, str]) -> None:
    r = cliente.post(
        "/v1/verificaciones",
        headers=auth,
        files={"imagen": ("c.png", b"GIF89a" + b"\x00" * 20, "image/png")},
    )
    assert r.status_code == 415
    assert r.json()["error"]["codigo"] == "tipo_no_soportado"


def test_rechaza_imagen_grande(cliente: TestClient, auth: dict[str, str]) -> None:
    r = cliente.post(
        "/v1/verificaciones",
        headers=auth,
        files={"imagen": ("c.png", PNG + b"\x00" * 2048, "image/png")},
    )
    assert r.status_code == 413


def test_rechaza_imagen_vacia_y_monto_invalido(cliente: TestClient, auth: dict[str, str]) -> None:
    assert (
        cliente.post(
            "/v1/verificaciones", headers=auth, files={"imagen": ("c.png", b"", "image/png")}
        ).status_code
        == 422
    )
    r = cliente.post(
        "/v1/verificaciones",
        headers=auth,
        files={"imagen": ("c.png", PNG, "image/png")},
        data={"monto_esperado": "-3"},
    )
    assert r.status_code == 422
    assert r.json()["error"]["codigo"] == "validacion"
    assert "-3" not in r.text


def test_idempotencia(cliente: TestClient, auth: dict[str, str], servicios: Servicios) -> None:
    falsas = servicios.verificaciones
    assert isinstance(falsas, VerificacionesFalsas)
    a = cliente.post(
        "/v1/verificaciones",
        headers=auth | {"Idempotency-Key": "clave-0001"},
        files={"imagen": ("c.png", PNG, "image/png")},
    )
    b = cliente.post(
        "/v1/verificaciones",
        headers=auth | {"Idempotency-Key": "clave-0001"},
        files={"imagen": ("c.png", PNG, "image/png")},
    )
    assert a.status_code == b.status_code == 202
    assert a.json()["id"] == b.json()["id"]
    assert b.headers["Idempotent-Replayed"] == "true"
    assert len(falsas.guardadas) == 1
    c = cliente.post(
        "/v1/verificaciones",
        headers=auth | {"Idempotency-Key": "clave-0001"},
        files={"imagen": ("c.png", PNG + b"x", "image/png")},
    )
    assert c.status_code == 409
    assert c.json()["error"]["codigo"] == "idempotencia_conflicto"


def test_revision(cliente: TestClient, auth: dict[str, str], servicios: Servicios) -> None:
    falsas = servicios.verificaciones
    assert isinstance(falsas, VerificacionesFalsas)
    vid = cliente.post(
        "/v1/verificaciones", headers=auth, files={"imagen": ("c.png", PNG, "image/png")}
    ).json()["id"]
    ruta = f"/v1/verificaciones/{vid}/revision"
    assert cliente.post(ruta, headers=auth, json={"decision": "aprobar"}).status_code == 409
    v = falsas.guardadas[uuid.UUID(vid)]
    falsas.guardadas[v.id] = replace(v, estado=Estado.SOSPECHOSO)
    r = cliente.post(ruta, headers=auth, json={"decision": "rechazar", "nota": "no aparece"})
    assert r.status_code == 200
    assert r.json()["estado"] == "rechazado"
    assert r.json()["revisada"]
    assert cliente.post(ruta, headers=auth, json={"decision": "quizas"}).status_code == 422
    assert (
        cliente.post(
            f"/v1/verificaciones/{uuid.uuid4()}/revision",
            headers=auth,
            json={"decision": "aprobar"},
        ).status_code
        == 404
    )


def test_vinculacion_de_dispositivo(cliente: TestClient, auth: dict[str, str]) -> None:
    r = cliente.post("/v1/dispositivos/codigos", headers=auth)
    assert r.status_code == 201
    assert r.json()["codigo"] == "482913"
    assert cliente.post("/v1/dispositivos/vincular", json={"codigo": "482913"}).json() == {
        "token": TOKEN
    }
    r = cliente.post("/v1/dispositivos/vincular", json={"codigo": "000000"})
    assert r.status_code == 400
    assert r.json()["error"]["codigo"] == "codigo_invalido"


def test_pagos_recibidos(cliente: TestClient, auth: dict[str, str]) -> None:
    pago = {
        "billetera": "yape",
        "monto": "45.50",
        "fecha_hora": "2026-11-09T10:14:00-05:00",
        "numero_operacion": "123",
        "huella": HUELLA,
    }
    assert cliente.post("/v1/pagos-recibidos", json=pago, headers=auth).status_code == 401
    disp = {"X-Dispositivo": TOKEN}
    a = cliente.post("/v1/pagos-recibidos", json=pago, headers=disp)
    b = cliente.post("/v1/pagos-recibidos", json=pago, headers=disp)
    assert a.status_code == 201
    assert a.json()["id"] == b.json()["id"]
    assert (
        cliente.post(
            "/v1/pagos-recibidos", headers=disp, json=pago | {"fecha_hora": "2026-11-09T10:14:00"}
        ).status_code
        == 422
    )
    assert (
        cliente.post(
            "/v1/pagos-recibidos", headers=disp, json=pago | {"huella": "corta"}
        ).status_code
        == 422
    )

    lista = cliente.get(
        "/v1/pagos-recibidos",
        headers=auth,
        params={"desde": "2026-11-09T00:00:00-05:00", "hasta": "2026-11-09T23:59:59-05:00"},
    )
    assert lista.status_code == 200
    assert len(lista.json()["pagos"]) == 1
    assert (
        cliente.get(
            "/v1/pagos-recibidos", headers=auth, params={"desde": "2026-11-09T00:00:00"}
        ).status_code
        == 422
    )
    assert (
        cliente.get(
            "/v1/pagos-recibidos",
            headers=auth,
            params={"desde": "2026-01-01T00:00:00Z", "hasta": "2026-11-09T00:00:00Z"},
        ).status_code
        == 422
    )
    assert cliente.get("/v1/pagos-recibidos", headers=disp).status_code == 401


def test_limite_de_peticiones(servicios: Servicios, auth: dict[str, str]) -> None:
    cliente = TestClient(create_app(Configuracion(peticiones_por_minuto=2), servicios))
    ruta = f"/v1/verificaciones/{uuid.uuid4()}"
    assert [cliente.get(ruta, headers=auth).status_code for _ in range(3)] == [404, 404, 429]
    r = cliente.get(ruta, headers=auth)
    assert r.json()["error"]["codigo"] == "demasiadas_peticiones"
    assert int(r.headers["Retry-After"]) >= 1


def test_logs_sin_secretos(
    cliente: TestClient, auth: dict[str, str], capsys: pytest.CaptureFixture[str]
) -> None:
    cliente.post("/v1/verificaciones", headers=auth, files={"imagen": ("c.png", PNG, "image/png")})
    cliente.post("/v1/pagos-recibidos", headers={"X-Dispositivo": TOKEN}, json={})
    salida = capsys.readouterr()
    texto = salida.out + salida.err
    assert '"peticion"' in texto
    assert auth["Authorization"].split()[1] not in texto
    assert TOKEN not in texto
    assert "PNG" not in texto


def test_openapi(cliente: TestClient) -> None:
    esquema = cliente.get("/openapi.json").json()
    assert {
        "/salud",
        "/v1/verificaciones",
        "/v1/verificaciones/{verificacion_id}",
        "/v1/verificaciones/{verificacion_id}/revision",
        "/v1/dispositivos/vincular",
        "/v1/dispositivos/codigos",
        "/v1/pagos-recibidos",
    } <= set(esquema["paths"])
    assert "RespuestaError" in esquema["components"]["schemas"]
    assert {"HTTPBearer", "APIKeyHeader"} <= set(esquema["components"]["securitySchemes"])


def test_cli_muestra_la_clave_una_vez(capsys: pytest.CaptureFixture[str]) -> None:
    hashes: list[str] = []

    class Registro:
        def crear(
            self,
            nombre: str,
            destinatarios: tuple[str, ...],
            celulares: tuple[str, ...],
            hash_clave: str,
        ) -> Comercio:
            hashes.append(hash_clave)
            return Comercio(uuid.uuid4(), nombre, destinatarios, celulares)

    assert main(["crear-comercio", "Bodega", "--destinatario", "Bodega SAC"], Registro()) == 0
    clave = capsys.readouterr().out.split(": ")[-1].strip()
    assert clave.startswith("yc_")
    assert hashes == [hashlib.sha256(clave.encode()).hexdigest()]


def test_webhook_secreto_una_vez_y_rotacion(cliente: TestClient, auth: dict[str, str]) -> None:
    a = cliente.put("/v1/comercio/webhook", headers=auth, json={"url": "https://t.example/h"})
    assert a.status_code == 200
    secreto = a.json()["secreto"]
    assert secreto
    b = cliente.put("/v1/comercio/webhook", headers=auth, json={"url": "https://t.example/otro"})
    assert b.json() == {"url": "https://t.example/otro", "secreto": None}
    c = cliente.post("/v1/comercio/webhook/rotar", headers=auth)
    assert c.status_code == 200
    assert c.json()["secreto"] not in (None, secreto)


@pytest.mark.parametrize(
    ("url", "codigo"),
    [
        ("http://tienda.example/h", 422),
        ("ftp://tienda.example/h", 422),
        ("https://", 422),
        ("http://localhost:9000/h", 200),
        ("http://127.0.0.1/h", 200),
    ],
)
def test_webhook_solo_https_salvo_localhost(
    cliente: TestClient, auth: dict[str, str], url: str, codigo: int
) -> None:
    r = cliente.put("/v1/comercio/webhook", headers=auth, json={"url": url})
    assert r.status_code == codigo


def test_webhook_requiere_comercio(cliente: TestClient) -> None:
    r = cliente.put("/v1/comercio/webhook", json={"url": "https://t.example/h"})
    assert r.status_code == 401
    assert cliente.post("/v1/comercio/webhook/rotar").status_code == 401


def test_variables_vacias_cuentan_como_ausentes(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("COBROVERIFICADO_ANTHROPIC_API_KEY", "")
    monkeypatch.setenv("COBROVERIFICADO_CLAVE_CIFRADO", " ")
    config = Configuracion(_env_file=None)
    assert config.anthropic_api_key is None
    assert config.clave_cifrado is None
