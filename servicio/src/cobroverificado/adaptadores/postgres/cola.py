from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta
from uuid import UUID

from .repositorios import Conexion, Pool, usar_conexion


def espera_exponencial(
    intentos: int,
    base: timedelta = timedelta(seconds=15),
    tope: timedelta = timedelta(hours=1),
) -> timedelta:
    espera: timedelta = base * 2 ** max(intentos - 1, 0)
    return min(espera, tope)


@dataclass(frozen=True, slots=True)
class Trabajo:
    id: int
    comercio_id: UUID
    verificacion_id: UUID
    ruta_imagen: str
    intentos: int
    max_intentos: int


class ColaTrabajosPg:
    def __init__(self, pool: Pool, max_intentos: int = 5) -> None:
        self._pool = pool
        self._max_intentos = max_intentos

    def encolar(
        self,
        comercio_id: UUID,
        verificacion_id: UUID,
        ruta_imagen: str,
        conexion: Conexion | None = None,
    ) -> int:
        with usar_conexion(self._pool, conexion) as activa:
            fila = activa.execute(
                "insert into trabajos (comercio_id, verificacion_id, ruta_imagen, max_intentos)"
                " values (%s, %s, %s, %s) returning id",
                (comercio_id, verificacion_id, ruta_imagen, self._max_intentos),
            ).fetchone()
        if fila is None:
            raise RuntimeError("la base no devolvió la fila esperada")
        return int(fila[0])

    def tomar(self, trabajador: str) -> Trabajo | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "update trabajos t set estado = 'en_curso', intentos = t.intentos + 1,"
                " bloqueado_en = now(), bloqueado_por = %s"
                " where t.id = (select id from trabajos"
                "   where estado = 'pendiente' and disponible_en <= now()"
                "   order by disponible_en, id limit 1 for update skip locked)"
                " returning t.id, t.comercio_id, t.verificacion_id, t.ruta_imagen,"
                " t.intentos, t.max_intentos",
                (trabajador,),
            ).fetchone()
        if fila is None:
            return None
        return Trabajo(fila[0], fila[1], fila[2], fila[3], fila[4], fila[5])

    def completar(self, trabajo_id: int) -> None:
        with self._pool.connection() as conexion:
            conexion.execute(
                "update trabajos set estado = 'terminado', terminado_en = now(),"
                " bloqueado_en = null, bloqueado_por = null where id = %s",
                (trabajo_id,),
            )

    def fallar(self, trabajo: Trabajo, error: str) -> bool:
        definitivo = trabajo.intentos >= trabajo.max_intentos
        with self._pool.connection() as conexion:
            conexion.execute(
                "update trabajos set estado = %s, ultimo_error = %s,"
                " disponible_en = now() + %s, bloqueado_en = null, bloqueado_por = null,"
                " terminado_en = case when %s then now() end"
                " where id = %s",
                (
                    "fallido" if definitivo else "pendiente",
                    error[:2000],
                    espera_exponencial(trabajo.intentos),
                    definitivo,
                    trabajo.id,
                ),
            )
        return definitivo

    def recuperar_bloqueados(self, antiguedad: timedelta) -> int:
        # un worker que murió deja trabajos en curso; vuelven a la cola si aún les quedan intentos
        with self._pool.connection() as conexion:
            cursor = conexion.execute(
                "update trabajos set"
                " estado = case when intentos >= max_intentos then 'fallido' else 'pendiente' end,"
                " terminado_en = case when intentos >= max_intentos then now() end,"
                " ultimo_error = 'bloqueo vencido', disponible_en = now(),"
                " bloqueado_en = null, bloqueado_por = null"
                " where estado = 'en_curso' and bloqueado_en < now() - %s",
                (antiguedad,),
            )
            return cursor.rowcount
