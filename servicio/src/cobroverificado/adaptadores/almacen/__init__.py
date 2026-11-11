from __future__ import annotations

import os
import secrets
import time
from datetime import timedelta
from pathlib import Path


class AlmacenImagenesLocal:
    def __init__(self, directorio: Path) -> None:
        self._directorio = directorio
        self._directorio.mkdir(parents=True, exist_ok=True)

    def _ruta(self, nombre: str) -> Path:
        if not nombre.isalnum():
            raise ValueError("nombre de imagen inválido")
        return self._directorio / nombre

    def guardar(self, imagen: bytes) -> str:
        nombre = secrets.token_hex(16)
        temporal = self._directorio / f".{nombre}.parcial"
        descriptor = os.open(temporal, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as archivo:
            archivo.write(imagen)
        temporal.replace(self._ruta(nombre))
        return nombre

    def leer(self, nombre: str) -> bytes | None:
        try:
            return self._ruta(nombre).read_bytes()
        except FileNotFoundError:
            return None

    def borrar(self, nombre: str) -> None:
        self._ruta(nombre).unlink(missing_ok=True)

    def limpiar_antiguas(self, retencion: timedelta) -> int:
        limite = time.time() - retencion.total_seconds()
        borradas = 0
        for archivo in self._directorio.iterdir():
            try:
                if archivo.is_file() and archivo.stat().st_mtime < limite:
                    archivo.unlink()
                    borradas += 1
            except FileNotFoundError:
                continue
        return borradas
