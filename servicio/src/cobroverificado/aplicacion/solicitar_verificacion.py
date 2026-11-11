from __future__ import annotations

import hashlib
from uuid import uuid4

from cobroverificado.aplicacion.errores import IdempotenciaConflictoError
from cobroverificado.dominio.modelos import Estado, SolicitudVerificacion, Verificacion
from cobroverificado.puertos import AlmacenImagenes, RegistroSolicitudes, Reloj, SolicitudRegistrada


def huella_peticion(solicitud: SolicitudVerificacion, imagen: bytes) -> str:
    extra = f"|{solicitud.monto_esperado}|{solicitud.referencia}".encode()
    return hashlib.sha256(imagen + extra).hexdigest()


class SolicitarVerificacion:
    def __init__(
        self, registro: RegistroSolicitudes, almacen: AlmacenImagenes, reloj: Reloj
    ) -> None:
        self._registro = registro
        self._almacen = almacen
        self._reloj = reloj

    def ejecutar(
        self,
        solicitud: SolicitudVerificacion,
        imagen: bytes,
        clave_idempotencia: str | None = None,
    ) -> SolicitudRegistrada:
        huella = huella_peticion(solicitud, imagen)
        nueva = Verificacion(
            id=uuid4(),
            comercio_id=solicitud.comercio_id,
            estado=Estado.PROCESANDO,
            creada_en=self._reloj.ahora(),
            monto_esperado=solicitud.monto_esperado,
            referencia=solicitud.referencia,
        )
        # la imagen se guarda antes para que el trabajo nunca apunte a un archivo inexistente
        nombre = self._almacen.guardar(imagen)
        try:
            registrada = self._registro.crear_y_encolar(nueva, nombre, clave_idempotencia, huella)
        except Exception:
            self._almacen.borrar(nombre)
            raise
        if registrada.creada:
            return registrada
        self._almacen.borrar(nombre)
        if registrada.huella_peticion != huella:
            raise IdempotenciaConflictoError("la Idempotency-Key ya se usó con otra solicitud")
        return registrada
