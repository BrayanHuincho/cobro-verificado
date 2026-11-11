from __future__ import annotations

import threading
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID, uuid4

from cobroverificado.adaptadores.almacen import AlmacenImagenesLocal
from cobroverificado.adaptadores.postgres import (
    ColaTrabajosPg,
    Pool,
    RepositorioComerciosPg,
    RepositorioVerificacionesPg,
    espera_exponencial,
    limpiar_filas,
)
from cobroverificado.dominio.modelos import Comercio, Estado, Veredicto, Verificacion
from cobroverificado.trabajador import Trabajador


def _vaciar(pool: Pool) -> None:
    with pool.connection() as conexion:
        conexion.execute("delete from trabajos")


def _verificacion(pool: Pool, comercio: Comercio) -> UUID:
    return (
        RepositorioVerificacionesPg(pool)
        .crear(Verificacion(uuid4(), comercio.id, Estado.PROCESANDO, datetime.now(UTC)))
        .id
    )


def test_espera_exponencial() -> None:
    assert espera_exponencial(1) == timedelta(seconds=15)
    assert espera_exponencial(3) == timedelta(seconds=60)
    assert espera_exponencial(30) == timedelta(hours=1)


def test_skip_locked_reparte_sin_duplicar(pool: Pool, comercio: Comercio) -> None:
    _vaciar(pool)
    cola = ColaTrabajosPg(pool)
    ids = {cola.encolar(comercio.id, _verificacion(pool, comercio), f"img{i}") for i in range(20)}
    tomados: list[int] = []
    candado = threading.Lock()

    def consumir() -> None:
        while (trabajo := cola.tomar(threading.current_thread().name)) is not None:
            with candado:
                tomados.append(trabajo.id)

    hilos = [threading.Thread(target=consumir) for _ in range(5)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    assert sorted(tomados) == sorted(ids)


def test_fallo_reprograma_y_luego_es_definitivo(pool: Pool, comercio: Comercio) -> None:
    _vaciar(pool)
    cola = ColaTrabajosPg(pool, max_intentos=2)
    cola.encolar(comercio.id, _verificacion(pool, comercio), "img")
    trabajo = cola.tomar("t")
    assert trabajo is not None
    assert not cola.fallar(trabajo, "boom")
    assert cola.tomar("t") is None
    with pool.connection() as conexion:
        conexion.execute("update trabajos set disponible_en = now()")
    segundo = cola.tomar("t")
    assert segundo is not None
    assert segundo.intentos == 2
    assert cola.fallar(segundo, "boom")
    with pool.connection() as conexion:
        fila = conexion.execute("select estado, ultimo_error from trabajos").fetchone()
    assert fila == ("fallido", "boom")


def test_recupera_bloqueos_vencidos(pool: Pool, comercio: Comercio) -> None:
    _vaciar(pool)
    cola = ColaTrabajosPg(pool)
    cola.encolar(comercio.id, _verificacion(pool, comercio), "img")
    assert cola.tomar("muerto") is not None
    assert cola.recuperar_bloqueados(timedelta(minutes=5)) == 0
    with pool.connection() as conexion:
        conexion.execute("update trabajos set bloqueado_en = now() - interval '1 hour'")
    assert cola.recuperar_bloqueados(timedelta(minutes=5)) == 1
    recuperado = cola.tomar("vivo")
    assert recuperado is not None
    assert recuperado.intentos == 2


def test_retencion_borra_terminados_antiguos(pool: Pool, comercio: Comercio) -> None:
    _vaciar(pool)
    cola = ColaTrabajosPg(pool)
    viejo = cola.encolar(comercio.id, _verificacion(pool, comercio), "a")
    cola.encolar(comercio.id, _verificacion(pool, comercio), "b")
    cola.completar(viejo)
    with pool.connection() as conexion:
        conexion.execute(
            "update trabajos set terminado_en = now() - interval '2 days' where id = %s", (viejo,)
        )
    assert limpiar_filas(pool, timedelta(hours=24)).trabajos == 1
    with pool.connection() as conexion:
        fila = conexion.execute("select count(*) from trabajos").fetchone()
    assert fila == (1,)


class _CasoFalso:
    def __init__(self) -> None:
        self.vistas: list[tuple[UUID, bytes]] = []

    def ejecutar(self, comercio: Comercio, verificacion_id: UUID, imagen: bytes) -> Veredicto:
        if imagen == b"imagen-mala":
            raise RuntimeError("ocr caído")
        self.vistas.append((verificacion_id, imagen))
        return Veredicto(Estado.PENDIENTE)


def test_trabajador_procesa_y_borra_imagen(pool: Pool, comercio: Comercio, tmp_path: Path) -> None:
    _vaciar(pool)
    cola = ColaTrabajosPg(pool, max_intentos=1)
    almacen = AlmacenImagenesLocal(tmp_path)
    v_bien = _verificacion(pool, comercio)
    v_mal = _verificacion(pool, comercio)
    cola.encolar(comercio.id, v_bien, almacen.guardar(b"imagen-buena"))
    cola.encolar(comercio.id, v_mal, almacen.guardar(b"imagen-mala"))
    caso = _CasoFalso()
    fallidas: list[UUID] = []
    trabajador = Trabajador(
        cola,
        almacen,
        RepositorioComerciosPg(pool),
        caso,
        al_fallar=lambda _c, v, _e: fallidas.append(v),
    )
    assert trabajador.procesar_uno()
    assert trabajador.procesar_uno()
    assert not trabajador.procesar_uno()
    assert caso.vistas == [(v_bien, b"imagen-buena")]
    assert fallidas == [v_mal]
    assert list(tmp_path.iterdir()) == []


def test_trabajador_se_detiene(pool: Pool, tmp_path: Path) -> None:
    _vaciar(pool)
    trabajador = Trabajador(
        ColaTrabajosPg(pool),
        AlmacenImagenesLocal(tmp_path),
        RepositorioComerciosPg(pool),
        _CasoFalso(),
    )
    hilo = threading.Thread(target=trabajador.ejecutar)
    hilo.start()
    trabajador.detener.set()
    hilo.join(timeout=5)
    assert not hilo.is_alive()
