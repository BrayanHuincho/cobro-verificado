from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Literal
from urllib.parse import urlsplit
from uuid import UUID

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator

from cobroverificado.dominio.modelos import (
    Billetera,
    DatosExtraidos,
    Estado,
    Fuente,
    OrigenPago,
    PagoRecibido,
    Senal,
    Severidad,
    Verificacion,
)

ID_EJEMPLO = "3f2b6c1e-8a4d-4c2b-9e7f-1a2b3c4d5e6f"


class Salud(BaseModel):
    estado: Literal["ok", "degradado"]


class SenalSalida(BaseModel):
    codigo: str
    severidad: Severidad
    mensaje: str

    @classmethod
    def desde(cls, senal: Senal) -> SenalSalida:
        return cls(codigo=senal.codigo, severidad=senal.severidad, mensaje=senal.mensaje)


class DatosSalida(BaseModel):
    billetera: Billetera | None
    monto: Decimal | None
    fecha_hora: datetime | None
    numero_operacion: str | None
    destinatario: str | None
    remitente: str | None
    confianza: float
    fuente: Fuente

    @classmethod
    def desde(cls, d: DatosExtraidos) -> DatosSalida:
        return cls(
            billetera=d.billetera,
            monto=d.monto,
            fecha_hora=d.fecha_hora,
            numero_operacion=d.numero_operacion,
            destinatario=d.destinatario,
            remitente=d.remitente,
            confianza=d.confianza,
            fuente=d.fuente,
        )


class VerificacionSalida(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "id": ID_EJEMPLO,
                    "estado": "procesando",
                    "creada_en": "2026-11-09T10:15:00-05:00",
                    "monto_esperado": "45.50",
                    "referencia": "pedido-1024",
                    "datos": None,
                    "senales": [],
                    "pago_id": None,
                    "requiere_revision": False,
                    "revisada": False,
                }
            ]
        }
    )

    id: UUID
    estado: Estado
    creada_en: datetime
    monto_esperado: Decimal | None
    referencia: str | None
    datos: DatosSalida | None
    senales: list[SenalSalida]
    pago_id: UUID | None
    requiere_revision: bool
    revisada: bool

    @classmethod
    def desde(cls, v: Verificacion) -> VerificacionSalida:
        senales = v.veredicto.senales if v.veredicto else v.senales
        return cls(
            id=v.id,
            estado=v.estado,
            creada_en=v.creada_en,
            monto_esperado=v.monto_esperado,
            referencia=v.referencia,
            datos=DatosSalida.desde(v.datos) if v.datos else None,
            senales=[SenalSalida.desde(s) for s in senales],
            pago_id=v.veredicto.pago_id if v.veredicto else None,
            requiere_revision=v.veredicto.requiere_revision if v.veredicto else False,
            revisada=v.revisada,
        )


class RevisionEntrada(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={"examples": [{"decision": "aprobar", "nota": "pago visto en el banco"}]},
    )

    decision: Literal["aprobar", "rechazar"]
    nota: str | None = Field(default=None, max_length=500)


class VincularEntrada(BaseModel):
    model_config = ConfigDict(
        extra="forbid", json_schema_extra={"examples": [{"codigo": "482913"}]}
    )

    codigo: str = Field(min_length=4, max_length=32)


class VincularSalida(BaseModel):
    token: str


class CodigoSalida(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [{"codigo": "482913", "expira_en": "2026-11-09T10:25:00-05:00"}]
        }
    )

    codigo: str
    expira_en: datetime


class PagoEntrada(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "examples": [
                {
                    "billetera": "yape",
                    "monto": "45.50",
                    "fecha_hora": "2026-11-09T10:14:00-05:00",
                    "remitente": "Ana P.",
                    "numero_operacion": "12345678",
                    "huella": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                }
            ]
        },
    )

    billetera: Billetera
    monto: Decimal = Field(gt=0, max_digits=12, decimal_places=2)
    fecha_hora: AwareDatetime
    remitente: str | None = Field(default=None, max_length=120)
    numero_operacion: str | None = Field(default=None, max_length=40)
    huella: str = Field(pattern=r"^[0-9a-f]{64}$")


class PagoSalida(BaseModel):
    id: UUID
    billetera: Billetera
    monto: Decimal
    fecha_hora: datetime
    remitente: str | None
    numero_operacion: str | None
    origen: OrigenPago
    recibido_en: datetime

    @classmethod
    def desde(cls, p: PagoRecibido) -> PagoSalida:
        return cls(
            id=p.id,
            billetera=p.billetera,
            monto=p.monto,
            fecha_hora=p.fecha_hora,
            remitente=p.remitente,
            numero_operacion=p.numero_operacion,
            origen=p.origen,
            recibido_en=p.recibido_en,
        )


class ListaPagos(BaseModel):
    pagos: list[PagoSalida]


_LOCALES = {"localhost", "127.0.0.1", "::1"}


class WebhookEntrada(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={"examples": [{"url": "https://tienda.example/cobroverificado"}]},
    )

    url: str = Field(max_length=2048)

    @field_validator("url")
    @classmethod
    def solo_https(cls, valor: str) -> str:
        partes = urlsplit(valor.strip())
        segura = partes.scheme == "https" and bool(partes.hostname)
        local = partes.scheme == "http" and partes.hostname in _LOCALES
        if not (segura or local):
            raise ValueError("la url debe usar https (http solo para localhost)")
        return valor.strip()


class WebhookSalida(BaseModel):
    url: str
    secreto: str | None = Field(
        description="Solo se muestra cuando se genera; luego hay que rotarlo para obtener otro"
    )


class SecretoSalida(BaseModel):
    secreto: str
