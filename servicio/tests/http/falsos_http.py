from __future__ import annotations

import hashlib
import uuid
from dataclasses import dataclass, field, replace
from datetime import UTC, datetime, timedelta
from uuid import UUID

from cobroverificado.adaptadores.http.servicios import (
    CodigoVinculacion,
    ConflictoIdempotenciaError,
    NuevoPago,
    Solicitada,
)
from cobroverificado.aplicacion.solicitar_verificacion import huella_peticion
from cobroverificado.dominio.modelos import (
    Comercio,
    Estado,
    OrigenPago,
    PagoRecibido,
    SolicitudVerificacion,
    Verificacion,
)

CLAVE = "yc_clave-de-prueba"
TOKEN = "token-dispositivo-prueba"
COMERCIO = Comercio(id=uuid.uuid4(), nombre="Bodega", destinatarios=("Bodega",), celulares=())
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
AHORA = datetime(2026, 11, 9, 15, 0, tzinfo=UTC)


def _h(texto: str) -> str:
    return hashlib.sha256(texto.encode()).hexdigest()


class AutenticacionFalsa:
    def comercio_por_clave(self, hash_clave: str) -> Comercio | None:
        return COMERCIO if hash_clave == _h(CLAVE) else None

    def comercio_por_dispositivo(self, hash_token: str) -> UUID | None:
        return COMERCIO.id if hash_token == _h(TOKEN) else None


@dataclass
class VerificacionesFalsas:
    guardadas: dict[UUID, Verificacion] = field(default_factory=dict)
    imagenes: list[bytes] = field(default_factory=list)
    claves: dict[str, tuple[str, UUID]] = field(default_factory=dict)

    def solicitar(
        self, solicitud: SolicitudVerificacion, imagen: bytes, clave_idempotencia: str | None
    ) -> Solicitada:
        huella = huella_peticion(solicitud, imagen)
        if clave_idempotencia and clave_idempotencia in self.claves:
            previa, id_ = self.claves[clave_idempotencia]
            if previa != huella:
                raise ConflictoIdempotenciaError("otra solicitud")
            return Solicitada(self.guardadas[id_], repetida=True)
        self.imagenes.append(imagen)
        v = Verificacion(
            uuid.uuid4(),
            solicitud.comercio_id,
            Estado.PROCESANDO,
            AHORA,
            solicitud.monto_esperado,
            solicitud.referencia,
        )
        self.guardadas[v.id] = v
        if clave_idempotencia:
            self.claves[clave_idempotencia] = (huella, v.id)
        return Solicitada(v, repetida=False)

    def obtener(self, comercio_id: UUID, verificacion_id: UUID) -> Verificacion | None:
        v = self.guardadas.get(verificacion_id)
        return v if v and v.comercio_id == comercio_id else None

    def revisar(
        self, comercio_id: UUID, verificacion_id: UUID, aprobar: bool, nota: str | None
    ) -> Verificacion | None:
        v = self.obtener(comercio_id, verificacion_id)
        if v is None:
            return None
        if v.estado == Estado.PROCESANDO:
            raise ValueError("la verificación aún se procesa")
        v = replace(v, estado=Estado.CONFIRMADO if aprobar else Estado.RECHAZADO, revisada=True)
        self.guardadas[v.id] = v
        return v


class DispositivosFalsos:
    def generar_codigo(self, comercio_id: UUID) -> CodigoVinculacion:
        return CodigoVinculacion("482913", AHORA + timedelta(minutes=10))

    def vincular(self, codigo: str) -> str | None:
        return TOKEN if codigo == "482913" else None


@dataclass
class PagosFalsos:
    pagos: list[PagoRecibido] = field(default_factory=list)

    def registrar(self, pago: NuevoPago) -> PagoRecibido:
        for p in self.pagos:
            if p.huella == pago.huella:
                return p
        nuevo = PagoRecibido(
            uuid.uuid4(),
            pago.comercio_id,
            pago.billetera,
            pago.monto,
            pago.fecha_hora,
            pago.numero_operacion,
            pago.remitente,
            pago.huella,
            OrigenPago.NOTIFICACION,
            AHORA,
        )
        self.pagos.append(nuevo)
        return nuevo

    def listar(self, comercio_id: UUID, desde: datetime, hasta: datetime) -> list[PagoRecibido]:
        return [p for p in self.pagos if desde <= p.fecha_hora <= hasta]


@dataclass
class SaludFalsa:
    ok: bool = True

    def disponible(self) -> bool:
        return self.ok


@dataclass
class WebhookFalso:
    urls: dict[UUID, str] = field(default_factory=dict)
    secretos: dict[UUID, str] = field(default_factory=dict)

    def configurar(self, comercio_id: UUID, url: str) -> str | None:
        self.urls[comercio_id] = url
        if comercio_id in self.secretos:
            return None
        return self.rotar(comercio_id)

    def rotar(self, comercio_id: UUID) -> str:
        self.secretos[comercio_id] = f"whsec_{len(self.secretos)}_{uuid.uuid4().hex}"
        return self.secretos[comercio_id]
