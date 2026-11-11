from __future__ import annotations

from importlib import resources

import psycopg
from psycopg.rows import TupleRow

# el candado evita que dos procesos apliquen la misma migración a la vez al arrancar
_CANDADO = 7_310_255


def pendientes_en_paquete() -> list[tuple[str, str]]:
    carpeta = resources.files(__package__)
    archivos = sorted(
        (a for a in carpeta.iterdir() if a.name.endswith(".sql")), key=lambda a: a.name
    )
    return [(a.name.removesuffix(".sql"), a.read_text(encoding="utf-8")) for a in archivos]


def migrar(conexion: psycopg.Connection[TupleRow]) -> list[str]:
    aplicadas: list[str] = []
    with conexion.transaction():
        conexion.execute("select pg_advisory_xact_lock(%s)", (_CANDADO,))
        conexion.execute(
            "create table if not exists esquema_migraciones ("
            " version text primary key,"
            " aplicada_en timestamptz not null default now())"
        )
        hechas = {
            str(fila[0])
            for fila in conexion.execute("select version from esquema_migraciones").fetchall()
        }
        for version, sql in pendientes_en_paquete():
            if version in hechas:
                continue
            conexion.execute(sql.encode("utf-8"))
            conexion.execute("insert into esquema_migraciones (version) values (%s)", (version,))
            aplicadas.append(version)
    return aplicadas
