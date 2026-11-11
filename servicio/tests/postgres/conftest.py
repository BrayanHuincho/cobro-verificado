from __future__ import annotations

import os
from collections.abc import Iterator
from uuid import uuid4

import psycopg
import pytest
from psycopg_pool import ConnectionPool

from cobroverificado.adaptadores.cifrado import CifradorSecretos
from cobroverificado.adaptadores.postgres import Pool, RepositorioComerciosPg, migrar
from cobroverificado.dominio.modelos import Comercio

CIFRADOR = CifradorSecretos("11" * 32)

URL = os.environ.get(
    "COBROVERIFICADO_TEST_DATABASE_URL", "postgresql://postgres:prueba@localhost:55432/postgres"
)


@pytest.fixture(scope="session")
def pool() -> Iterator[Pool]:
    try:
        psycopg.connect(URL, connect_timeout=3).close()
    except psycopg.OperationalError:
        pytest.skip("postgres de pruebas no disponible")
    with psycopg.connect(URL, autocommit=True) as conexion:
        conexion.execute("drop schema public cascade")
        conexion.execute("create schema public")
        migrar(conexion)
    with ConnectionPool(URL, min_size=1, max_size=8, open=True) as abierto:
        yield abierto


@pytest.fixture
def comercio(pool: Pool) -> Comercio:
    nuevo = Comercio(id=uuid4(), nombre="Tienda", destinatarios=("Ana P.",), celulares=("987",))
    return RepositorioComerciosPg(pool).crear(
        nuevo, secreto_cifrado=CIFRADOR.cifrar("secreto", nuevo.id)
    )
