from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta

from .repositorios import Pool


@dataclass(frozen=True, slots=True)
class ResultadoLimpieza:
    trabajos: int
    entregas: int
    codigos: int


def limpiar_filas(pool: Pool, retencion: timedelta) -> ResultadoLimpieza:
    with pool.connection() as conexion, conexion.transaction():
        trabajos = conexion.execute(
            "delete from trabajos where estado in ('terminado', 'fallido')"
            " and terminado_en < now() - %s",
            (retencion,),
        ).rowcount
        entregas = conexion.execute(
            "delete from entregas_webhook where estado in ('entregada', 'fallida')"
            " and coalesce(entregada_en, disponible_en) < now() - %s",
            (retencion,),
        ).rowcount
        codigos = conexion.execute(
            "delete from codigos_vinculacion where expira_en < now() - %s", (retencion,)
        ).rowcount
    return ResultadoLimpieza(trabajos, entregas, codigos)
