from __future__ import annotations

import re
import unicodedata

_MARCAS_DE_CORTE = (".", "*", "…")
_MINIMO_DIGITOS_CELULAR = 3


def normalizar_nombre(texto: str) -> str:
    descompuesto = unicodedata.normalize("NFKD", texto)
    sin_tildes = "".join(c for c in descompuesto if not unicodedata.combining(c))
    return " ".join(sin_tildes.casefold().split())


def _fichas(texto: str) -> list[tuple[str, bool]]:
    fichas: list[tuple[str, bool]] = []
    for crudo in normalizar_nombre(texto).split(" "):
        cortado = crudo.endswith(_MARCAS_DE_CORTE)
        limpio = re.sub(r"[^a-z0-9ñ]", "", crudo)
        if limpio:
            fichas.append((limpio, cortado))
    return fichas


def coincide_nombre(capturado: str, esperado: str) -> bool:
    # las apps muestran nombres recortados ("Juan P.", "Maria Lop*"): cada palabra capturada
    # debe aparecer en orden en el esperado, completa o como prefijo si está cortada
    capturadas = _fichas(capturado)
    esperadas = [ficha for ficha, _ in _fichas(esperado)]
    if not capturadas or not esperadas:
        return False
    if not any(not cortada and len(ficha) >= 3 for ficha, cortada in capturadas):
        return False

    posicion = 0
    for indice, (ficha, cortada) in enumerate(capturadas):
        admite_prefijo = cortada or indice == len(capturadas) - 1
        while posicion < len(esperadas):
            candidata = esperadas[posicion]
            posicion += 1
            if candidata == ficha or (admite_prefijo and candidata.startswith(ficha)):
                break
        else:
            return False
    return True


def solo_digitos(texto: str) -> str:
    return "".join(c for c in texto if c.isdigit())


def coincide_celular(capturado: str, esperado: str) -> bool:
    visibles = solo_digitos(capturado)
    completo = solo_digitos(esperado)
    if len(visibles) < _MINIMO_DIGITOS_CELULAR or not completo:
        return False
    return completo.endswith(visibles) or visibles.endswith(completo)
