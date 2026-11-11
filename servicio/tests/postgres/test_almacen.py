from __future__ import annotations

import os
import time
from datetime import timedelta
from pathlib import Path

import pytest

from cobroverificado.adaptadores.almacen import AlmacenImagenesLocal


def test_guarda_lee_y_borra(tmp_path: Path) -> None:
    almacen = AlmacenImagenesLocal(tmp_path)
    a = almacen.guardar(b"x")
    b = almacen.guardar(b"x")
    assert a != b
    assert len(a) == 32
    assert almacen.leer(a) == b"x"
    almacen.borrar(a)
    assert almacen.leer(a) is None


def test_rechaza_rutas(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="inválido"):
        AlmacenImagenesLocal(tmp_path).leer("../secreto")


def test_limpia_antiguas(tmp_path: Path) -> None:
    almacen = AlmacenImagenesLocal(tmp_path)
    vieja = almacen.guardar(b"1")
    nueva = almacen.guardar(b"2")
    hace_dos_dias = time.time() - 2 * 86400
    os.utime(tmp_path / vieja, (hace_dos_dias, hace_dos_dias))
    assert almacen.limpiar_antiguas(timedelta(hours=24)) == 1
    assert almacen.leer(nueva) == b"2"
