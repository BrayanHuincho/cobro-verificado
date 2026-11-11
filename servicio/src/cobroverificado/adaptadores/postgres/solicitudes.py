from __future__ import annotations

from cobroverificado.dominio.modelos import Verificacion
from cobroverificado.puertos import SolicitudRegistrada

from .cola import ColaTrabajosPg
from .repositorios import Pool, RepositorioVerificacionesPg


class RegistroSolicitudesPg:
    def __init__(self, pool: Pool, cola: ColaTrabajosPg | None = None) -> None:
        self._pool = pool
        self._verificaciones = RepositorioVerificacionesPg(pool)
        self._cola = cola or ColaTrabajosPg(pool)

    def crear_y_encolar(
        self,
        verificacion: Verificacion,
        ruta_imagen: str,
        clave_idempotencia: str | None,
        huella_peticion: str,
    ) -> SolicitudRegistrada:
        with self._pool.connection() as conexion, conexion.transaction():
            registrada = self._verificaciones.crear_idempotente(
                verificacion, clave_idempotencia, huella_peticion, conexion
            )
            if registrada.creada:
                self._cola.encolar(
                    verificacion.comercio_id, registrada.verificacion.id, ruta_imagen, conexion
                )
        return registrada
