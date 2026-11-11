from __future__ import annotations

import logging
import os
import signal
import socket
import sys
import threading
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import timedelta
from pathlib import Path
from types import FrameType
from typing import Protocol
from uuid import UUID

from cobroverificado.dominio.modelos import Comercio, Veredicto, Verificacion
from cobroverificado.puertos import RepositorioVerificaciones

log = logging.getLogger("cobroverificado.trabajador")


class TrabajoTomado(Protocol):
    @property
    def id(self) -> int: ...
    @property
    def comercio_id(self) -> UUID: ...
    @property
    def verificacion_id(self) -> UUID: ...
    @property
    def ruta_imagen(self) -> str: ...


class Cola[T: TrabajoTomado](Protocol):
    def tomar(self, trabajador: str) -> T | None: ...
    def completar(self, trabajo_id: int) -> None: ...
    def fallar(self, trabajo: T, error: str) -> bool: ...
    def recuperar_bloqueados(self, antiguedad: timedelta) -> int: ...


class Almacen(Protocol):
    def leer(self, nombre: str) -> bytes | None: ...
    def borrar(self, nombre: str) -> None: ...


class Despachador(Protocol):
    def entregar_siguiente(self) -> bool: ...
    def avisar(self, verificacion: Verificacion, url: str | None) -> int | None: ...


class Comercios(Protocol):
    def obtener(self, comercio_id: UUID) -> Comercio | None: ...


class CasoVerificar(Protocol):
    def ejecutar(self, comercio: Comercio, verificacion_id: UUID, imagen: bytes) -> Veredicto: ...


AlFallar = Callable[[UUID, UUID, str], None]


@dataclass(frozen=True, slots=True)
class Intervalos:
    espera_vacia: float = 1.0
    recuperacion: float = 60.0
    limpieza: float = 900.0
    bloqueo_vencido: timedelta = timedelta(minutes=10)


INTERVALOS = Intervalos()
LATIDO_MAXIMO = 60.0


class Trabajador[T: TrabajoTomado]:
    def __init__(
        self,
        cola: Cola[T],
        almacen: Almacen,
        comercios: Comercios,
        verificar: CasoVerificar,
        verificaciones: RepositorioVerificaciones | None = None,
        despachador: Despachador | None = None,
        limpiar: Callable[[], None] | None = None,
        al_fallar: AlFallar | None = None,
        intervalos: Intervalos = INTERVALOS,
        nombre: str | None = None,
    ) -> None:
        self._cola = cola
        self._almacen = almacen
        self._comercios = comercios
        self._verificar = verificar
        self._verificaciones = verificaciones
        self._despachador = despachador
        self._limpiar = limpiar
        self._al_fallar = al_fallar
        self._intervalos = intervalos
        self.nombre = nombre or f"{socket.gethostname()}:{os.getpid()}"
        self.detener = threading.Event()
        self.archivo_latido: Path | None = None

    def procesar_uno(self) -> bool:
        trabajo = self._cola.tomar(self.nombre)
        if trabajo is None:
            return False
        try:
            comercio = self._comercios.obtener(trabajo.comercio_id)
            if comercio is None:
                raise LookupError("el comercio ya no existe")
            imagen = self._almacen.leer(trabajo.ruta_imagen)
            if imagen is None:
                raise FileNotFoundError("la imagen ya no está en el almacén")
            self._verificar.ejecutar(comercio, trabajo.verificacion_id, imagen)
            self._avisar(comercio, trabajo.verificacion_id)
        except Exception as error:
            log.exception("trabajo %s falló", trabajo.id)
            definitivo = self._cola.fallar(trabajo, f"{type(error).__name__}: {error}")
            if definitivo:
                self._almacen.borrar(trabajo.ruta_imagen)
                if self._al_fallar is not None:
                    self._al_fallar(trabajo.comercio_id, trabajo.verificacion_id, str(error))
            return True
        self._cola.completar(trabajo.id)
        self._almacen.borrar(trabajo.ruta_imagen)
        return True

    def _avisar(self, comercio: Comercio, verificacion_id: UUID) -> None:
        if self._despachador is None or self._verificaciones is None or not comercio.url_webhook:
            return
        verificacion = self._verificaciones.obtener(comercio.id, verificacion_id)
        if verificacion is not None:
            self._despachador.avisar(verificacion, comercio.url_webhook)

    def _mantenimiento(self, ultima: dict[str, float]) -> None:
        ahora = time.monotonic()
        if ahora - ultima.get("recuperacion", 0.0) >= self._intervalos.recuperacion:
            ultima["recuperacion"] = ahora
            recuperados = self._cola.recuperar_bloqueados(self._intervalos.bloqueo_vencido)
            if recuperados:
                log.warning("%s trabajos recuperados de bloqueos vencidos", recuperados)
        if (
            self._limpiar is not None
            and ahora - ultima.get("limpieza", 0.0) >= self._intervalos.limpieza
        ):
            ultima["limpieza"] = ahora
            self._limpiar()

    def ejecutar(self) -> None:
        ultima: dict[str, float] = {}
        log.info("trabajador %s iniciado", self.nombre)
        while not self.detener.is_set():
            if self.archivo_latido is not None:
                latir(self.archivo_latido)
            hubo_trabajo = False
            try:
                self._mantenimiento(ultima)
                hubo_trabajo = self.procesar_uno()
                if self._despachador is not None:
                    hubo_trabajo = self._despachador.entregar_siguiente() or hubo_trabajo
            except Exception:
                log.exception("error en el ciclo del trabajador")
            if not hubo_trabajo:
                self.detener.wait(self._intervalos.espera_vacia)
        log.info("trabajador %s detenido", self.nombre)

    def instalar_senales(self) -> None:
        def parar(_numero: int, _marco: FrameType | None) -> None:
            self.detener.set()

        signal.signal(signal.SIGINT, parar)
        signal.signal(signal.SIGTERM, parar)


def latir(archivo: Path) -> None:
    try:
        archivo.write_text(f"{time.time():.0f}", encoding="ascii")
    except OSError:
        log.exception("no se pudo escribir el latido")


def latido_reciente(archivo: Path, maximo: float = LATIDO_MAXIMO) -> bool:
    try:
        marca = float(archivo.read_text(encoding="ascii"))
    except (OSError, ValueError):
        return False
    return time.time() - marca <= maximo


def main() -> None:
    por_defecto = "/tmp/cobroverificado-latido"  # noqa: S108
    archivo_latido = Path(os.environ.get("COBROVERIFICADO_ARCHIVO_LATIDO", por_defecto))
    # el healthcheck del contenedor invoca "salud" y solo mira la antigüedad del latido
    if sys.argv[1:] == ["salud"]:
        sys.exit(0 if latido_reciente(archivo_latido) else 1)

    from cobroverificado.composicion import construir_trabajador
    from cobroverificado.config import Configuracion

    config = Configuracion()
    logging.basicConfig(
        level=config.log_level.upper(), format="%(asctime)s %(levelname)s %(message)s"
    )
    trabajador = construir_trabajador(config)
    trabajador.archivo_latido = archivo_latido
    trabajador.instalar_senales()
    trabajador.ejecutar()


if __name__ == "__main__":
    main()
