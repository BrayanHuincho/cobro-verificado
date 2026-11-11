from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime
from decimal import Decimal
from uuid import UUID

from cobroverificado.dominio.decision import ZONA_LIMA
from cobroverificado.dominio.modelos import (
    Billetera,
    DatosExtraidos,
    Estado,
    PagoRecibido,
    Senal,
    Veredicto,
    Verificacion,
)

AHORA = datetime(2026, 11, 9, 15, 0, tzinfo=ZONA_LIMA)


@dataclass
class RelojFijo:
    momento: datetime = AHORA

    def ahora(self) -> datetime:
        return self.momento


@dataclass
class ExtractorFijo:
    datos: DatosExtraidos
    recibidas: list[bytes] = field(default_factory=list)

    def extraer(self, imagen: bytes) -> DatosExtraidos:
        self.recibidas.append(imagen)
        return self.datos


@dataclass
class AnalizadorFijo:
    senales: list[Senal] = field(default_factory=list)

    def analizar(self, imagen: bytes, datos: DatosExtraidos) -> list[Senal]:
        return list(self.senales)


@dataclass
class PagosEnMemoria:
    pagos: list[PagoRecibido] = field(default_factory=list)
    consultas: list[tuple[Billetera, datetime, datetime]] = field(default_factory=list)

    def registrar(self, pago: PagoRecibido) -> PagoRecibido:
        for existente in self.pagos:
            if existente.comercio_id == pago.comercio_id and existente.huella == pago.huella:
                return existente
        self.pagos.append(pago)
        return pago

    def candidatos(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        monto: Decimal,
        desde: datetime,
        hasta: datetime,
    ) -> list[PagoRecibido]:
        self.consultas.append((billetera, desde, hasta))
        return [
            p
            for p in self.pagos
            if p.comercio_id == comercio_id
            and p.billetera == billetera
            and p.monto == monto
            and desde <= p.fecha_hora <= hasta
        ]


@dataclass
class VerificacionesEnMemoria:
    verificaciones: dict[UUID, Verificacion] = field(default_factory=dict)
    operaciones: dict[tuple[UUID, Billetera, str], UUID] = field(default_factory=dict)
    notas: dict[UUID, str | None] = field(default_factory=dict)

    def crear(self, verificacion: Verificacion) -> Verificacion:
        self.verificaciones[verificacion.id] = verificacion
        return verificacion

    def obtener(self, comercio_id: UUID, verificacion_id: UUID) -> Verificacion | None:
        v = self.verificaciones.get(verificacion_id)
        return v if v is not None and v.comercio_id == comercio_id else None

    def guardar_resultado(
        self,
        comercio_id: UUID,
        verificacion_id: UUID,
        datos: DatosExtraidos,
        veredicto: Veredicto,
    ) -> None:
        actual = self.obtener(comercio_id, verificacion_id)
        if actual is None:
            return
        self.verificaciones[verificacion_id] = replace(
            actual,
            estado=veredicto.estado,
            datos=datos,
            veredicto=veredicto,
            senales=veredicto.senales,
        )

    def registrar_revision(
        self, comercio_id: UUID, verificacion_id: UUID, veredicto: Veredicto, nota: str | None
    ) -> None:
        actual = self.obtener(comercio_id, verificacion_id)
        if actual is None:
            return
        self.verificaciones[verificacion_id] = replace(
            actual,
            estado=veredicto.estado,
            veredicto=veredicto,
            senales=veredicto.senales,
            revisada=True,
        )
        self.notas[verificacion_id] = nota

    def reservar_operacion(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        numero_operacion: str,
        verificacion_id: UUID,
    ) -> bool:
        dueno = self.operaciones.setdefault(
            (comercio_id, billetera, numero_operacion), verificacion_id
        )
        return dueno == verificacion_id


def nueva_verificacion(
    comercio_id: UUID, id_: UUID, monto: Decimal | None = Decimal("50.00")
) -> Verificacion:
    return Verificacion(
        id=id_,
        comercio_id=comercio_id,
        estado=Estado.PROCESANDO,
        creada_en=AHORA,
        monto_esperado=monto,
    )
