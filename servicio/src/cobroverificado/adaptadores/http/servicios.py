from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Protocol
from uuid import UUID

from cobroverificado.dominio.modelos import (
    Billetera,
    Comercio,
    PagoRecibido,
    SolicitudVerificacion,
    Verificacion,
)

# lo que la capa http necesita de los casos de uso; composicion.py entrega las implementaciones


class Autenticacion(Protocol):
    def comercio_por_clave(self, hash_clave: str) -> Comercio | None: ...
    def comercio_por_dispositivo(self, hash_token: str) -> UUID | None: ...


class ConflictoIdempotenciaError(Exception):
    pass


@dataclass(frozen=True, slots=True)
class Solicitada:
    verificacion: Verificacion
    repetida: bool


class CasosVerificaciones(Protocol):
    def solicitar(
        self, solicitud: SolicitudVerificacion, imagen: bytes, clave_idempotencia: str | None
    ) -> Solicitada: ...
    def obtener(self, comercio_id: UUID, verificacion_id: UUID) -> Verificacion | None: ...
    def revisar(
        self, comercio_id: UUID, verificacion_id: UUID, aprobar: bool, nota: str | None
    ) -> Verificacion | None: ...


@dataclass(frozen=True, slots=True)
class CodigoVinculacion:
    codigo: str
    expira_en: datetime


class CasosDispositivos(Protocol):
    def generar_codigo(self, comercio_id: UUID) -> CodigoVinculacion: ...
    def vincular(self, codigo: str) -> str | None: ...


@dataclass(frozen=True, slots=True)
class NuevoPago:
    comercio_id: UUID
    billetera: Billetera
    monto: Decimal
    fecha_hora: datetime
    remitente: str | None
    numero_operacion: str | None
    huella: str


class CasosPagos(Protocol):
    def registrar(self, pago: NuevoPago) -> PagoRecibido: ...
    def listar(self, comercio_id: UUID, desde: datetime, hasta: datetime) -> list[PagoRecibido]: ...


class Salud(Protocol):
    def disponible(self) -> bool: ...


class CasosWebhook(Protocol):
    def configurar(self, comercio_id: UUID, url: str) -> str | None: ...
    def rotar(self, comercio_id: UUID) -> str: ...


@dataclass(frozen=True, slots=True)
class Servicios:
    autenticacion: Autenticacion
    verificaciones: CasosVerificaciones
    dispositivos: CasosDispositivos
    pagos: CasosPagos
    salud: Salud
    webhook: CasosWebhook
