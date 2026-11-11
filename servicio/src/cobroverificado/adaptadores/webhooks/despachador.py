from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Protocol
from uuid import UUID

from cobroverificado.dominio.modelos import Verificacion

from .firma import firmar


class EntregaPendiente(Protocol):
    @property
    def id(self) -> int: ...
    @property
    def comercio_id(self) -> UUID: ...
    @property
    def url(self) -> str: ...
    @property
    def cuerpo(self) -> dict[str, Any]: ...
    @property
    def secreto(self) -> str | None: ...


class RegistroEntregas(Protocol):
    def programar(
        self, comercio_id: UUID, verificacion_id: UUID, url: str, cuerpo: dict[str, Any]
    ) -> int: ...
    def tomar(self) -> EntregaPendiente | None: ...
    def marcar_entregada(self, entrega_id: int, codigo: int) -> None: ...
    def marcar_fallo(self, entrega: Any, codigo: int | None, error: str) -> bool: ...


@dataclass(frozen=True, slots=True)
class Respuesta:
    codigo: int | None
    error: str | None


Enviar = Callable[[str, bytes, dict[str, str]], Respuesta]
Descifrar = Callable[[str, UUID], str]


def enviar_http(
    url: str, cuerpo: bytes, cabeceras: dict[str, str], espera: float = 10.0
) -> Respuesta:
    if not url.startswith(("https://", "http://")):
        return Respuesta(None, "solo se admiten urls http o https")
    peticion = urllib.request.Request(url, data=cuerpo, headers=cabeceras, method="POST")  # noqa: S310
    try:
        with urllib.request.urlopen(peticion, timeout=espera) as respuesta:  # noqa: S310
            codigo = int(respuesta.status)
    except urllib.error.HTTPError as error:
        return Respuesta(error.code, f"http {error.code}")
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        return Respuesta(None, str(error))
    return Respuesta(codigo, None if 200 <= codigo < 300 else f"http {codigo}")


def cuerpo_verificacion(verificacion: Verificacion) -> dict[str, Any]:
    veredicto = verificacion.veredicto
    datos = verificacion.datos
    return {
        "evento": "verificacion.completada",
        "verificacion": {
            "id": str(verificacion.id),
            "estado": str(verificacion.estado),
            "creada_en": verificacion.creada_en.isoformat(),
            "monto_esperado": None
            if verificacion.monto_esperado is None
            else str(verificacion.monto_esperado),
            "referencia": verificacion.referencia,
            "monto": None if datos is None or datos.monto is None else str(datos.monto),
            "numero_operacion": None if datos is None else datos.numero_operacion,
            "requiere_revision": veredicto is not None and veredicto.requiere_revision,
            "pago_id": None
            if veredicto is None or veredicto.pago_id is None
            else str(veredicto.pago_id),
            "senales": [
                {"codigo": s.codigo, "severidad": str(s.severidad), "mensaje": s.mensaje}
                for s in verificacion.senales
            ],
        },
    }


class DespachadorWebhooks:
    def __init__(
        self,
        registro: RegistroEntregas,
        descifrar: Descifrar,
        enviar: Enviar = enviar_http,
        reloj: Callable[[], float] = time.time,
    ) -> None:
        self._registro = registro
        self._descifrar = descifrar
        self._enviar = enviar
        self._reloj = reloj

    def avisar(self, verificacion: Verificacion, url: str | None) -> int | None:
        if not url:
            return None
        return self._registro.programar(
            verificacion.comercio_id, verificacion.id, url, cuerpo_verificacion(verificacion)
        )

    def entregar_siguiente(self) -> bool:
        entrega = self._registro.tomar()
        if entrega is None:
            return False
        if not entrega.secreto:
            self._registro.marcar_fallo(entrega, None, "comercio sin secreto de webhook")
            return True
        try:
            secreto = self._descifrar(entrega.secreto, entrega.comercio_id)
        except Exception as error:
            self._registro.marcar_fallo(entrega, None, str(error))
            return True
        cuerpo = json.dumps(entrega.cuerpo, separators=(",", ":"), ensure_ascii=False).encode()
        cabeceras = {
            "Content-Type": "application/json",
            "User-Agent": "cobroverificado-webhooks/1",
            "X-Firma": firmar(secreto, cuerpo, int(self._reloj())),
        }
        respuesta = self._enviar(entrega.url, cuerpo, cabeceras)
        if respuesta.error is None and respuesta.codigo is not None:
            self._registro.marcar_entregada(entrega.id, respuesta.codigo)
        else:
            self._registro.marcar_fallo(
                entrega, respuesta.codigo, respuesta.error or "sin respuesta"
            )
        return True
