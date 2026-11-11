from __future__ import annotations

import threading
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path
from uuid import UUID

import pytest

from cobroverificado.adaptadores.almacen import AlmacenImagenesLocal
from cobroverificado.adaptadores.postgres import ColaTrabajosPg, Pool, RegistroSolicitudesPg
from cobroverificado.aplicacion.errores import IdempotenciaConflictoError
from cobroverificado.aplicacion.solicitar_verificacion import SolicitarVerificacion
from cobroverificado.dominio.modelos import Comercio, SolicitudVerificacion
from cobroverificado.puertos import SolicitudRegistrada

IMAGEN = b"\x89PNG\r\n\x1a\n" + b"\x01" * 32


class _Reloj:
    def ahora(self) -> datetime:
        return datetime.now(UTC)


class _ColaRota(ColaTrabajosPg):
    def encolar(self, *args: object, **kwargs: object) -> int:
        raise RuntimeError("cola caída")


def _contar(pool: Pool, comercio_id: UUID) -> tuple[int, int]:
    with pool.connection() as conexion:
        fila = conexion.execute(
            "select (select count(*) from verificaciones where comercio_id = %s),"
            " (select count(*) from trabajos where comercio_id = %s)",
            (comercio_id, comercio_id),
        ).fetchone()
    assert fila is not None
    return int(fila[0]), int(fila[1])


def _caso(pool: Pool, tmp_path: Path, cola: ColaTrabajosPg | None = None) -> SolicitarVerificacion:
    return SolicitarVerificacion(
        RegistroSolicitudesPg(pool, cola), AlmacenImagenesLocal(tmp_path), _Reloj()
    )


def test_misma_clave_concurrente_crea_una_sola(
    pool: Pool, comercio: Comercio, tmp_path: Path
) -> None:
    caso = _caso(pool, tmp_path)
    solicitud = SolicitudVerificacion(comercio.id, Decimal("10.00"), "pedido-7")
    total = 8
    barrera = threading.Barrier(total)
    resultados: list[SolicitudRegistrada] = []
    errores: list[BaseException] = []
    candado = threading.Lock()

    def enviar() -> None:
        barrera.wait()
        try:
            registrada = caso.ejecutar(solicitud, IMAGEN, "clave-concurrente")
        except BaseException as error:
            with candado:
                errores.append(error)
            return
        with candado:
            resultados.append(registrada)

    hilos = [threading.Thread(target=enviar) for _ in range(total)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()

    assert errores == []
    assert len(resultados) == total
    assert len({r.verificacion.id for r in resultados}) == 1
    assert sum(r.creada for r in resultados) == 1
    assert _contar(pool, comercio.id) == (1, 1)
    assert len(list(tmp_path.iterdir())) == 1


def test_misma_clave_con_otro_cuerpo_es_conflicto(
    pool: Pool, comercio: Comercio, tmp_path: Path
) -> None:
    caso = _caso(pool, tmp_path)
    caso.ejecutar(SolicitudVerificacion(comercio.id, None, None), IMAGEN, "clave-otra")
    with pytest.raises(IdempotenciaConflictoError):
        caso.ejecutar(SolicitudVerificacion(comercio.id, None, None), IMAGEN + b"x", "clave-otra")
    assert _contar(pool, comercio.id) == (1, 1)
    assert len(list(tmp_path.iterdir())) == 1


def test_si_falla_el_encolado_no_queda_nada(pool: Pool, comercio: Comercio, tmp_path: Path) -> None:
    caso = _caso(pool, tmp_path, _ColaRota(pool))
    with pytest.raises(RuntimeError, match="cola caída"):
        caso.ejecutar(SolicitudVerificacion(comercio.id, None, None), IMAGEN, "clave-rota")
    assert _contar(pool, comercio.id) == (0, 0)
    assert list(tmp_path.iterdir()) == []
