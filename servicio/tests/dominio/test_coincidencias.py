from __future__ import annotations

import pytest
from hypothesis import given
from hypothesis import strategies as st

from cobroverificado.dominio.coincidencias import (
    coincide_celular,
    coincide_nombre,
    normalizar_nombre,
)


@pytest.mark.parametrize(
    ("capturado", "esperado"),
    [
        ("Juan Perez", "Juan Pérez"),
        ("JUAN PÉREZ", "juan perez"),
        ("Juan P.", "Juan Perez Gomez"),
        ("Juan P*", "Juan Perez Gomez"),
        ("Juan Pe", "Juan Perez"),
        ("Juan Gomez", "Juan Carlos Perez Gomez"),
        ("María López R.", "Maria Lopez Rojas"),
        ("  bodega   la   esquina ", "Bodega La Esquina"),
        ("Ñique S.", "Ñique Salas"),
    ],
)
def test_nombres_que_coinciden(capturado: str, esperado: str) -> None:
    assert coincide_nombre(capturado, esperado)


@pytest.mark.parametrize(
    ("capturado", "esperado"),
    [
        ("Pedro Perez", "Juan Perez"),
        ("Juan Q.", "Juan Perez"),
        ("Perez Juan", "Juan Perez"),
        ("J. P.", "Juan Perez"),
        ("Juan Perez Gomez", "Juan Perez"),
        ("Juanito Perez", "Juan Perez"),
        ("", "Juan Perez"),
        ("Juan", ""),
        ("...", "Juan"),
    ],
)
def test_nombres_que_no_coinciden(capturado: str, esperado: str) -> None:
    assert not coincide_nombre(capturado, esperado)


def test_normalizar_quita_tildes_y_espacios() -> None:
    assert normalizar_nombre("  ÁNGEL   Muñoz ") == "angel munoz"


nombres = st.lists(
    st.text(alphabet="abcdefghijklmnopqrstuvwxyz", min_size=3, max_size=10),
    min_size=1,
    max_size=4,
).map(" ".join)


@given(nombres)
def test_un_nombre_coincide_consigo_mismo_en_mayusculas(nombre: str) -> None:
    assert coincide_nombre(nombre.upper(), nombre)


@given(nombres)
def test_inicial_del_apellido_coincide(nombre: str) -> None:
    palabras = nombre.split()
    if len(palabras) < 2:
        return
    recortado = f"{palabras[0]} {palabras[1][0]}."
    assert coincide_nombre(recortado, nombre)


@pytest.mark.parametrize(
    ("capturado", "esperado"),
    [
        ("*** *** 789", "987654789"),
        ("987 654 789", "987654789"),
        ("+51 987654789", "987654789"),
        ("987654789", "+51 987 654 789"),
        ("****4789", "987654789"),
    ],
)
def test_celulares_que_coinciden(capturado: str, esperado: str) -> None:
    assert coincide_celular(capturado, esperado)


@pytest.mark.parametrize(
    ("capturado", "esperado"),
    [
        ("*** *** 788", "987654789"),
        ("** 89", "987654789"),
        ("", "987654789"),
        ("789", ""),
    ],
)
def test_celulares_que_no_coinciden(capturado: str, esperado: str) -> None:
    assert not coincide_celular(capturado, esperado)


@given(st.text(alphabet="0123456789", min_size=9, max_size=9), st.integers(3, 9))
def test_cualquier_sufijo_visible_coincide(celular: str, visibles: int) -> None:
    assert coincide_celular("*" * (9 - visibles) + celular[-visibles:], celular)
