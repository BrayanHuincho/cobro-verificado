from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Protocol
from uuid import UUID

from cobroverificado.dominio.modelos import (
    Billetera,
    DatosExtraidos,
    PagoRecibido,
    Senal,
    Veredicto,
    Verificacion,
)


class Extractor(Protocol):
    def extraer(self, imagen: bytes) -> DatosExtraidos: ...


class AnalizadorImagen(Protocol):
    def analizar(self, imagen: bytes, datos: DatosExtraidos) -> list[Senal]: ...


class RepositorioPagos(Protocol):
    def registrar(self, pago: PagoRecibido) -> PagoRecibido: ...

    def candidatos(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        monto: Decimal,
        desde: datetime,
        hasta: datetime,
    ) -> list[PagoRecibido]: ...


class RepositorioVerificaciones(Protocol):
    def crear(self, verificacion: Verificacion) -> Verificacion: ...

    def obtener(self, comercio_id: UUID, verificacion_id: UUID) -> Verificacion | None: ...

    def guardar_resultado(
        self,
        comercio_id: UUID,
        verificacion_id: UUID,
        datos: DatosExtraidos,
        veredicto: Veredicto,
    ) -> None: ...

    def registrar_revision(
        self, comercio_id: UUID, verificacion_id: UUID, veredicto: Veredicto, nota: str | None
    ) -> None: ...

    def reservar_operacion(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        numero_operacion: str,
        verificacion_id: UUID,
    ) -> bool: ...


@dataclass(frozen=True, slots=True)
class SolicitudRegistrada:
    verificacion: Verificacion
    creada: bool
    huella_peticion: str | None


class RegistroSolicitudes(Protocol):
    # crea la verificación y su trabajo en una sola transacción; con clave repetida no crea nada
    def crear_y_encolar(
        self,
        verificacion: Verificacion,
        ruta_imagen: str,
        clave_idempotencia: str | None,
        huella_peticion: str,
    ) -> SolicitudRegistrada: ...


class AlmacenImagenes(Protocol):
    def guardar(self, imagen: bytes) -> str: ...

    def borrar(self, nombre: str) -> None: ...


class Reloj(Protocol):
    def ahora(self) -> datetime: ...


__all__ = [
    "AlmacenImagenes",
    "AnalizadorImagen",
    "Extractor",
    "RegistroSolicitudes",
    "Reloj",
    "RepositorioPagos",
    "RepositorioVerificaciones",
    "SolicitudRegistrada",
]
