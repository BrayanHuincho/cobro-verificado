from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from enum import StrEnum
from uuid import UUID


class Billetera(StrEnum):
    YAPE = "yape"
    PLIN = "plin"


class Fuente(StrEnum):
    OCR = "ocr"
    VISION = "vision"


class Severidad(StrEnum):
    INFO = "info"
    ADVERTENCIA = "advertencia"
    CRITICA = "critica"


class Estado(StrEnum):
    PROCESANDO = "procesando"
    CONFIRMADO = "confirmado"
    PENDIENTE = "pendiente"
    SOSPECHOSO = "sospechoso"
    RECHAZADO = "rechazado"


class OrigenPago(StrEnum):
    NOTIFICACION = "notificacion"
    MANUAL = "manual"


@dataclass(frozen=True, slots=True)
class DatosExtraidos:
    billetera: Billetera | None
    monto: Decimal | None
    fecha_hora: datetime | None
    numero_operacion: str | None
    destinatario: str | None
    remitente: str | None
    celular_destino: str | None
    confianza: float
    fuente: Fuente


@dataclass(frozen=True, slots=True)
class Senal:
    codigo: str
    severidad: Severidad
    mensaje: str


@dataclass(frozen=True, slots=True)
class Comercio:
    id: UUID
    nombre: str
    destinatarios: tuple[str, ...]
    celulares: tuple[str, ...]
    ventana_minutos: int = 1440
    tolerancia_minutos: int = 10
    umbral_revision_humana: Decimal | None = None
    url_webhook: str | None = None


@dataclass(frozen=True, slots=True)
class PagoRecibido:
    id: UUID
    comercio_id: UUID
    billetera: Billetera
    monto: Decimal
    fecha_hora: datetime
    numero_operacion: str | None
    remitente: str | None
    huella: str
    origen: OrigenPago
    recibido_en: datetime


@dataclass(frozen=True, slots=True)
class SolicitudVerificacion:
    comercio_id: UUID
    monto_esperado: Decimal | None
    referencia: str | None


@dataclass(frozen=True, slots=True)
class Veredicto:
    estado: Estado
    senales: tuple[Senal, ...] = ()
    pago_id: UUID | None = None
    requiere_revision: bool = False


@dataclass(frozen=True, slots=True)
class Verificacion:
    id: UUID
    comercio_id: UUID
    estado: Estado
    creada_en: datetime
    monto_esperado: Decimal | None = None
    referencia: str | None = None
    datos: DatosExtraidos | None = None
    veredicto: Veredicto | None = None
    revisada: bool = False
    senales: tuple[Senal, ...] = field(default=())
