from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta
from typing import Any
from uuid import UUID

from psycopg.types.json import Jsonb

from .cola import espera_exponencial
from .repositorios import Pool


@dataclass(frozen=True, slots=True)
class Entrega:
    id: int
    comercio_id: UUID
    verificacion_id: UUID
    url: str
    cuerpo: dict[str, Any]
    intentos: int
    max_intentos: int
    secreto: str | None


class RegistroEntregasPg:
    def __init__(self, pool: Pool, max_intentos: int = 8) -> None:
        self._pool = pool
        self._max_intentos = max_intentos

    def programar(
        self, comercio_id: UUID, verificacion_id: UUID, url: str, cuerpo: dict[str, Any]
    ) -> int:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "insert into entregas_webhook"
                " (comercio_id, verificacion_id, url, cuerpo, max_intentos)"
                " values (%s, %s, %s, %s, %s) returning id",
                (comercio_id, verificacion_id, url, Jsonb(cuerpo), self._max_intentos),
            ).fetchone()
        if fila is None:
            raise RuntimeError("la base no devolvió la fila esperada")
        return int(fila[0])

    def tomar(self) -> Entrega | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "update entregas_webhook e set estado = 'en_curso',"
                " intentos = e.intentos + 1, bloqueado_en = now()"
                " from comercios c"
                " where c.id = e.comercio_id and e.id = (select id from entregas_webhook"
                "   where estado = 'pendiente' and disponible_en <= now()"
                "   order by disponible_en, id limit 1 for update skip locked)"
                " returning e.id, e.comercio_id, e.verificacion_id, e.url, e.cuerpo,"
                " e.intentos, e.max_intentos, c.secreto_webhook_cifrado",
            ).fetchone()
        if fila is None:
            return None
        return Entrega(*fila)

    def marcar_entregada(self, entrega_id: int, codigo: int) -> None:
        with self._pool.connection() as conexion:
            conexion.execute(
                "update entregas_webhook set estado = 'entregada', ultimo_codigo = %s,"
                " ultimo_error = null, entregada_en = now(), bloqueado_en = null"
                " where id = %s",
                (codigo, entrega_id),
            )

    def marcar_fallo(self, entrega: Entrega, codigo: int | None, error: str) -> bool:
        definitiva = entrega.intentos >= entrega.max_intentos
        with self._pool.connection() as conexion:
            conexion.execute(
                "update entregas_webhook set estado = %s, ultimo_codigo = %s,"
                " ultimo_error = %s, disponible_en = now() + %s, bloqueado_en = null"
                " where id = %s",
                (
                    "fallida" if definitiva else "pendiente",
                    codigo,
                    error[:2000],
                    espera_exponencial(entrega.intentos, base=timedelta(seconds=30)),
                    entrega.id,
                ),
            )
        return definitiva

    def recuperar_bloqueadas(self, antiguedad: timedelta) -> int:
        with self._pool.connection() as conexion:
            cursor = conexion.execute(
                "update entregas_webhook set"
                " estado = case when intentos >= max_intentos then 'fallida' else 'pendiente' end,"
                " ultimo_error = 'bloqueo vencido', disponible_en = now(), bloqueado_en = null"
                " where estado = 'en_curso' and bloqueado_en < now() - %s",
                (antiguedad,),
            )
            return cursor.rowcount

    def historial(
        self, comercio_id: UUID, verificacion_id: UUID
    ) -> list[tuple[str, int, int | None]]:
        with self._pool.connection() as conexion:
            filas = conexion.execute(
                "select estado, intentos, ultimo_codigo from entregas_webhook"
                " where comercio_id = %s and verificacion_id = %s order by id",
                (comercio_id, verificacion_id),
            ).fetchall()
        return [(f[0], f[1], f[2]) for f in filas]
